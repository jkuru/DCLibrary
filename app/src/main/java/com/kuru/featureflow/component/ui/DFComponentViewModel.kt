package com.kuru.featureflow.component.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.android.play.core.splitinstall.SplitInstallSessionState
// Import domain layer components used by the ViewModel
import com.kuru.featureflow.component.state.DFInstallationMonitoringState
import com.kuru.featureflow.component.domain.DFLoadFeatureResult
import com.kuru.featureflow.component.domain.DFLoadFeatureUseCase
import com.kuru.featureflow.component.domain.DFTrackFeatureInstallUseCase
import com.kuru.featureflow.component.domain.DFProcessUriState
import com.kuru.featureflow.component.domain.DFResolveFeatureRouteUseCase
import com.kuru.featureflow.component.state.DFFeatureSetupResult
import com.kuru.featureflow.component.domain.DFCompleteFeatureSetupUseCase
import com.kuru.featureflow.component.state.FeatureSetupStep
// Import state management components
import com.kuru.featureflow.component.state.DFStateStore
import com.kuru.featureflow.component.state.DFErrorCode
import com.kuru.featureflow.component.state.DFInstallationState
// Hilt annotation for ViewModel injection
import dagger.hilt.android.lifecycle.HiltViewModel
// Coroutine utilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
// Dependency Injection
import javax.inject.Inject

/**
 * Represents the various states the UI can be in while loading or interacting
 * with a dynamic feature. This is observed by the UI (Activity/Compose screen)
 * to render the appropriate content.
 */
sealed class DFComponentState {
    /** Indicates the feature is currently being loaded or installed. */
    data object Loading : DFComponentState()

    /** Indicates an error occurred during the process. */
    data class Error(
        val message: String,        // User-friendly error message
        val errorType: ErrorType,   // Category of the error
        val feature: String? = null,// Feature associated with the error, if known
        val dfErrorCode: DFErrorCode? = null // Specific Play Core error code, if applicable
    ) : DFComponentState()

    /** Indicates Play Core requires user confirmation to proceed with installation. */
    data class RequiresConfirmation(val feature: String) : DFComponentState()

    /**
     * Indicates the feature is successfully installed and post-install steps are done.
     * Note: The actual UI might be rendered via `dynamicScreenContent` later.
     * This state confirms readiness.
     */
    data class Success(
        val feature: String,                   // Name of the successfully loaded feature
        val featureInstallationState: DFInstallationState, // Specific state (usually Installed)
        val params: List<String> = emptyList() // Parameters passed during loading
    ) : DFComponentState()
}

/**
 * Categorizes the types of errors that can occur, used in [DFComponentState.Error].
 */
enum class ErrorType {
    NETWORK, STORAGE, INSTALLATION, VALIDATION, URI_INVALID,
    SERVICE_LOADER, UNKNOWN, PRE_INSTALL_INTERCEPTOR, POST_INSTALL_INTERCEPTOR
}

/**
 * Represents user actions or events that the ViewModel needs to process.
 * Dispatched from the UI (Activity/Compose screen).
 */
sealed class DFComponentIntent {
    /** Intent to load a specific dynamic feature with optional parameters. */
    data class LoadFeature(val feature: String, val params: List<String>) : DFComponentIntent()

    /** Intent to retry the last failed feature load attempt. */
    object Retry : DFComponentIntent()

    /** Intent reporting the result of the user confirmation dialog shown by the Activity. */
    data class UserConfirmationResult(val feature: String, val confirmed: Boolean) : DFComponentIntent()

    /** Intent to process a URI, typically from a deep link or initial activity intent. */
    data class ProcessUri(val uri: String) : DFComponentIntent()
}

/**
 * Data class encapsulating the necessary information to show the Play Core confirmation dialog.
 * Emitted as a one-time event via `eventFlow`.
 */
data class ConfirmationEventData(val feature: String, val sessionState: SplitInstallSessionState)

/**
 * ViewModel responsible for orchestrating the loading, installation, and display
 * of dynamic feature modules. It manages the UI state, handles user intents,
 * interacts with domain use cases, and communicates events back to the UI (Activity).
 *
 * @property stateStore Access to persistent and in-memory state (last attempt, install status).
 * @property loadFeatureUseCase Use case for initial feature load checks (validation, install status).
 * @property processUriUseCase Use case for parsing URIs into feature routes or navigation keys.
 * @property runPostInstallStepsUseCase Use case for executing steps after installation (ServiceLoader, interceptors, screen fetch).
 * @property monitorInstallationUseCase Use case for monitoring Play Core installation progress and events.
 * @property handleErrorUseCase Use case for processing errors and determining appropriate UI/state updates.
 */
