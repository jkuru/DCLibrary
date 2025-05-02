package com.kuru.featureflow.component.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.kuru.featureflow.component.domain.DFInstallationMonitoringEvent
import com.kuru.featureflow.component.domain.DFLoadFeatureResult
import com.kuru.featureflow.component.domain.DFLoadFeatureUseCase
import com.kuru.featureflow.component.domain.DFMonitorInstallationUseCase
import com.kuru.featureflow.component.domain.DFProcessUriResult
import com.kuru.featureflow.component.domain.DFProcessUriUseCase
import com.kuru.featureflow.component.domain.DFRunPostInstallResult
import com.kuru.featureflow.component.domain.DFRunPostInstallStepsUseCase
import com.kuru.featureflow.component.domain.Step
import com.kuru.featureflow.component.state.DFComponentStateStore
import com.kuru.featureflow.component.state.DFErrorCode
import com.kuru.featureflow.component.state.DFHandleErrorUseCase
import com.kuru.featureflow.component.state.DFInstallationState
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

sealed class DFComponentState {
    data object Loading : DFComponentState()
    data class Error(
        val message: String,
        val errorType: ErrorType,
        val feature: String? = null,
        val dfErrorCode: DFErrorCode? = null
    ) : DFComponentState()

    data class RequiresConfirmation(val feature: String) : DFComponentState()
    data class Success(
        val feature: String,
        val featureInstallationState: DFInstallationState,
        val params: List<String> = emptyList()
    ) : DFComponentState()
}


enum class ErrorType {
    NETWORK, STORAGE, INSTALLATION, VALIDATION, URI_INVALID,
    SERVICE_LOADER, UNKNOWN, PRE_INSTALL_INTERCEPTOR, POST_INSTALL_INTERCEPTOR
}


sealed class DFComponentIntent {
    data class LoadFeature(val feature: String, val params: List<String>) :
        DFComponentIntent() // e.g.,params =  ["userId=123", "key=value"]

    object Retry : DFComponentIntent()
    data class UserConfirmationResult(val feature: String,val confirmed: Boolean) : DFComponentIntent()
    data class ProcessUri(val uri: String) : DFComponentIntent()
}

// --- ViewModel Event for triggering side-effects in Activity ---
// Encapsulates data needed for confirmation dialog
data class ConfirmationEventData(val feature: String, val sessionState: SplitInstallSessionState)


