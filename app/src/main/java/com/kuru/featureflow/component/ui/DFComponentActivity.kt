package com.kuru.featureflow.component.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.kuru.featureflow.ui.theme.FeatureFlowTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * DFComponentActivity serves as the host Activity for displaying dynamic feature modules
 * or the installation/loading UI managed by DFComponentViewModel.
 * It orchestrates the UI based on the state received from the ViewModel and handles
 * Android-specific interactions like the Play Core confirmation dialog.
 */
@AndroidEntryPoint
class DFComponentActivity : ComponentActivity() {

    // --- ViewModel Interaction ---
    // Obtain the ViewModel instance using Hilt's viewModels delegate.
    // This ViewModel manages the state and logic for dynamic feature loading.
    private val viewModel: DFComponentViewModel by viewModels()

    // --- User Confirmation Flow ---
    // ActivityResultLauncher to handle the result from the Play Core confirmation dialog.
    // This is necessary because the confirmation is started via an IntentSender provided by Play Core.
    private lateinit var confirmationResultLauncher: ActivityResultLauncher<IntentSenderRequest>

    // --- User Confirmation Flow ---
    // Temporary state within Activity to hold the feature name for the active confirmation request.
    // Needed because the ActivityResultLauncher callback doesn't have context about which feature
    // triggered the confirmation dialog. It bridges the gap between launching the dialog
    // and receiving its result.
    private var featureAwaitingConfirmationResult: String? by mutableStateOf(null)

    companion object {
        // Key for the extra data in the Intent, specifying the target URI for feature loading.
        const val EXTRA_TARGET = "uri"
        private const val TAG = "DFComponentActivity"
    }

