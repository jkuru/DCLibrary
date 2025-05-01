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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.kuru.featureflow.component.state.DFInstallationState
import com.kuru.featureflow.ui.theme.FeatureFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DFComponentActivity : ComponentActivity() {

    private val viewModel: DFComponentViewModel by viewModels()

    @Inject
    lateinit var splitInstallManager: SplitInstallManager

    private lateinit var confirmationResultLauncher: ActivityResultLauncher<IntentSenderRequest>

    // Keep track of the feature name associated with a *currently active* confirmation request
    // This helps prevent duplicate dialog launches and ensures correct feature context on result.
    private var _featureAwaitingConfirmation: String? = null

    companion object {
        const val EXTRA_TARGET = "uri"
        private const val TAG = "DFComponentActivity"
    }

    /**
     * Called when the activity is first created. This is where you should do all of your normal
     * static set up: create views, bind data to lists, etc. This method also provides you with
     * a Bundle containing the activity's previously frozen state, if there was one.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being
     * shut down then this Bundle contains the data it most recently supplied in
     * onSaveInstanceState(Bundle). Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Always call the superclass first
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display for the activity, allowing content to draw under system bars.
        enableEdgeToEdge()

        // Register an ActivityResultLauncher to handle the result from the Play Core confirmation dialog.
        // This launcher is prepared here and used later when startConfirmationDialogForResult is called.
        confirmationResultLauncher = registerForActivityResult(
            // Contract for starting an IntentSender for result (used by Play Core dialog).
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            // This lambda block executes when the confirmation dialog is dismissed (user interacts or cancels).

            // Retrieve the name of the feature we were awaiting confirmation for.
            // This is crucial because the launcher itself doesn't carry this context.
            val confirmedFeature = _featureAwaitingConfirmation

            if (confirmedFeature != null) {
                // Check if the user approved the confirmation (pressed 'Install' or similar positive action).
                if (result.resultCode == Activity.RESULT_OK) {
                    Log.i(
                        TAG,
                        "User confirmed installation via dialog for feature: $confirmedFeature."
                    )
                    // Inform the ViewModel that the user has confirmed, so it can potentially update
                    // its state or log this event. The installation should resume automatically via Play Core listeners.
                    viewModel.processIntent(DFComponentIntent.ConfirmInstallation(confirmedFeature))
                } else {
                    // User cancelled the dialog or declined the installation.
                    Log.w(
                        TAG,
                        "User declined or cancelled installation confirmation for feature: $confirmedFeature. Result code: ${result.resultCode}"
                    )
                    // Inform the ViewModel that confirmation was denied/cancelled.
                    // This allows the ViewModel to potentially update the UI to an error or idle state.
                    // Sending an Error intent here signals that the user action led to a non-successful outcome.
                    viewModel.processIntent(DFComponentIntent.Error("User declined confirmation for $confirmedFeature"))
                }

                // IMPORTANT: Clear the tracking flag *after* handling the result.
                // This signifies that we are no longer actively awaiting confirmation for this feature.
                _featureAwaitingConfirmation = null

            } else {
                // This case should ideally not happen if the flag is managed correctly before launching.
                // Log an error if we receive a result but don't know which feature it was for.
                Log.e(
                    TAG,
                    "Confirmation result received, but _featureAwaitingConfirmation was unexpectedly null. Result code: ${result.resultCode}"
                )
            }
        }

        // Process the Intent that started this Activity (e.g., from a deep link or navigation).
        // This extracts the target URI and dispatches an intent to the ViewModel to handle it.
        handleIntent(intent)

        // Set the activity's content to a Jetpack Compose UI.
        setContent {
            // --- Compose UI Setup ---

            // Remember a NavController for potential use within dynamic feature screens.
            val navController = rememberNavController()
            // Determine if the system is currently in dark mode.
            val isDarkTheme = isSystemInDarkTheme()

            // Observe the main UI state flow from the ViewModel using lifecycle awareness.
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            // Observe the StateFlow holding the composable lambda for the dynamic feature's screen.
            val dynamicScreenLambda by viewModel.dynamicScreenContent.collectAsStateWithLifecycle()

            // --- Side Effects ---

            // Execute side effects based on the current UI state (e.g., launching the confirmation dialog).
            // This composable contains the LaunchedEffect needed for interacting with system dialogs.
            HandleSideEffects(uiState)

            // Apply the application's theme (colors, typography).
            FeatureFlowTheme(darkTheme = isDarkTheme) {
                // --- UI Content ---

                // Conditionally display either the dynamically loaded feature screen or the standard installer screen.
                if (dynamicScreenLambda != null && uiState is DFComponentState.Success) {
                    // If we have a screen lambda AND the state is Success, render the dynamic feature's UI.
                    Log.d(
                        TAG,
                        "Rendering dynamic screen content for feature: ${(uiState as DFComponentState.Success).feature}"
                    )
                    // Smart cast uiState to Success is already done by the 'if' condition
                    val successState: DFComponentState.Success =
                        uiState as DFComponentState.Success // Already smart-casted
                    // Invoke the Composable function provided by the loaded feature module.
                    dynamicScreenLambda?.invoke(navController, successState.params)

                    // Use DisposableEffect to clean up when this dynamic screen leaves composition.
                    // This prevents stale content if the user navigates back or the activity is recreated.
                    DisposableEffect(Unit) {
                        onDispose {
                            // Tell the ViewModel to clear the dynamic content when this Composable is disposed.
                            Log.d(
                                TAG,
                                "Leaving dynamic screen scope, clearing dynamic content in ViewModel."
                            )
                            viewModel.clearDynamicContent()
                        }
                    }
                } else {
                    // Otherwise (e.g., loading, error, initial state, or success state before lambda is ready),
                    // show the standard DFComponentScreen which displays progress, errors, retry buttons etc.
                    DFComponentScreen(viewModel = viewModel)
                }
            } // End Theme
        } // End setContent
    } // End onCreate

    /**
     * Composable function responsible for handling side effects triggered by UI state changes.
     * This is the standard Compose way to perform actions like launching external Activities/Dialogs
     * or making imperative calls based on the current state.
     *
     *         Here's a breakdown of the data flow after the user confirms the installation:
     *
     *         1. **User Confirmation**: The user presses "OK" (or the equivalent positive action) in the Google Play confirmation dialog.
     *         2. **Activity Result**: Your `DFComponentActivity` receives the `Activity.RESULT_OK` via the `confirmationResultLauncher`.
     *         3. **ViewModel Intent**: The Activity calls `viewModel.processIntent(DFComponentIntent.ConfirmInstallation(confirmedFeature))`.
     *         4. **`handleInstallationConfirmed` Called**: The ViewModel executes this function.
     *         - It clears the `pendingConfirmationState`.
     *         - Crucially (based on the provided code): It sets the `_uiState` back to `DFComponentState.Loading` (`_uiState.value = DFComponentState.Loading`). It doesn't directly call `loadFeature` again in this version. The idea is that Play Core's background process will automatically resume the installation, and the existing listener/collector will pick up the new states.
     *         5. **Installation Resumes**: Google Play Core resumes the installation process in the background.
     *         6. **State Updates via Collector**: The `collect` block inside `initiateInstallation` (which is likely still running or resumes) starts receiving updated `DFInstallationState` from the `installer.installComponent(feature)` flow (e.g., `Downloading`, `Installing`).
     *         7. **UI State Updates**: `updateUiStateFromInstallationState` is called with these new states, potentially keeping the UI in `DFComponentState.Loading` or reflecting progress if you modified it.
     *         8. **Installation Completes**: The collector eventually receives `DFInstallationState.Installed`.
     *         9. **Post-Install Steps**:
     *         - `updateUiStateFromInstallationState` sets `_uiState.value` to `DFComponentState.Success(...)`. At this point, `_dynamicScreenContent` is still `null`.
     *         - The collector then calls `runServiceLoaderInitialization`.
     *         10. **Lambda Fetching**:
     *         - `runServiceLoaderInitialization` (after initializing components) calls `fetchAndSetDynamicScreen`.
     *         - `fetchAndSetDynamicScreen` retrieves the Composable lambda (`screenLambda`) from the registry.
     *         - It sets `_dynamicScreenContent.value = screenLambda`.
     *         - It also sets `_uiState.value = DFComponentState.Success(...)` again (ensuring the final state reflects success with content ready).
     *         11. **Activity Observation & Navigation**:
     *         - Your `DFComponentActivity` collects both `uiState` and `dynamicScreenContent` using `collectAsStateWithLifecycle`.
     *         - When the state updates from step 10 propagate to the Activity, the `setContent` block recomposes.
     *         - The condition `if (dynamicScreenLambda != null && uiState is DFComponentState.Success)` becomes true.
     *         - The code `dynamicScreenLambda?.invoke(navController)` is executed, which effectively navigates to or displays the dynamic feature's UI.
     *
     *         In summary:  the user confirmation eventually leads to state changes observed by the Activity,
     *         which in turn trigger the navigation/display of the dynamic feature.
     *         The key is that it's not `handleInstallationConfirmed` directly restarting the entire load, but rather resetting the UI state while the underlying Play Core listener picks up the resumed installation,
     *         eventually leading to both the `Success` state and the `dynamicScreenContent` lambda becoming available for the Activity to use.
     *
     *
     * @param uiState The current state from the ViewModel, observed by the UI.
     */
    @Composable
    private fun HandleSideEffects(uiState: DFComponentState) {
        // LaunchedEffect runs its block whenever the key (uiState) changes.
        // This is appropriate for triggering one-off actions based on state transitions.

        LaunchedEffect(uiState) {
            when (uiState) {
                is DFComponentState.RequiresConfirmation -> {
                    // This state means Play Core needs user confirmation (e.g., large download).
                    // We need to launch the confirmation dialog provided by the SplitInstallManager.

                    // Check if we are *already* awaiting confirmation for this specific feature.
                    // This prevents launching multiple dialogs if the state recomposes quickly.
                    if (_featureAwaitingConfirmation != uiState.feature) {
                        Log.i(
                            TAG,
                            "HandleSideEffects: RequiresConfirmation state observed for feature: ${uiState.feature}"
                        )

                        // Retrieve the necessary Play Core SessionState object from the ViewModel.
                        // This state is required by the startConfirmationDialogForResult method.
                        val sessionStateToConfirm: SplitInstallSessionState? =
                            viewModel.getPendingConfirmationSessionState()

                        if (sessionStateToConfirm != null) {
                            // IMPORTANT: Store the feature name *before* launching the dialog.
                            // This allows the ActivityResult callback (confirmationResultLauncher)
                            // to know which feature the result belongs to.
                            _featureAwaitingConfirmation = uiState.feature

                            Log.i(
                                TAG,
                                "HandleSideEffects: Attempting to launch confirmation dialog for feature: ${uiState.feature} with Session ID: ${sessionStateToConfirm.sessionId()}"
                            )
                            try {
                                // Launch the confirmation dialog. This suspends until the dialog is dismissed.
                                // The result will be delivered to the 'confirmationResultLauncher'.
                                splitInstallManager.startConfirmationDialogForResult(
                                    sessionStateToConfirm, // The specific state needing confirmation
                                    this@DFComponentActivity, // The activity to launch from
                                    1 // Request code (not typically used here)
                                )
                                Log.i(
                                    TAG,
                                    "HandleSideEffects: Confirmation dialog launch initiated."
                                )
                            } catch (e: Exception) {
                                Log.e(
                                    TAG,
                                    "HandleSideEffects: Error launching confirmation dialog for ${uiState.feature}",
                                    e
                                )
                                // If launching fails, clear the tracking flag to allow potential retries.
                                _featureAwaitingConfirmation = null
                                // Consider informing the ViewModel about the launch failure if needed.
                                // viewModel.processIntent(DFComponentIntent.Error("Failed to show confirmation"))
                            }
                        } else {
                            // This indicates a potential logic error: The ViewModel reported
                            // RequiresConfirmation state but didn't provide the necessary SessionState.
                            Log.e(
                                TAG,
                                "HandleSideEffects: Error - UI State is RequiresConfirmation for ${uiState.feature}, but ViewModel returned null session state."
                            )
                            // Potentially trigger an error state in the ViewModel.
                        }
                    } else {
                        // Log that we are intentionally skipping dialog launch because one is likely already active.
                        Log.w(
                            TAG,
                            "HandleSideEffects: RequiresConfirmation state for ${uiState.feature} is already being handled (dialog likely showing)."
                        )
                    }
                }

                is DFComponentState.Success -> {
                    // Handle the side effects when a feature successfully loads/installs.

                    // Robustness check: If the feature that just succeeded is the one we were
                    // tracking for confirmation, clear the flag. This handles edge cases where
                    // the state changes before the ActivityResult callback clears it.
                    if (_featureAwaitingConfirmation == uiState.feature) {
                        Log.i(
                            TAG,
                            "HandleSideEffects: Feature $_featureAwaitingConfirmation succeeded, clearing confirmation tracking variable."
                        )
                        _featureAwaitingConfirmation = null
                    }

                    // Log when the final 'Installed' state is reached within Success.
                    if (uiState.featureInstallationState is DFInstallationState.Installed) {
                        val featureName = uiState.feature
                        Log.e(
                            TAG,
                            "HandleSideEffects: Feature $featureName reached final Installed state."
                        )
                    }
                    // Read the comments above to understand the flow.
                }

                is DFComponentState.Error -> {
                    // Handle side effects for error states.

                    // Robustness check: If an error occurs for the feature we were awaiting
                    // confirmation for, clear the tracking flag. This handles cases where the
                    // installation fails after the dialog was shown but before the result callback.
                    if (_featureAwaitingConfirmation == uiState.feature) {
                        Log.e(
                            TAG,
                            "HandleSideEffects: Error occurred for feature $_featureAwaitingConfirmation while awaiting confirmation, clearing tracking variable."
                        )
                        _featureAwaitingConfirmation = null
                    }
                    // Read the comments above to understand the flow.
                }

                DFComponentState.Loading -> {
                    // Read the comments above to understand the flow.
                }
            } // end when
        } // end LaunchedEffect
    } // end HandleSideEffects

    /**
     * Called by the Android OS when the activity is re-launched while already running
     * (e.g., if its launchMode is singleTop, singleTask, or singleInstance in the Manifest,
     * or if specific Intent flags like FLAG_ACTIVITY_CLEAR_TOP are used) and a new Intent
     * is delivered to it.
     *
     * This typically happens when:
     * 1. The activity has `android:launchMode="singleTop"` and it's already at the top
     * of the activity stack when an Intent targeting it arrives.
     * 2. The activity has `android:launchMode="singleTask"` or `"singleInstance"` and an
     * Intent targeting it arrives while an instance already exists anywhere in the system.
     * 3. A notification click or deep link resolves to this activity while it's running
     * under one of the above conditions.
     *
     * It's crucial to handle the new intent here to update the UI or process data based
     * on the incoming request, as `onCreate` will not be called again in these scenarios.
     *
     * @param intent The new intent that was started for the activity.
     */
    override fun onNewIntent(intent: Intent) {
        // Always call the superclass implementation first.
        super.onNewIntent(intent)

        // Update the intent associated with this activity instance.
        // This ensures that subsequent calls to getIntent() will return this new intent.
        setIntent(intent)

        // Process the new intent to extract the URI and potentially load a new feature.
        // Reuses the same logic as in onCreate to handle intent processing.
        handleIntent(intent)
    }

    /**
     * Processes an incoming Intent to extract a target URI for dynamic feature loading.
     * This method is called from both onCreate (for the initial intent) and onNewIntent
     * (for subsequent intents when the activity is already running).
     * It extracts the URI and delegates the actual processing and feature loading logic
     * to the ViewModel.
     *
     * @param intent The Intent received by the activity. Can be null.
     */
    private fun handleIntent(intent: Intent?) {
        // Safety check: Do nothing if the intent is null.
        if (intent == null) {
            Log.w(TAG, "handleIntent received a null intent. Cannot process.")
            return
        }

        // Attempt to extract the target URI string from the Intent.
        // Prioritize ACTION_VIEW (deep links), then check for a custom EXTRA_TARGET.
        val uriString: String? = when {
            // Check if the intent is a standard VIEW action (like a deep link).
            intent.action == Intent.ACTION_VIEW -> intent.data?.toString()
            // Check if the intent contains our custom extra for specifying a target.
            intent.hasExtra(EXTRA_TARGET) -> intent.getStringExtra(EXTRA_TARGET)
            // If neither is present, we don't have a target URI.
            else -> null
        }

        // Check if a URI string was successfully extracted.
        if (uriString == null) {
            Log.w(
                TAG,
                "Intent received, but it did not contain an actionable URI in intent.data or EXTRA_TARGET."
            )
            // Optionally, you could inform the user or ViewModel about the invalid intent here.
            return
        }

        // If a URI was found, dispatch an intent to the ViewModel to process it.
        // The ViewModel will handle parsing, deduplication, and initiating the feature load.
        Log.d(TAG, "Dispatching ProcessUri intent to ViewModel with URI: $uriString")
        viewModel.processIntent(DFComponentIntent.ProcessUri(uriString))
    }

}