@HiltViewModel
class DFComponentViewModel @Inject constructor(
    private val stateStore: DFComponentStateStore,
    private val loadFeatureUseCase: DFLoadFeatureUseCase,
    private val processUriUseCase: DFProcessUriUseCase,
    private val runPostInstallStepsUseCase: DFRunPostInstallStepsUseCase,
    private val monitorInstallationUseCase: DFMonitorInstallationUseCase,
    private val handleErrorUseCase: DFHandleErrorUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "DFComponentViewModel"
    }

    // --- State Flows ---
    private val _currentFeature = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow<DFComponentState>(DFComponentState.Loading)
    val uiState: StateFlow<DFComponentState> = _uiState.asStateFlow()

    // --- ADD StateFlow for the Dynamic Screen Composable ---
    private val _dynamicScreenContent =
        MutableStateFlow<(@Composable (NavController, List<String>) -> Unit)?>(null)
    val dynamicScreenContent: StateFlow<(@Composable (NavController, List<String>) -> Unit)?> =
        _dynamicScreenContent.asStateFlow()
    private val processedFeatures = mutableSetOf<String>()
    private val _currentParams = MutableStateFlow<List<String>>(emptyList())

    private var currentInstallJob: Job? = null

    // --- ViewModel internal state for pending confirmation ---
    private var pendingConfirmationData: ConfirmationEventData? = null
    // ---

    // --- SharedFlow for Events (e.g., show confirmation dialog) ---
    private val _eventFlow = MutableSharedFlow<ConfirmationEventData>(
        replay = 0, // No replay needed
        extraBufferCapacity = 1, // Buffer one event
        onBufferOverflow = BufferOverflow.DROP_OLDEST // Drop oldest if buffer full
    )
    val eventFlow: SharedFlow<ConfirmationEventData> = _eventFlow.asSharedFlow()
    // ---

    // --- ADD Function to clear dynamic content when navigating away or reloading ---
    fun clearDynamicContent() {
        Log.e(TAG, "Clearing dynamic screen content.")
        _dynamicScreenContent.value = null
        // Also reset UI state if appropriate, e.g., back to loading for next feature?
        // _uiState.value = DFComponentState.Loading // Optional: Reset UI state
    }
    // ---

    fun processIntent(intent: DFComponentIntent) {
        Log.d(TAG, "Processing intent: $intent")
        // Clear previous dynamic content when starting a new feature load explicitly
        if (intent is DFComponentIntent.LoadFeature) {
            // Only clear if it's a *different* feature or retry of same feature
            if (_currentFeature.value != intent.feature || _dynamicScreenContent.value == null) {
                clearDynamicContent()
            }
        } else if (intent is DFComponentIntent.Retry) {
            clearDynamicContent()
        }

        when (intent) {
            is DFComponentIntent.LoadFeature -> {
                _currentParams.value = intent.params
                loadFeature(intent.feature)
            }

            is DFComponentIntent.Retry -> {
                retryLastFeature()
            }

            is DFComponentIntent.UserConfirmationResult -> handleUserConfirmationResult(intent.feature,intent.confirmed)
            is DFComponentIntent.ProcessUri -> handleProcessUriIntent(intent.uri)
        }
    }

    private fun handleProcessUriIntent(uri: String?) { // Accept nullable URI
        Log.d(TAG, "Handling ProcessUri intent with URI: $uri")

        when (val result = processUriUseCase(uri)) { // Call the use case
            is DFProcessUriResult.FeatureRoute -> {
                Log.i(
                    TAG,
                    "ProcessUriUseCase result: FeatureRoute. Dispatching LoadFeature intent."
                )
                // Dispatch LoadFeature intent with extracted details
                processIntent(DFComponentIntent.LoadFeature(result.name, result.params))
            }

            is DFProcessUriResult.NavigationRoute -> {
                Log.w(
                    TAG,
                    "ProcessUriUseCase result: NavigationRoute (${result.key}). Requires specific navigation implementation."
                )
                processIntent(DFComponentIntent.LoadFeature(result.key, result.params))
            }

            is DFProcessUriResult.InvalidUri -> {
                Log.e(
                    TAG,
                    "ProcessUriUseCase result: InvalidUri. Dispatching Error intent. Reason: ${result.reason}"
                )
                // Directly call error handler
                handleErrorState(
                    feature = null, // URI processing error might not have feature context yet
                    errorType = ErrorType.URI_INVALID,
                    message = "Invalid link/request: ${result.reason}"
                )
            }
        }
    }


    private fun executePostInstallSteps(feature: String) {
        viewModelScope.launch {
            when (val result = runPostInstallStepsUseCase(feature)) {
                is DFRunPostInstallResult.Success -> {
                    Log.i(
                        TAG,
                        "Post-install steps successful for $feature. Screen lambda received."
                    )
                    _dynamicScreenContent.value =
                        result.screen // Update the dynamic content StateFlow

                    // Update the UI state to Success, indicating the feature is ready to be displayed
                    // Ensure we have the correct InstallationState (should be Installed here)
                    val currentInstallState = stateStore.getInstallationState(feature)
                    _uiState.value = DFComponentState.Success(
                        feature = feature,
                        // Use the known state, default to Installed if somehow unknown
                        featureInstallationState = if (currentInstallState is DFInstallationState.Installed) currentInstallState else DFInstallationState.Installed,
                        params = _currentParams.value
                    )
                    processedFeatures.add(feature) // Mark as successfully processed
                }

                is DFRunPostInstallResult.Failure -> {
                    Log.e(
                        TAG,
                        "Post-install steps failed for $feature at step ${result.step}: ${result.message}",
                        result.cause
                    )
                    // Map the failure step/message to an appropriate ErrorType and call handleErrorState
                    val errorType = when (result.step) {
                        Step.SERVICE_LOADER_INITIALIZATION -> ErrorType.SERVICE_LOADER
                        Step.POST_INSTALL_INTERCEPTORS -> ErrorType.POST_INSTALL_INTERCEPTOR
                        Step.FETCH_DYNAMIC_SCREEN -> ErrorType.SERVICE_LOADER // Or a more specific UI_LOAD_ERROR?
                    }
                    handleErrorState(
                        feature = feature,
                        errorType = errorType,
                        message = result.message,
                        // Pass DFErrorCode if available from cause, otherwise null
                        dfErrorCode = null // Or potentially map from result.cause if possible
                    )
                }
            }
        }
    }

    private fun loadFeature(feature: String) {
        Log.d(TAG, "Processing loadFeature intent for: $feature")

        // --- Start: Checks handled by ViewModel Orchestration ---
        // Skip if feature is already processed successfully in this session
        // Note: 'processedFeatures' helps avoid re-running post-install steps if the
        // user navigates back and forth quickly *within the same ViewModel instance*.
        // The LoadFeatureUseCase itself just checks current installation status.
        if (processedFeatures.contains(feature) && uiState.value is DFComponentState.Success) {
            Log.d(
                TAG,
                "Skipping loadFeature for already processed feature in this session: $feature"
            )
            // Ensure the dynamic content is available if it was cleared
            if (_dynamicScreenContent.value == null) {
                Log.w(
                    TAG,
                    "Feature '$feature' already processed, but dynamic content is null. Re-running post-steps to fetch."
                )
                executePostInstallSteps(feature) // Re-fetch content if needed
            }
            return
        }
        // Handle potential ongoing job for a *different* feature
        if (_currentFeature.value != feature) {
            currentInstallJob?.cancel(CancellationException("New feature load requested: $feature"))
            currentInstallJob = null
            Log.i(TAG, "Cancelled previous job for feature: ${_currentFeature.value}")
            pendingConfirmationData = null // Clear confirmation state if feature changes
            // Clear previous dynamic content when loading a new feature explicitly
            clearDynamicContent() // Ensure this is called when feature *changes*
        } else {
            // If it's the same feature, the existing job might handle it,
            // or maybe we intend to restart? Current logic restarts via new job launch below.
            Log.d(
                TAG,
                "loadFeature called for the same feature: $feature. Existing job will be replaced."
            )
        }
        // --- End: Checks handled by ViewModel Orchestration ---
        _currentFeature.value = feature

        currentInstallJob = viewModelScope.launch {
            // --- Start: Logic now delegated to LoadFeatureUseCase ---
            // The use case handles: input validation, storing last attempt, checking install status
            when (val loadResult = loadFeatureUseCase(feature)) {
                is DFLoadFeatureResult.ProceedToPostInstall -> {
                    Log.i(TAG, "Feature '$feature' is installed. Proceeding to post-install steps.")
                    executePostInstallSteps(feature)
                }

                is DFLoadFeatureResult.ProceedToInstallationMonitoring -> {
                    Log.i(
                        TAG,
                        "Feature '$feature' not installed. Proceeding to installation monitoring."
                    )
                    initiateInstallation(feature) // initiateInstallation now focuses only on monitoring setup
                }

                is DFLoadFeatureResult.Failure -> {
                    Log.w(TAG, "LoadFeatureUseCase failed for '$feature': ${loadResult.message}")
                    handleErrorState(
                        feature = feature, // Pass feature context if available
                        errorType = loadResult.errorType,
                        message = loadResult.message
                    )
                }
            }
            // --- End: Logic now delegated to LoadFeatureUseCase ---

            // Handle potential errors during the *launch* itself (less likely now)
            currentInstallJob?.invokeOnCompletion { throwable ->
                if (throwable != null && throwable !is CancellationException) {
                    Log.e(
                        TAG,
                        "Unhandled error during LoadFeature job launch/completion for $feature",
                        throwable
                    )
                    // Ensure error state is set if the job fails unexpectedly outside handled paths
                    if (_uiState.value !is DFComponentState.Error) {
                        handleErrorState(
                            feature,
                            ErrorType.UNKNOWN,
                            "Failed to load feature: ${throwable.message}"
                        )
                    }
                }
            }
        }
    }

    private fun initiateInstallation(feature: String) {

        // Clear any lingering confirmation state from previous attempts for this feature
        pendingConfirmationData = null

        // Directly launch the monitoring coroutine
        currentInstallJob = viewModelScope.launch {
            try {
                Log.i(TAG, "Starting monitoring via MonitorInstallationUseCase for: $feature")
                _uiState.value = DFComponentState.Loading // Show loading

                // Collect events from the MonitorInstallationUseCase
                monitorInstallationUseCase(feature, _currentParams.value)
                    .collect { event ->
                        // Event handling logic remains the same...
                        Log.d(
                            TAG,
                            "Received event from MonitorInstallationUseCase: ${event::class.simpleName}"
                        )
                        when (event) {
                            is DFInstallationMonitoringEvent.UpdateUiState -> {
                                if (_uiState.value != event.state) {
                                    _uiState.value = event.state
                                }
                            }

                            is DFInstallationMonitoringEvent.StorePendingConfirmation -> {
                                // Only store and emit if we aren't already pending for this feature
                                if (pendingConfirmationData?.feature != feature) {
                                    val confirmationData =
                                        ConfirmationEventData(feature, event.sessionState)
                                    pendingConfirmationData = confirmationData
                                    _eventFlow.tryEmit(confirmationData) // Use tryEmit for SharedFlow
                                    Log.i(
                                        TAG,
                                        "Stored pendingConfirmationData and emitted event for $feature, Session ID: ${event.sessionState.sessionId()}"
                                    )
                                } else {
                                    Log.w(
                                        TAG,
                                        "Already pending confirmation for $feature, skipping store/emit."
                                    )
                                }
                            }

                            is DFInstallationMonitoringEvent.ClearPendingConfirmation -> {
                                if (pendingConfirmationData != null) {
                                    pendingConfirmationData = null
                                    Log.i(
                                        TAG,
                                        "Cleared pendingConfirmationData for $feature due to Clear event"
                                    )
                                }
                            }

                            is DFInstallationMonitoringEvent.TriggerPostInstallSteps -> {
                                pendingConfirmationData = null // Ensure cleared on success
                                executePostInstallSteps(feature)
                            }

                            is DFInstallationMonitoringEvent.InstallationFailedTerminal -> {
                                Log.e(
                                    TAG,
                                    "Installation monitoring terminated with failure for $feature: ${event.errorState}"
                                )
                                pendingConfirmationData = null // Clear on failure
                                // UI state already updated via UpdateUiState event
                            }

                            is DFInstallationMonitoringEvent.InstallationCancelledTerminal -> {
                                Log.w(
                                    TAG,
                                    "Installation monitoring terminated with cancellation for $feature"
                                )
                                pendingConfirmationData = null // Clear on cancellation
                                // UI state already updated via UpdateUiState event
                            }
                        }
                    }

            } catch (e: CancellationException) {
                Log.i(TAG, "Installation monitoring coroutine cancelled for $feature: ${e.message}")
                // Handle cancellation UI state...
                pendingConfirmationData = null // Ensure cleared on cancellation
                // Optionally set UI state to cancelled or error if needed
            } catch (e: Exception) {
                Log.e(TAG, "Error launching/running installation monitoring for $feature", e)
                handleErrorState(
                    feature = feature,
                    errorType = ErrorType.UNKNOWN,
                    message = "Failed during installation monitoring: ${e.message}"
                )
                pendingConfirmationData = null // Ensure cleared on unexpected error
            }
        }
    }

    private fun retryLastFeature() {
        viewModelScope.launch {
            val lastFeature = stateStore.getLastAttemptedFeature()
            if (lastFeature != null) {
                Log.e(TAG, "Retrying feature: $lastFeature")
                loadFeature(lastFeature) // This will cancel existing job and restart
            } else {
                Log.e(TAG, "No last attempted feature found to retry.")
                _uiState.value =
                    DFComponentState.Error("No feature to retry", ErrorType.VALIDATION, null)
            }
        }
    }

    // Called AFTER the Activity receives the result from the Play confirmation dialog
    private fun handleUserConfirmationResult(feature: String, confirmed: Boolean) {
        Log.i(TAG, "User confirmation result received for feature: $feature, Confirmed: $confirmed")

        // Clear the locally stored pending state regardless of outcome
        // Play Core listener will handle the actual installation progress
        pendingConfirmationData = null

        if (confirmed) {
            // User confirmed. Installation should resume via Play Core listener.
            // Optionally update UI back to a generic Loading state if it was RequiresConfirmation
            if (_uiState.value is DFComponentState.RequiresConfirmation) {
                _uiState.value = DFComponentState.Loading
            }
            Log.d(TAG, "User confirmed, installation expected to resume.")
        } else {
            // User declined or cancelled the dialog.
            Log.w(TAG, "User declined or cancelled confirmation for $feature.")
            // Set UI state to an appropriate error/cancelled state
            handleErrorState(
                feature = feature,
                errorType = ErrorType.INSTALLATION, // Or a specific 'USER_CANCELLED' type
                message = "User cancelled installation for $feature.",
                dfErrorCode = DFErrorCode.NO_ERROR // Or a custom code for user cancel
            )
        }
    }



    private fun handleErrorState(
        feature: String?,
        errorType: ErrorType,
        message: String,
        dfErrorCode: DFErrorCode? = null
    ) {
        Log.e(TAG, "Handling error: feature=${feature ?: "N/A"}, type=$errorType, code=$dfErrorCode, msg=$message")

        // --- Clear pending confirmation on any error ---
        if (pendingConfirmationData != null && (feature == null || pendingConfirmationData?.feature == feature)) {
            Log.w(TAG, "Clearing pending confirmation data due to error.")
            pendingConfirmationData = null
        }
        // ---

        // 1. Call the use case to get the processed error states
        val errorResult = handleErrorUseCase.invoke(
            feature = feature,
            currentFeature = _currentFeature.value, // Pass the ViewModel's current context
            errorType = errorType,
            message = message,
            dfErrorCode = dfErrorCode
        )

        // 2. Perform side effect: Update central store if needed
        errorResult.installationStateToStore?.let { stateToStore ->
            // The use case determined the feature context was clear for this error
            // Use the feature name determined by the use case within uiErrorState
            val featureToUpdate = errorResult.uiErrorState.feature
            if (featureToUpdate != null && featureToUpdate != "unknown") {
                viewModelScope.launch { // Ensure storage access is on appropriate scope/dispatcher if needed
                    Log.d(
                        TAG,
                        "Updating state store for feature '$featureToUpdate' to: $stateToStore"
                    )
                    stateStore.setInstallationState(featureToUpdate, stateToStore)
                }
            } else {
                Log.w(TAG, "Skipping state store update as effective feature is unknown.")
            }
        }

        // 4. Perform side effect: Update UI state (ViewModel internal state)
        if (_uiState.value != errorResult.uiErrorState) {
            _uiState.value = errorResult.uiErrorState
            Log.d(TAG, "Updated UI state to: ${errorResult.uiErrorState}")
        }
    }


}