    /**
     * Called when the activity is first created. Sets up edge-to-edge display,
     * registers the confirmation result launcher, processes the initial intent,
     * and sets the Compose content based on ViewModel state and events.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being
     * shut down then this Bundle contains the data it most recently supplied in
     * onSaveInstanceState(Bundle). Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Configure UI display behavior.

        // --- User Confirmation Flow ---
        // Register the ActivityResultLauncher. The callback handles the result from Play Core's
        // confirmation dialog (user accepts or declines). It uses the `featureAwaitingConfirmationResult`
        // state to know which feature the result belongs to and informs the ViewModel via
        // `UserConfirmationResult` intent.
        confirmationResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val confirmedFeature = featureAwaitingConfirmationResult // Get the feature context

            if (confirmedFeature != null) {
                val userConfirmed = result.resultCode == Activity.RESULT_OK
                Log.i(TAG, "User confirmation result for $confirmedFeature: $userConfirmed")

                // --- ViewModel Interaction ---
                // Send the confirmation result (feature name and confirmed status) back to the ViewModel
                // for further processing (e.g., updating state, canceling).
                viewModel.processIntent(
                    DFComponentIntent.UserConfirmationResult(
                        confirmedFeature,
                        userConfirmed
                    )
                )

                // --- User Confirmation Flow ---
                // Clear the temporary state now that the result has been processed.
                featureAwaitingConfirmationResult = null
            } else {
                Log.e(
                    TAG,
                    "Confirmation result received, but featureAwaitingConfirmationResult was unexpectedly null."
                )
            }
        }

        // --- ViewModel Interaction ---
        // Process the initial Intent that started this Activity. This extracts the target
        // URI and triggers the ViewModel to handle it (via ProcessUri intent).
        handleIntent(intent)

        // --- States & ViewModel Interaction ---
        // Set the Activity's content using Jetpack Compose.
        setContent {
            val navController =
                rememberNavController() // NavController for potential nested navigation within features.
            val isDarkTheme = isSystemInDarkTheme() // System theme setting.

            // --- States ---
            // Observe the main UI state flow from the ViewModel using lifecycle-aware collection.
            // The `uiState` variable determines what is shown (Loading, Error, Confirmation, Success placeholder, or Dynamic Feature).
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            // --- States ---
            // Observe the StateFlow holding the composable lambda for the dynamic feature's actual UI.
            // This is populated by the ViewModel after successful loading and post-install steps.
            val dynamicScreenLambda by viewModel.dynamicScreenContent.collectAsStateWithLifecycle()

            // --- User Confirmation Flow & ViewModel Interaction ---
            // Collect events from the ViewModel's SharedFlow. This is used for one-off events
            // that shouldn't be tied directly to state recomposition, like triggering the confirmation dialog.
            LaunchedEffect(Unit) {
                viewModel.eventFlow.collect { eventData ->
                    when (eventData) {
                        // --- User Confirmation Flow ---
                        // When the ViewModel emits a ConfirmationEventData, it means Play Core requires user confirmation.
                        is ConfirmationEventData -> {
                            // Avoid launching multiple confirmation dialogs simultaneously.
                            if (featureAwaitingConfirmationResult == null) {
                                Log.i(
                                    TAG,
                                    "Received ShowConfirmation event for feature: ${eventData.feature}"
                                )
                                // Store the feature name context *before* launching the dialog.
                                featureAwaitingConfirmationResult = eventData.feature
                                // Trigger the confirmation dialog using the IntentSender from the event.
                                launchConfirmationDialog(eventData.sessionState)
                            } else {
                                Log.w(
                                    TAG,
                                    "Ignoring ShowConfirmation event for ${eventData.feature} as another confirmation for ${featureAwaitingConfirmationResult} is already pending."
                                )
                            }
                        }
                    }
                }
            }

            // --- States & User Confirmation Flow ---
            // Log UI state changes and add robustness: if the UI reaches a final state
            // while still technically waiting for a confirmation result (edge case), clear the tracker.
            LaunchedEffect(uiState) {
                Log.d(TAG, "UI State Changed: ${uiState::class.simpleName}")
                if ((uiState is DFComponentState.Success || uiState is DFComponentState.Error) && featureAwaitingConfirmationResult != null) {
                    // Check if the terminal state matches the feature awaiting confirmation
                    val featureInState = when (val state = uiState) {
                        is DFComponentState.Success -> state.feature
                        is DFComponentState.Error -> state.feature
                        else -> null
                    }
                    if (featureInState == featureAwaitingConfirmationResult) {
                        Log.w(
                            TAG,
                            "UI reached terminal state for $featureAwaitingConfirmationResult while awaiting confirmation result. Clearing tracking."
                        )
                        featureAwaitingConfirmationResult = null // Clear the flag
                    }
                }
            }

            // Apply the application theme.
            FeatureFlowTheme(darkTheme = isDarkTheme) {
                // --- States ---
                // Determine the main content based on the UI state and presence of dynamic screen content.
                // If a dynamic screen lambda is available AND the state is Success, render the feature's UI.
                if (dynamicScreenLambda != null && uiState is DFComponentState.Success) {
                    Log.d(
                        TAG,
                        "Rendering dynamic screen content for feature: ${(uiState as DFComponentState.Success).feature}"
                    )
                    val successState = uiState as DFComponentState.Success // Smart cast
                    // Invoke the Composable function provided by the loaded feature module.
                    dynamicScreenLambda?.invoke(navController, successState.params)

                    // --- ViewModel Interaction ---
                    // Use DisposableEffect to notify the ViewModel when the dynamic feature's UI
                    // leaves the composition (e.g., user navigates away). This allows the ViewModel
                    // to clear the potentially heavy Composable lambda.
                    DisposableEffect(Unit) {
                        onDispose {
                            Log.d(
                                TAG,
                                "Leaving dynamic screen scope, clearing dynamic content in ViewModel."
                            )
                            viewModel.clearDynamicContent() // Tell ViewModel to clear the lambda
                        }
                    }
                } else {
                    // --- States ---
                    // Otherwise (Loading, Error, RequiresConfirmation, or Success before lambda is ready),
                    // show the standard DFComponentScreen which displays progress, errors, retry buttons etc.
                    // The DFComponentScreen observes the same uiState internally.
                    DFComponentScreen(viewModel = viewModel)
                }
            }
        }
    }

    /**
     * Handles new Intents delivered to the activity while it's running (e.g., via singleTop launch mode).
     * Ensures the activity processes updated URIs or navigation requests.
     *
     * @param intent The new intent that was started for the activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent
        // --- ViewModel Interaction ---
        // Process the new intent, potentially triggering a new feature load in the ViewModel.
        handleIntent(intent)
    }

    /**
     * Processes an incoming Intent (initial or new) to extract the target URI.
     * Dispatches a `ProcessUri` intent to the ViewModel for handling.
     *
     * @param intent The Intent received by the activity.
     */
    private fun handleIntent(intent: Intent?) {
        Log.d(TAG, "onNewIntent triggered at ${System.currentTimeMillis()} for instance ${this.hashCode()}")
        Log.d(TAG, "  Intent Action: ${intent?.action}")
        Log.d(TAG, "  Intent Data: ${intent?.dataString}")
        Log.d(TAG, "  Intent Extras: ${intent?.extras}")
        if (intent == null) {
            Log.w(TAG, "handleIntent received a null intent.")
            return
        }

        // Extract URI from ACTION_VIEW or custom EXTRA_TARGET.
        val uriString: String? = when {
            intent.action == Intent.ACTION_VIEW -> intent.data?.toString()
            intent.hasExtra(EXTRA_TARGET) -> intent.getStringExtra(EXTRA_TARGET)
            else -> null
        }

        if (uriString == null) {
            Log.w(TAG, "Intent did not contain an actionable URI.")
            // Optionally inform ViewModel about invalid intent
            // viewModel.processIntent(...)
            return
        }

        // --- ViewModel Interaction ---
        // Send the extracted URI to the ViewModel for parsing and initiating the feature load process.
        Log.d(TAG, "Dispatching ProcessUri intent to ViewModel with URI: $uriString")
       viewModel.processIntent(DFComponentIntent.ProcessUri(uriString))
    }

