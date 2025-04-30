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
import com.google.android.play.core.splitinstall.SplitInstallSessionState // Import Play Core state class
import com.kuru.featureflow.component.route.DFComponentUriRouteParser
import com.kuru.featureflow.component.state.DFInstallationState // Keep import if used elsewhere
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        confirmationResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            // Use the stored feature name to provide context to the ViewModel
            val confirmedFeature = _featureAwaitingConfirmation
            if (confirmedFeature != null) {
                if (result.resultCode == Activity.RESULT_OK) {
                    Log.i(TAG, "User confirmed installation for feature: $confirmedFeature.")
                    // Inform ViewModel confirmation was handled
                    viewModel.processIntent(DFComponentIntent.ConfirmInstallation(confirmedFeature))
                } else {
                    Log.w(
                        TAG,
                        "User declined or cancelled installation confirmation for feature: $confirmedFeature. Result code: ${result.resultCode}"
                    )
                    // Optionally inform ViewModel about cancellation if specific handling is needed
                    // viewModel.processIntent(DFComponentIntent.CancelConfirmation(confirmedFeature))
                    // For now, let the installer listener detect cancellation state if it occurs.
                }
                // Clear the tracked feature name once the confirmation dialog is dismissed
                _featureAwaitingConfirmation = null
            } else {
                Log.e(
                    TAG,
                    "Confirmation result received, but _featureAwaitingConfirmation was null. Result code: ${result.resultCode}"
                )
            }
        }

        handleIntent(intent)

        setContent {
            // --- Need NavController for the lambda ---
            val navController = rememberNavController()
            val isDarkTheme = isSystemInDarkTheme()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            // --- Observe dynamic screen content ---
            val dynamicScreenLambda by viewModel.dynamicScreenContent.collectAsStateWithLifecycle()


            // Handle side effects like launching confirmation dialogs
            HandleSideEffects(uiState)

            FeatureFlowTheme(darkTheme = isDarkTheme) {
                // --- Conditionally Render Content ---
                if (dynamicScreenLambda != null && uiState is DFComponentState.Success) {
                    // If we have the lambda and state is Success, invoke the dynamic screen
                    Log.d(
                        TAG,
                        "Rendering dynamic screen content for feature: ${(uiState as DFComponentState.Success).feature}"
                    )
                    dynamicScreenLambda?.invoke(navController) // Invoke the Composable lambda

                    // Optional: Add DisposableEffect to clear content when leaving this scope
                    DisposableEffect(Unit) {
                        onDispose {
                            Log.d(TAG, "Leaving dynamic screen scope, clearing content.")
                            // Clear when navigating back or component is disposed
                            viewModel.clearDynamicContent()
                        }
                    }
                } else {
                    // Otherwise, show the standard Installer/Loading/Error screen
                    DFComponentScreen(viewModel = viewModel)
                }
            }
        }
    }

    @Composable
    private fun HandleSideEffects(uiState: DFComponentState) {
        // Use uiState as the key for LaunchedEffect to re-trigger on any state change
        LaunchedEffect(uiState) {
            when (uiState) {
                is DFComponentState.RequiresConfirmation -> {
                    // Only launch if we aren't already awaiting confirmation for this specific feature
                    if (_featureAwaitingConfirmation != uiState.feature) {
                        Log.d(
                            TAG,
                            "RequiresConfirmation state observed for feature: ${uiState.feature}"
                        )

                        // Ask ViewModel for the actual Play Core state object needed for the dialog
                        val sessionStateToConfirm: SplitInstallSessionState? =
                            viewModel.getPendingConfirmationSessionState()

                        if (sessionStateToConfirm != null) {
                            // Store the feature name *before* launching the dialog
                            _featureAwaitingConfirmation = uiState.feature
                            Log.d(
                                TAG,
                                "Attempting to launch confirmation dialog for feature: ${uiState.feature} with Session ID: ${sessionStateToConfirm.sessionId()}"
                            )
                            try {
                                splitInstallManager.startConfirmationDialogForResult(
                                    sessionStateToConfirm, // Use the object from ViewModel
                                    this@DFComponentActivity,
                                    1
                                )
                            } catch (e: Exception) {
                                Log.e(
                                    TAG,
                                    "Error launching confirmation dialog for ${uiState.feature}",
                                    e
                                )
                                // If launch fails, clear the tracking variable and maybe inform ViewModel
                                _featureAwaitingConfirmation = null
                                // Consider dispatching an error state via ViewModel if launch fails critically
                            }
                        } else {
                            // This case indicates an internal logic error:
                            // The UI state is RequiresConfirmation, but the ViewModel didn't have the
                            // corresponding raw Play Core state ready.
                            Log.e(
                                TAG,
                                "Error: UI State is RequiresConfirmation for ${uiState.feature}, but ViewModel returned null session state."
                            )
                            // Potentially trigger an error state in the ViewModel or log prominently.
                            // viewModel.processIntent(DFComponentIntent.HandleInternalError(...))
                        }
                    } else {
                        Log.d(
                            TAG,
                            "RequiresConfirmation state for ${uiState.feature} is already being handled (dialog likely showing)."
                        )
                    }
                }

                is DFComponentState.Success -> {
                    // If the feature that just succeeded is the one we were awaiting confirmation for,
                    // clear the tracking variable (though it should also be cleared by the ActivityResult callback)
                    if (_featureAwaitingConfirmation == uiState.feature) {
                        Log.d(
                            TAG,
                            "Feature $_featureAwaitingConfirmation succeeded, clearing tracking variable."
                        )
                        _featureAwaitingConfirmation = null
                    }

                    if (uiState.DFInstallationState is DFInstallationState.Installed) {
                        val featureName = uiState.feature
                        Log.d(TAG, "Feature $featureName reached Installed state.")
                        // TODO: Trigger navigation to the actual feature screen here.
                        // Example: FindNavController().navigate(featureName)
                        // Example: setResult(Activity.RESULT_OK); finish()
                    }
                }

                is DFComponentState.Error -> {
                    // If an error occurs for the feature we were awaiting confirmation for,
                    // clear the tracking variable.
                    if (_featureAwaitingConfirmation == uiState.feature) {
                        Log.w(
                            TAG,
                            "Error occurred for feature $_featureAwaitingConfirmation while awaiting confirmation, clearing tracking variable."
                        )
                        _featureAwaitingConfirmation = null
                    }
                }

                is DFComponentState.Loading -> {
                    // If we move back to Loading for the feature we were awaiting confirmation for
                    // (e.g., user confirmed, download resuming), we might keep _featureAwaitingConfirmation
                    // until the ActivityResult comes back, or clear it here depending on desired logic.
                    // The current logic relies on the ActivityResult callback to clear it.
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            Log.w(TAG, "handleIntent received a null intent.")
            // Consider finishing or showing an error if the activity requires an intent
            // finish()
            return
        }

        val uriString: String? = when {
            intent.action == Intent.ACTION_VIEW -> intent.data?.toString()
            intent.hasExtra(EXTRA_TARGET) -> intent.getStringExtra(EXTRA_TARGET)
            else -> null
        }

        if (uriString != null) {
            Log.d(TAG, "Processing URI: $uriString")
            processRoute(uriString)
        } else {
            Log.w(
                TAG,
                "Intent received without actionable URI (Action: ${intent.action}, Has Extra: ${
                    intent.hasExtra(EXTRA_TARGET)
                })"
            )
            // Handle invalid/unusable intents (e.g., finish, show error)
            // Maybe load a default state via ViewModel?
            // finish()
        }
    }

    private fun processRoute(uri: String) {
        val dynamicRoute = DFComponentUriRouteParser.extractRoute(uri)

        if (dynamicRoute.status == "success" && dynamicRoute.route.isNotEmpty()) {
            Log.d(
                TAG,
                "Extracted valid route: ${dynamicRoute.route}. Dispatching LoadFeature intent."
            )
            viewModel.processIntent(DFComponentIntent.LoadFeature(dynamicRoute.route))

        } else if (dynamicRoute.status == "success" && dynamicRoute.navigationKey.isNotEmpty()) {
            Log.w(
                TAG,
                "Navigation by key (${dynamicRoute.navigationKey}) needs specific implementation."
            )
            viewModel.processIntent(DFComponentIntent.LoadFeature(dynamicRoute.navigationKey))

        } else {
            Log.e(
                TAG,
                "Failed to extract a valid route or navigation key from URI: $uri. Status: ${dynamicRoute.status}"
            )
            // Handle invalid route (e.g., show error message via ViewModel, finish activity)
            // viewModel.processIntent(DFComponentIntent.ShowError("Invalid link"))
            // finish() // Example: Finish if route is invalid
        }
    }
}