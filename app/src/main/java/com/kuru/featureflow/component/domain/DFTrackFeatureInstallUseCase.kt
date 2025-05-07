package com.kuru.featureflow.component.domain

import android.util.Log
import com.kuru.featureflow.component.state.DFStateStore
import com.kuru.featureflow.component.state.DFInstallationState
import com.kuru.featureflow.component.state.DFErrorCode
import com.kuru.featureflow.component.state.DFFeatureError
import com.kuru.featureflow.component.state.DFInstallationMonitoringState
import com.kuru.featureflow.component.ui.DFComponentState
import com.kuru.featureflow.component.ui.ErrorType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

/**
 * Use case responsible for monitoring the installation progress of a dynamic feature module.
 * It interacts with the installer, updates the persistent state store, and emits events
 * to the ViewModel to coordinate UI updates, confirmation dialogs, and post-installation steps.
 */
open class DFTrackFeatureInstallUseCase @Inject constructor(
    private val installer: DFFeatureInstaller,
    private val stateStore: DFStateStore
) {
    companion object {
        private const val TAG = "DFTrackFeatureInstallUseCase"
    }

    /**
     * Performs generic pre-installation checks (e.g., network, storage).
     * NOTE: This is a placeholder. Implement actual checks as needed.
     *
     * @param feature The name of the feature for context.
     * @return True if checks pass, false otherwise.
     */
    private fun runGenericPreInstallChecks(feature: String): Boolean {
        Log.i(TAG, "Running generic pre-install checks for $feature")
        // TODO: Implement actual checks (network availability, storage space, etc.)
        return true
    }

    /**
     * Maps a specific framework installation error code to a UI error category.
     *
     * @param errorCode The detailed error code from the installation process.
     * @return The corresponding UI ErrorType category.
     */
    fun mapDfErrorCodeToErrorType(errorCode: DFErrorCode): ErrorType {
        return when (errorCode) {
            DFErrorCode.NETWORK_ERROR -> ErrorType.NETWORK
            DFErrorCode.INSUFFICIENT_STORAGE -> ErrorType.STORAGE
            DFErrorCode.API_NOT_AVAILABLE,
            DFErrorCode.PLAY_STORE_NOT_FOUND,
            DFErrorCode.MODULE_UNAVAILABLE,
            DFErrorCode.INVALID_REQUEST,
            DFErrorCode.SESSION_NOT_FOUND,
            DFErrorCode.ACCESS_DENIED,
            DFErrorCode.APP_NOT_OWNED,
            DFErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED,
            DFErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION,
            DFErrorCode.SPLITCOMPAT_VERIFICATION_ERROR,
            DFErrorCode.SPLITCOMPAT_EMULATION_ERROR,
            DFErrorCode.DOWNLOAD_SIZE_EXCEEDED -> ErrorType.INSTALLATION
            DFErrorCode.INTERNAL_ERROR -> ErrorType.UNKNOWN
            DFErrorCode.NO_ERROR -> ErrorType.UNKNOWN
            DFErrorCode.UNKNOWN_ERROR -> ErrorType.UNKNOWN
        }
    }

    /**
     * Processes an error condition and returns the necessary state updates.
     * Integrated from DFHandleErrorUseCase.
     *
     * @param feature The name of the feature directly associated with this error, if known.
     * @param currentFeature The feature currently being processed or last attempted.
     * @param errorType The category of the error.
     * @param message A descriptive error message for the UI.
     * @param dfErrorCode An optional specific installation error code, if applicable.
     * @return A [DFFeatureError] containing the UI state and optional installation state update.
     */
    internal fun handleError(
        feature: String?,
        currentFeature: String?,
        errorType: ErrorType,
        message: String,
        dfErrorCode: DFErrorCode? = null
    ): DFFeatureError {
        // Determine the most relevant feature name for reporting
        val effectiveFeature = feature ?: currentFeature ?: "unknown"

        // Determine the final error code, defaulting to UNKNOWN_ERROR
        val finalErrorCode = dfErrorCode ?: DFErrorCode.UNKNOWN_ERROR

        // Create the UI error state object
        val uiErrorState = DFComponentState.Error(
            message = message,
            errorType = errorType,
            feature = effectiveFeature,
            dfErrorCode = finalErrorCode
        )

        // Determine if we should update the persistent installation state
        val installationStateToStore: DFInstallationState.Failed? = if (feature != null) {
            Log.d(TAG, "Error associated with known feature '$feature'. Preparing Failed state to store.")
            DFInstallationState.Failed(finalErrorCode)
        } else {
            Log.w(TAG, "Error not associated with a specific feature ('feature' param was null). Won't store Failed installation state.")
            null
        }

        return DFFeatureError(
            uiErrorState = uiErrorState,
            installationStateToStore = installationStateToStore
        )
    }

    /**
     * Starts monitoring the installation for the given feature.
     *
     * @param feature The name of the feature module to monitor.
     * @param currentParams The parameters associated with the feature load attempt.
     * @return A Flow emitting [DFInstallationMonitoringState]s for the ViewModel to handle.
     */
    operator fun invoke(
        feature: String,
        currentParams: List<String>
    ): Flow<DFInstallationMonitoringState> = flow {
        Log.d(TAG, "Starting installation monitoring for feature: $feature")
        // Step 1: Perform Pre-Install Checks
        if (!runGenericPreInstallChecks(feature)) {
            Log.w(TAG, "Pre-install checks failed for $feature. Aborting installation monitoring.")
            val errorResult = handleError(
                feature = feature,
                currentFeature = feature,
                errorType = ErrorType.PRE_INSTALL_INTERCEPTOR,
                message = "Pre-installation checks failed for $feature."
            )
            emit(DFInstallationMonitoringState.UpdateUiState(errorResult.uiErrorState))
            emit(DFInstallationMonitoringState.InstallationFailedTerminal(errorResult.uiErrorState))
            // Persist failed state if applicable
            errorResult.installationStateToStore?.let {
                try {
                    stateStore.setInstallationState(feature, it)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to store error state for $feature", e)
                }
            }
            return@flow
        }
        Log.i(TAG, "Pre-install checks passed for $feature.")

        // Step 2: Proceed with Installation Monitoring
        var wasConfirmationPending = false

        installer.installFeature(feature)
            .distinctUntilChanged()
            .transform { installProgress ->
                val frameworkState = installProgress.frameworkState
                val playCoreState = installProgress.playCoreState
                Log.v(TAG, "Processing install progress for $feature: $frameworkState")

                // Persist the raw framework state
                try {
                    stateStore.setInstallationState(feature, frameworkState)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update StateStore for $feature with state $frameworkState", e)
                }

                // Map to UI state
                val uiState = when (frameworkState) {
                    DFInstallationState.NotInstalled,
                    DFInstallationState.Pending,
                    is DFInstallationState.Downloading,
                    is DFInstallationState.Installing,
                    DFInstallationState.Canceling -> DFComponentState.Loading

                    DFInstallationState.Installed -> DFComponentState.Success(
                        feature = feature,
                        featureInstallationState = frameworkState,
                        params = currentParams
                    )

                    is DFInstallationState.Failed -> {
                        val errorResult = handleError(
                            feature = feature,
                            currentFeature = feature,
                            errorType = mapDfErrorCodeToErrorType(frameworkState.errorCode),
                            message = "Installation failed (Code: ${frameworkState.errorCode.name})",
                            dfErrorCode = frameworkState.errorCode
                        )
                        errorResult.uiErrorState
                    }

                    DFInstallationState.RequiresConfirmation -> DFComponentState.RequiresConfirmation(
                        feature = feature
                    )

                    DFInstallationState.Canceled -> {
                        val errorResult = handleError(
                            feature = feature,
                            currentFeature = feature,
                            errorType = ErrorType.INSTALLATION,
                            message = "Installation canceled by user or system.",
                            dfErrorCode = DFErrorCode.NO_ERROR
                        )
                        errorResult.uiErrorState
                    }

                    DFInstallationState.Unknown -> {
                        val errorResult = handleError(
                            feature = feature,
                            currentFeature = feature,
                            errorType = ErrorType.UNKNOWN,
                            message = "Unknown installation state encountered.",
                            dfErrorCode = DFErrorCode.UNKNOWN_ERROR
                        )
                        errorResult.uiErrorState
                    }
                }
                emit(DFInstallationMonitoringState.UpdateUiState(uiState))

                // Handle Confirmation State Logic
                if (frameworkState is DFInstallationState.RequiresConfirmation) {
                    if (playCoreState != null) {
                        emit(DFInstallationMonitoringState.StorePendingConfirmation(playCoreState))
                        wasConfirmationPending = true
                        Log.d(TAG, "Emitted StorePendingConfirmation for $feature")
                    } else {
                        Log.e(TAG, "RequiresConfirmation state received for $feature but playCoreState is null!")
                        val errorResult = handleError(
                            feature = feature,
                            currentFeature = feature,
                            errorType = ErrorType.UNKNOWN,
                            message = "Internal error: Missing confirmation details."
                        )
                        emit(DFInstallationMonitoringState.InstallationFailedTerminal(errorResult.uiErrorState))
                        // Persist failed state if applicable
                        errorResult.installationStateToStore?.let {
                            try {
                                stateStore.setInstallationState(feature, it)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to store error state for $feature", e)
                            }
                        }
                    }
                } else if (wasConfirmationPending && frameworkState !is DFInstallationState.Pending) {
                    emit(DFInstallationMonitoringState.ClearPendingConfirmation)
                    wasConfirmationPending = false
                    Log.d(TAG, "Emitted ClearPendingConfirmation for $feature as state is $frameworkState")
                }

                // Handle Terminal States
                when (frameworkState) {
                    is DFInstallationState.Installed -> {
                        Log.i(TAG, "Installation complete for $feature. Emitting TriggerPostInstallSteps.")
                        emit(DFInstallationMonitoringState.TriggerPostInstallSteps)
                    }
                    is DFInstallationState.Failed -> {
                        Log.w(TAG, "Installation failed for $feature. Emitting InstallationFailedTerminal.")
                        if (uiState is DFComponentState.Error) {
                            emit(DFInstallationMonitoringState.InstallationFailedTerminal(uiState))
                        }
                    }
                    is DFInstallationState.Canceled -> {
                        Log.w(TAG, "Installation cancelled for $feature. Emitting InstallationCancelledTerminal.")
                        emit(DFInstallationMonitoringState.InstallationCancelledTerminal)
                    }
                    else -> {}
                }
            }
            .catch { e ->
                Log.e(TAG, "Error caught in installation flow for $feature", e)
                if (e is CancellationException) {
                    throw e
                } else {
                    val errorResult = handleError(
                        feature = feature,
                        currentFeature = feature,
                        errorType = ErrorType.UNKNOWN,
                        message = "An unexpected error occurred during installation: ${e.message}"
                    )
                    emit(DFInstallationMonitoringState.UpdateUiState(errorResult.uiErrorState))
                    emit(DFInstallationMonitoringState.InstallationFailedTerminal(errorResult.uiErrorState))
                    // Persist failed state if applicable
                    errorResult.installationStateToStore?.let {
                        try {
                            stateStore.setInstallationState(feature, it)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to store error state for $feature", e)
                        }
                    }
                }
            }
            .collect { event ->
                Log.d(TAG, "Emitting DFInstallationMonitoringEvent for $feature: $event")
                emit(event)
            }
    }
}