@HiltViewModel
class DFComponentViewModel @Inject constructor(
    private val stateStore: DFStateStore,
    private val loadFeatureUseCase: DFLoadFeatureUseCase,
    private val processUriUseCase: DFResolveFeatureRouteUseCase,
    private val runPostInstallStepsUseCase: DFCompleteFeatureSetupUseCase,
    private val monitorInstallationUseCase: DFTrackFeatureInstallUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "DFComponentViewModel"
    }

    // --- State Management ---

    /** Tracks the name of the feature currently being processed (loaded or installed). */
    private val _currentFeature = MutableStateFlow<String?>(null)

    /** The primary UI state flow observed by the Compose UI. Defaults to Loading. */
    private val _uiState = MutableStateFlow<DFComponentState>(DFComponentState.Loading)
    val uiState: StateFlow<DFComponentState> = _uiState.asStateFlow() // Exposed as immutable StateFlow

    /** StateFlow holding the dynamically loaded Composable function for the feature's screen. Null initially or when cleared. */
    private val _dynamicScreenContent =
        MutableStateFlow<(@Composable (NavController, List<String>) -> Unit)?>(null)
    val dynamicScreenContent: StateFlow<(@Composable (NavController, List<String>) -> Unit)?> =
        _dynamicScreenContent.asStateFlow() // Exposed as immutable StateFlow

    /** Set to keep track of features successfully processed (post-install steps completed) within this ViewModel instance lifecycle. */
    private val processedFeatures = mutableSetOf<String>()

    /** Holds the parameters associated with the current feature load intent. */
    private val _currentParams = MutableStateFlow<List<String>>(emptyList())

    /** Job tracking the currently active installation monitoring coroutine. Allows cancellation. */
    private var currentInstallJob: Job? = null

    /** Internal state to hold data needed for a pending user confirmation dialog (prevents multiple concurrent dialogs). */
    private var pendingConfirmationData: ConfirmationEventData? = null

    /** SharedFlow for emitting one-time events to the UI (e.g., trigger confirmation dialog). */
    private val _eventFlow = MutableSharedFlow<ConfirmationEventData>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val eventFlow: SharedFlow<ConfirmationEventData> = _eventFlow.asSharedFlow() // Exposed as immutable SharedFlow

    private var currentlyProcessingUri: String? by mutableStateOf(null)
    private var isProcessingJobActive: Boolean by mutableStateOf(false)

    /**
     * Clears the dynamically loaded screen content. Called when navigating away
     * from the dynamic feature's screen or starting a new feature load.
     */
    fun clearDynamicContent() {
        Log.d(TAG, "Clearing dynamic screen content.")
        _dynamicScreenContent.value = null
        // Optionally reset UI state here if needed, e.g., back to Loading
        // _uiState.value = DFComponentState.Loading
    }

    /**
     * Main entry point for processing intents dispatched from the UI.
     * Delegates to specific handler functions based on the intent type.
     * Clears dynamic content when starting a new/different feature load or retry.
     *
     * @param intent The [DFComponentIntent] received from the UI.
     */
    fun processIntent(intent: DFComponentIntent) {
        Log.d(TAG, "Processing intent: $intent")

        // Clear dynamic content preemptively if loading a new feature or retrying
        if (intent is DFComponentIntent.LoadFeature) {
            if (_currentFeature.value != intent.feature || _dynamicScreenContent.value == null) {
                clearDynamicContent()
            }
        } else if (intent is DFComponentIntent.Retry) {
            clearDynamicContent()
        }

        // Route intent to appropriate handler
        when (intent) {
            is DFComponentIntent.LoadFeature -> {
                _currentParams.value = intent.params // Store parameters
                loadFeature(intent.feature)        // Handle feature loading
            }
            is DFComponentIntent.Retry -> retryLastFeature() // Handle retry
            is DFComponentIntent.UserConfirmationResult -> handleUserConfirmationResult(intent.feature, intent.confirmed) // Handle confirmation result
            is DFComponentIntent.ProcessUri -> handleProcessUriIntent(intent.uri) // Handle URI processing
        }
    }

    /**
     * Handles the [DFComponentIntent.ProcessUri] intent.
     * Uses [processUriUseCase] to parse the URI and then dispatches a
     * [DFComponentIntent.LoadFeature] or handles invalid URI errors.
     *
     * @param uri The raw URI string from the intent.
     */
    private fun handleProcessUriIntent(uri: String?) {
        Log.d(TAG, "Handling ProcessUri intent with URI: $uri")
        if (uri == null) {handleErrorState(
            feature = null,
            errorType = ErrorType.URI_INVALID,
            message = "Received null URI."
        ) }
      // --- Duplicate Intent Mitigation Logic ---
        val isActive = currentInstallJob?.isActive == true ||
                uiState.value is DFComponentState.Loading ||
                uiState.value is DFComponentState.RequiresConfirmation

        if (uri == currentlyProcessingUri && isActive) {
            Log.w(TAG, "Ignoring duplicate ProcessUri intent for already processing URI: $uri")
            return // Ignore duplicate for active process
        }
        // If different URI, or same URI but not active, proceed:
        currentlyProcessingUri = uri // Store the URI we are now processing

        // ---- Duplicate Intent  End Mitigation ---
        when (val result = processUriUseCase(uri)) { // Invoke the use case
            is DFProcessUriState.FeatureRoute -> {
                Log.i(TAG, "URI parsed as FeatureRoute: ${result.name}. Dispatching LoadFeature.")
                // Trigger feature loading with parsed details
                processIntent(DFComponentIntent.LoadFeature(result.name, result.params))
            }
            is DFProcessUriState.NavigationRoute -> {
                // Placeholder: Current implementation treats navigation keys like feature routes.
                // Needs specific handling if navigating to non-DF screens is required.
                Log.w(TAG, "URI parsed as NavigationRoute: ${result.key}. Dispatching LoadFeature (needs specific impl).")
                processIntent(DFComponentIntent.LoadFeature(result.key, result.params))
            }
            is DFProcessUriState.InvalidUri -> {
                Log.e(TAG, "URI is invalid: ${result.reason}. Handling error.")
                // Handle the invalid URI error state
                handleErrorState(
                    feature = null, // Feature name might not be known yet
                    errorType = ErrorType.URI_INVALID,
                    message = "Invalid link/request: ${result.reason}"
                )
            }
        }
    }

    /**
     * Executes the post-installation steps (ServiceLoader init, interceptors, screen fetch)
     * using [runPostInstallStepsUseCase]. Updates the UI state to Success and populates
     * `_dynamicScreenContent` on success, or handles errors on failure.
     *
     * @param feature The name of the feature for which to run post-install steps.
     */
    private fun executePostInstallSteps(feature: String) {
        viewModelScope.launch { // Launch in ViewModel's scope
            Log.i(TAG, "Executing post-install steps for feature: $feature")
            when (val result = runPostInstallStepsUseCase(feature)) { // Invoke use case
                is DFFeatureSetupResult.Success -> {
                    Log.i(TAG, "Post-install steps successful for $feature. Screen lambda received.")
                    // Store the fetched Composable lambda
                    _dynamicScreenContent.value = result.screen
                    // Update UI to Success state, confirming readiness
                    val currentInstallState = stateStore.getInstallationState(feature)
                    _uiState.value = DFComponentState.Success(
                        feature = feature,
                        // Ensure state is 'Installed' or use fallback
                        featureInstallationState = if (currentInstallState is DFInstallationState.Installed) currentInstallState else DFInstallationState.Installed,
                        params = _currentParams.value // Include parameters
                    )
                    processedFeatures.add(feature) // Mark feature as fully processed for this session
                }
                is DFFeatureSetupResult.Failure -> {
                    Log.e(TAG, "Post-install steps failed for $feature at step ${result.featureSetupStep}: ${result.message}", result.cause)
                    // Map the failure step to an ErrorType and handle the error state
                    val errorType = when (result.featureSetupStep) {
                        FeatureSetupStep.SERVICE_LOADER_INITIALIZATION -> ErrorType.SERVICE_LOADER
                        FeatureSetupStep.POST_INSTALL_INTERCEPTORS -> ErrorType.POST_INSTALL_INTERCEPTOR
                        FeatureSetupStep.FETCH_DYNAMIC_SCREEN -> ErrorType.SERVICE_LOADER // Or specific UI error
                    }
                    handleErrorState(
                        feature = feature,
                        errorType = errorType,
                        message = result.message,
                        dfErrorCode = null // Potentially extract from result.cause if relevant
                    )
                }
            }
        }
    }

    /**
     * Handles the [DFComponentIntent.LoadFeature] intent.
     * Manages job cancellation for concurrent requests, checks if the feature is already
     * processed, and delegates the core loading logic to [loadFeatureUseCase].
     * Based on the use case result, either proceeds to post-install steps or initiates
     * installation monitoring.
     *
     * @param feature The name of the feature to load.
     */
    private fun loadFeature(feature: String) {
        Log.d(TAG, "Handling loadFeature intent for: $feature")

        // --- ViewModel Orchestration Checks ---
        // Avoid redundant processing if already successfully loaded in this session
        if (processedFeatures.contains(feature) && uiState.value is DFComponentState.Success) {
            Log.d(TAG, "Skipping loadFeature for already processed feature: $feature")
            // If content was cleared, re-run post-install steps to fetch it again
            if (_dynamicScreenContent.value == null) {
                Log.w(TAG, "Feature '$feature' processed, but content missing. Re-running post-steps.")
                executePostInstallSteps(feature)
            }
            return
        }

        // Check if a job is already running *for a different feature*
        if (_currentFeature.value != null && _currentFeature.value != feature && currentInstallJob?.isActive == true) {
            Log.w(TAG, "Cancelling previous job ${currentInstallJob} for feature '${_currentFeature.value}' due to new request for '$feature'")
            currentInstallJob?.cancel(CancellationException("New feature load requested: $feature"))
            currentInstallJob = null
            pendingConfirmationData = null // Clear confirmation state for old feature
            clearDynamicContent()          // Clear old dynamic content
            _currentFeature.value = null   // Reset current feature since the old one is cancelled
        } else if (_currentFeature.value == feature && currentInstallJob?.isActive == true) {
            // If it's the SAME feature and a job is ALREADY active, just log and return.
            // Let the existing job finish. The duplicate intent should have been caught earlier
            // in handleProcessUriIntent, but this adds safety.
            Log.w(TAG, "loadFeature called for the same feature '$feature' which already has an active job. Ignoring redundant load request.")
            return // Exit without cancelling or restarting
        }


        _currentFeature.value = feature // Update the current feature being processed
        Log.d(TAG, "loadFeature: Launching new job for feature '$feature'")
        // Launch a new coroutine for the loading/installation process
        currentInstallJob = viewModelScope.launch {
            // Delegate initial checks and decision making to the LoadFeatureUseCase
            when (val loadResult = loadFeatureUseCase(feature)) {
                is DFLoadFeatureResult.ProceedToPostInstall -> {
                    Log.i(TAG, "Feature '$feature' is installed. Proceeding to post-install steps.")
                    // Feature already installed, run post-install logic
                    executePostInstallSteps(feature)
                }
                is DFLoadFeatureResult.ProceedToInstallationMonitoring -> {
                    Log.i(TAG, "Feature '$feature' not installed. Initiating installation monitoring.")
                    // Feature needs installation, start monitoring
                    initiateInstallation(feature)
                }
                is DFLoadFeatureResult.Failure -> {
                    Log.w(TAG, "LoadFeatureUseCase failed for '$feature': ${loadResult.message}")
                    // Handle errors reported by the initial load check
                    handleErrorState(
                        feature = feature,
                        errorType = loadResult.errorType,
                        message = loadResult.message
                    )
                }
            }

            // Add completion logging for the job launched here
            currentInstallJob?.invokeOnCompletion { throwable ->
                if (throwable is CancellationException) {
                    Log.i(TAG, "loadFeature: Job for '$feature' cancelled. Reason: ${throwable.message}")
                } else if (throwable != null) {
                    Log.e(TAG, "loadFeature: Job for '$feature' completed with error", throwable)
                    if (_uiState.value !is DFComponentState.Error) { // Avoid overriding specific errors
                        handleErrorState(feature, ErrorType.UNKNOWN,"Job failed: ${throwable.message}")
                    }
                } else {
                    Log.i(TAG, "loadFeature: Job for '$feature' completed successfully.")
                }
                // Reset current feature only if this *specific* job instance completed for it
                if (_currentFeature.value == feature && currentInstallJob == this.coroutineContext[Job]) {
                    // Consider if resetting _currentFeature here is correct,
                    // maybe only on error/cancel? Success path might rely on it staying set.
                    // _currentFeature.value = null // Optional reset
                }
            }
        }
        Log.d(TAG, "loadFeature: Assigned job $currentInstallJob for '$feature'")
    }

    /**
     * Initiates the installation monitoring process using [monitorInstallationUseCase].
     * Collects events from the use case flow to update UI state, handle confirmation
     * requests (emitting events to the Activity), trigger post-install steps, or
     * handle terminal failure/cancellation states.
     *
     * @param feature The name of the feature to install and monitor.
     */
    private fun initiateInstallation(feature: String) {
        // Ensure any previous confirmation state for this feature is cleared
        pendingConfirmationData = null

        // Launch the monitoring coroutine, replacing any existing job
        currentInstallJob = viewModelScope.launch {
            try {
                Log.i(TAG, "Starting installation monitoring for: $feature")
                _uiState.value = DFComponentState.Loading // Set initial UI state to Loading

                // Collect events emitted by the MonitorInstallationUseCase flow
                monitorInstallationUseCase.invoke(feature, _currentParams.value)
                    .collect { event ->
                        Log.d(TAG, "Received MonitorInstallation event: ${event::class.simpleName}")
                        when (event) {
                            // Update UI state based on installation progress
                            is DFInstallationMonitoringState.UpdateUiState -> {
                                if (_uiState.value != event.state) { // Avoid redundant updates
                                    _uiState.value = event.state
                                }
                            }
                            // Store confirmation data and emit event to Activity to show dialog
                            is DFInstallationMonitoringState.StorePendingConfirmation -> {
                                // Only store/emit if not already pending for this feature
                                if (pendingConfirmationData?.feature != feature) {
                                    val confirmationData = ConfirmationEventData(feature, event.sessionState)
                                    pendingConfirmationData = confirmationData // Store locally
                                    _eventFlow.tryEmit(confirmationData)    // Emit event to Activity
                                    Log.i(TAG, "Stored pending confirmation and emitted event for $feature.")
                                } else {
                                    Log.w(TAG, "Already pending confirmation for $feature, skipping store/emit.")
                                }
                            }
                            // Clear pending confirmation data (dialog no longer needed or resolved)
                            is DFInstallationMonitoringState.ClearPendingConfirmation -> {
                                if (pendingConfirmationData != null) {
                                    pendingConfirmationData = null
                                    Log.i(TAG, "Cleared pending confirmation for $feature.")
                                }
                            }
                            // Installation successful, trigger post-install steps
                            is DFInstallationMonitoringState.TriggerPostInstallSteps -> {
                                pendingConfirmationData = null // Clear confirmation state on success
                                executePostInstallSteps(feature)
                            }
                            // Installation failed terminally
                            is DFInstallationMonitoringState.InstallationFailedTerminal -> {
                                Log.e(TAG, "Installation failed terminally for $feature: ${event.errorState.message}")
                                pendingConfirmationData = null // Clear confirmation state on failure
                                // UI state should have been updated via UpdateUiState already
                            }
                            // Installation cancelled terminally
                            is DFInstallationMonitoringState.InstallationCancelledTerminal -> {
                                Log.w(TAG, "Installation cancelled terminally for $feature.")
                                pendingConfirmationData = null // Clear confirmation state on cancellation
                                // UI state should have been updated via UpdateUiState already
                            }
                        }
                    }
            } catch (e: CancellationException) {
                // Handle cancellation of the monitoring job itself
                Log.i(TAG, "Installation monitoring cancelled for $feature: ${e.message}")
                pendingConfirmationData = null // Clear confirmation state
                // Optionally update UI state to reflect cancellation if not already handled
            } catch (e: Exception) {
                // Handle unexpected errors during monitoring
                Log.e(TAG, "Error during installation monitoring for $feature", e)
                handleErrorState(
                    feature = feature,
                    errorType = ErrorType.UNKNOWN,
                    message = "Failed during installation monitoring: ${e.message}"
                )
                pendingConfirmationData = null // Clear confirmation state
            }
        }
    }

    /**
     * Handles the [DFComponentIntent.Retry] intent.
     * Retrieves the last attempted feature from [stateStore] and calls [loadFeature]
     * to restart the loading process for it.
     */
    private fun retryLastFeature() {
        viewModelScope.launch {
            val lastFeature = stateStore.getLastAttemptedFeature() // Get last feature URI/name
            if (lastFeature != null) {
                Log.i(TAG, "Retrying last attempted feature: $lastFeature")
                loadFeature(lastFeature) // Re-initiate loadFeature, which handles job cancellation etc.
            } else {
                Log.e(TAG, "No last attempted feature found to retry.")
                _uiState.value = DFComponentState.Error("No feature to retry", ErrorType.VALIDATION, null)
            }
        }
    }

    /**
     * Handles the [DFComponentIntent.UserConfirmationResult] intent, received after the
     * user interacts with the Play Core confirmation dialog shown by the Activity.
     * Clears the pending confirmation state and updates the UI based on whether the
     * user confirmed or declined.
     *
     * @param feature The feature for which the confirmation result was received.
     * @param confirmed True if the user confirmed, false otherwise.
     */
    private fun handleUserConfirmationResult(feature: String, confirmed: Boolean) {
        Log.i(TAG, "User confirmation result received for feature: $feature, Confirmed: $confirmed")

        // Clear the locally tracked pending state - the listener will observe actual progress.
        pendingConfirmationData = null

        if (confirmed) {
            // User confirmed. Installation should resume automatically via Play Core's listener.
            // Optionally revert UI from RequiresConfirmation back to Loading.
            if (_uiState.value is DFComponentState.RequiresConfirmation) {
                _uiState.value = DFComponentState.Loading
            }
            Log.d(TAG, "User confirmed, installation should resume.")
        } else {
            // User declined or cancelled.
            Log.w(TAG, "User declined/cancelled confirmation for $feature.")
            // Set UI state to reflect cancellation (treated as an error).
            handleErrorState(
                feature = feature,
                errorType = ErrorType.INSTALLATION, // Or a more specific 'USER_CANCELLED' type
                message = "User cancelled installation for $feature.",
                dfErrorCode = DFErrorCode.NO_ERROR // Or a specific code for user cancellation if defined
            )
        }
    }

    /**
     * Centralized error handling function.
     * Clears pending confirmation state, uses [handleErrorUseCase] to process the error
     * details and determine the final UI error state and any necessary persistent state updates.
     * Updates the state store and the ViewModel's UI state accordingly.
     *
     * @param feature The feature specifically associated with this error, if known.
     * @param errorType The category of the error.
     * @param message The error message for the UI.
     * @param dfErrorCode Optional specific Play Core error code.
     */
    private fun handleErrorState(
        feature: String?,
        errorType: ErrorType,
        message: String,
        dfErrorCode: DFErrorCode? = null
    ) {
        Log.e(TAG, "Handling error: feature=${feature ?: "N/A"}, type=$errorType, code=$dfErrorCode, msg=$message")

        // Clear any pending confirmation if an error occurs for that feature (or any feature if context unknown)
        if (pendingConfirmationData != null && (feature == null || pendingConfirmationData?.feature == feature)) {
            Log.w(TAG, "Clearing pending confirmation data due to error.")
            pendingConfirmationData = null
        }

        // 1. Delegate error processing to the HandleErrorUseCase
        val errorResult = monitorInstallationUseCase.handleError(
            feature = feature,                   // Specific feature related to this error event
            currentFeature = _currentFeature.value, // Overall feature context in ViewModel
            errorType = errorType,
            message = message,
            dfErrorCode = dfErrorCode
        )

        // 2. Update persistent state store if the use case determined it necessary
        errorResult.installationStateToStore?.let { stateToStore ->
            val featureToUpdate = errorResult.uiErrorState.feature // Use feature name from processed result
            if (featureToUpdate != null && featureToUpdate != "unknown") {
                viewModelScope.launch { // Use appropriate scope/dispatcher if needed
                    Log.d(TAG, "Updating state store for '$featureToUpdate' to: $stateToStore")
                    stateStore.setInstallationState(featureToUpdate, stateToStore)
                }
            } else {
                Log.w(TAG, "Skipping state store update as effective feature is unknown.")
            }
        }

        // 3. Update the ViewModel's UI state flow
        if (_uiState.value != errorResult.uiErrorState) { // Avoid redundant updates
            _uiState.value = errorResult.uiErrorState
            Log.d(TAG, "Updated UI state to Error: ${errorResult.uiErrorState.message}")
        }
    }
}