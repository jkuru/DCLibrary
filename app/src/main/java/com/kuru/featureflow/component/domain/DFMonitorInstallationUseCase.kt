package com.kuru.featureflow.component.domain

import android.util.Log
import com.kuru.featureflow.component.googleplay.DFComponentInstaller
import com.kuru.featureflow.component.state.DFComponentStateStore
import com.kuru.featureflow.component.state.DFInstallationState
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
 * It interacts with the installer, updates the persistent state store, maps installation
 * states to UI states, and emits events to the ViewModel to coordinate UI updates,
 * confirmation dialogs, and post-installation steps.
 */
class DFMonitorInstallationUseCase @Inject constructor(
    private val installer: DFComponentInstaller,
    private val stateStore: DFComponentStateStore,
    private val handleInstallationStateUseCase: DFHandleInstallationStateUseCase
) {
    companion object {
        private const val TAG = "MonitorInstallUseCase"
    }

    /**
     * Performs generic pre-installation checks (e.g., network, storage).
     * NOTE: This is a placeholder. Implement actual checks as needed.
     * Inject dependencies like Context, NetworkManager, etc., if required.
     *
     * @param feature The name of the feature for context.
     * @return True if checks pass, false otherwise.
     */
    private fun runGenericPreInstallChecks(feature: String): Boolean {
        Log.i(TAG, "Running generic pre-install checks for $feature")
        // TODO: Implement actual checks (network availability, storage space, etc.)
        // Example:
        // val hasNetwork = networkChecker.isConnected()
        // val hasEnoughStorage = storageChecker.hasEnoughSpace()
        // if (!hasNetwork) { Log.w(TAG,"PreInstallCheck failed: No network"); return false }
        // if (!hasEnoughStorage) { Log.w(TAG,"PreInstallCheck failed: Insufficient storage"); return false }
        return true // Placeholder
    }

    /**
     * Starts monitoring the installation for the given feature.
     *
     * @param feature The name of the feature module to monitor.
     * @param currentParams The parameters associated with the feature load attempt.
     * @return A Flow emitting [DFInstallationMonitoringEvent]s for the ViewModel to handle.
     */
    operator fun invoke(
        feature: String,
        currentParams: List<String>
    ): Flow<DFInstallationMonitoringEvent> = flow {
        Log.d(TAG, "Starting installation monitoring for feature: $feature")
        // Step 1: Perform Pre-Install Checks
        if (!runGenericPreInstallChecks(feature)) {
            Log.w(TAG, "Pre-install checks failed for $feature. Aborting installation monitoring.")
            val errorState = DFComponentState.Error(
                message = "Pre-installation checks failed for $feature.",
                errorType = ErrorType.PRE_INSTALL_INTERCEPTOR,
                feature = feature
            )
            emit(DFInstallationMonitoringEvent.UpdateUiState(errorState)) // Update UI
            emit(DFInstallationMonitoringEvent.InstallationFailedTerminal(errorState)) // Signal terminal failure
            return@flow // Complete the flow immediately
        }
        Log.i(TAG, "Pre-install checks passed for $feature.")

        // Step 2: Proceed with Installation Monitoring if checks passed
        // Flag to track if we previously emitted StorePendingConfirmation
        // to decide when to emit ClearPendingConfirmation.
        var wasConfirmationPending = false

        installer.installComponent(feature) // Returns Flow<DFInstallProgress>
            .distinctUntilChanged() // Avoid processing identical progress updates
            .transform { installProgress -> // Use transform to emit multiple events per input
                val frameworkState = installProgress.frameworkState
                val playCoreState = installProgress.playCoreState // May be null

                Log.v(TAG, "Processing install progress for $feature: $frameworkState")

                // 1. Persist the raw framework state
                try {
                    stateStore.setInstallationState(feature, frameworkState)
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Failed to update StateStore for $feature with state $frameworkState",
                        e
                    )
                    // Decide if this failure should halt the flow or just be logged
                }


                // 2. Map to UI state
                val uiState = handleInstallationStateUseCase(
                    feature = feature,
                    installationState = frameworkState,
                    currentParams = currentParams
                )
                emit(DFInstallationMonitoringEvent.UpdateUiState(uiState)) // Emit UI state update


                // 3. Handle Confirmation State Logic
                if (frameworkState is DFInstallationState.RequiresConfirmation) {
                    if (playCoreState != null) {
                        emit(DFInstallationMonitoringEvent.StorePendingConfirmation(playCoreState))
                        wasConfirmationPending = true
                        Log.d(TAG, "Emitted StorePendingConfirmation for $feature")
                    } else {
                        // This is an error state - RequiresConfirmation state without the needed Play Core state
                        Log.e(
                            TAG,
                            "RequiresConfirmation state received for $feature but playCoreState is null!"
                        )
                        // Emit a failure event?
                        emit(
                            DFInstallationMonitoringEvent.InstallationFailedTerminal(
                                DFComponentState.Error(
                                    message = "Internal error: Missing confirmation details.",
                                    errorType = ErrorType.UNKNOWN,
                                    feature = feature
                                )
                            )
                        )
                        // Potentially terminate the flow here if desired
                    }
                } else if (wasConfirmationPending && frameworkState !is DFInstallationState.Pending) {
                    // If we previously required confirmation, but no longer do (and are not just Pending),
                    // emit event to clear the stored state in the ViewModel.
                    // We wait until it's not Pending to avoid clearing too early if states fluctuate.
                    emit(DFInstallationMonitoringEvent.ClearPendingConfirmation)
                    wasConfirmationPending = false
                    Log.d(
                        TAG,
                        "Emitted ClearPendingConfirmation for $feature as state is $frameworkState"
                    )
                }

                // 4. Handle Terminal States / Trigger Next Steps
                when (frameworkState) {
                    is DFInstallationState.Installed -> {
                        Log.i(
                            TAG,
                            "Installation complete for $feature. Emitting TriggerPostInstallSteps."
                        )
                        emit(DFInstallationMonitoringEvent.TriggerPostInstallSteps)
                        // Note: Flow collection will typically end here naturally if the
                        // underlying installer flow completes after INSTALLED.
                    }

                    is DFInstallationState.Failed -> {
                        Log.w(
                            TAG,
                            "Installation failed for $feature. Emitting InstallationFailedTerminal."
                        )
                        // We already emitted UpdateUiState(Error), now emit terminal event
                        if (uiState is DFComponentState.Error) {
                            emit(DFInstallationMonitoringEvent.InstallationFailedTerminal(uiState))
                        } else {
                            // Fallback if uiState wasn't Error (shouldn't happen)
                            emit(
                                DFInstallationMonitoringEvent.InstallationFailedTerminal(
                                    handleInstallationStateUseCase(
                                        feature,
                                        frameworkState,
                                        currentParams
                                    ) as DFComponentState.Error
                                )
                            )
                        }
                    }

                    is DFInstallationState.Canceled -> {
                        Log.w(
                            TAG,
                            "Installation cancelled for $feature. Emitting InstallationCancelledTerminal."
                        )
                        // We already emitted UpdateUiState(Error), now emit terminal event
                        emit(DFInstallationMonitoringEvent.InstallationCancelledTerminal)
                    }

                    else -> {
                        // Non-terminal state, handled above (UI update, confirmation logic)
                    }
                }
            }
            .catch { e -> // Catch errors from the installer flow itself
                Log.e(TAG, "Error caught in installation flow for $feature", e)
                if (e is CancellationException) {
                    // Propagate cancellation if needed or handle gracefully
                    throw e // Re-throw cancellation
                } else {
                    // Emit a terminal failure event for unexpected errors
                    val errorState = DFComponentState.Error(
                        message = "An unexpected error occurred during installation: ${e.message}",
                        errorType = ErrorType.UNKNOWN,
                        feature = feature
                    )
                    emit(DFInstallationMonitoringEvent.UpdateUiState(errorState)) // Update UI first
                    emit(DFInstallationMonitoringEvent.InstallationFailedTerminal(errorState)) // Then emit terminal event
                }
            }
            .collect { event ->
                Log.d(TAG, "Emitting DFInstallationMonitoringEvent for $feature: $event")
                emit(event) // Emit to outer flow
            }
    }
}