    // --- User Confirmation Flow ---
    /**
     * Helper function to launch the Play Core confirmation dialog using the registered
     * ActivityResultLauncher. Stores the feature name temporarily before launching.
     * Handles potential errors during launch and informs the ViewModel if launch fails.
     *
     * @param sessionState The SplitInstallSessionState containing the resolution intent needed to launch the dialog.
     */
    private fun launchConfirmationDialog(sessionState: SplitInstallSessionState) {
        // Retrieve the feature name stored just before calling this function.
        val featureName = featureAwaitingConfirmationResult
        if (featureName == null) {
            Log.e(
                TAG,
                "Attempted to launch confirmation dialog, but featureAwaitingConfirmationResult is null."
            )
            // Optional: Notify ViewModel if this state is reached unexpectedly
            // viewModel.processIntent(...)
            return
        }

        Log.i(
            TAG,
            "Attempting to launch confirmation dialog for feature: $featureName with Session ID: ${sessionState.sessionId()}"
        )
        try {
            // Get the IntentSender from the session state (required for REQUIRES_USER_CONFIRMATION).
            sessionState.resolutionIntent()?.let { intentSender ->
                val request = IntentSenderRequest.Builder(intentSender).build()
                // Launch the dialog using the registered launcher. The result is handled in the launcher's callback.
                confirmationResultLauncher.launch(request)
                Log.i(TAG, "Confirmation dialog launch initiated via ActivityResultLauncher.")
            } ?: run {
                // Handle the unlikely case where resolutionIntent is null.
                Log.e(
                    TAG,
                    "Cannot launch confirmation dialog: resolutionIntent was null for feature $featureName."
                )
                featureAwaitingConfirmationResult = null // Clear flag as we cannot proceed
                // --- ViewModel Interaction ---
                // Notify ViewModel about the failure (treat as cancellation).
                viewModel.processIntent(
                    DFComponentIntent.UserConfirmationResult(
                        featureName,
                        false
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching confirmation dialog for $featureName", e)
            featureAwaitingConfirmationResult = null // Clear flag on error
            // --- ViewModel Interaction ---
            // Inform ViewModel about the launch failure (treat as cancellation).
            viewModel.processIntent(DFComponentIntent.UserConfirmationResult(featureName, false))
        }
    }
}