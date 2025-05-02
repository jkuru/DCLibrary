package com.kuru.featureflow.component.domain


import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.kuru.featureflow.component.ui.DFComponentState

/**
 * Represents events emitted by the [DFInstallationMonitoringEvent] flow
 * to signal necessary actions or state updates to the ViewModel.
 */
sealed class DFInstallationMonitoringEvent {
    /**
     * Signals that the UI state should be updated based on the installation progress.
     * @param state The new UI state derived from the installation state.
     */
    data class UpdateUiState(val state: DFComponentState) : DFInstallationMonitoringEvent()

    /**
     * Signals that the raw Play Core session state needs to be stored temporarily
     * because user confirmation is required.
     * @param sessionState The state object needed for the confirmation dialog.
     */
    data class StorePendingConfirmation(val sessionState: SplitInstallSessionState) : DFInstallationMonitoringEvent()

    /**
     * Signals that any temporarily stored pending confirmation state should be cleared,
     * likely because the installation has moved past the confirmation step or failed.
     */
    data object ClearPendingConfirmation : DFInstallationMonitoringEvent()

    /**
     * Signals that the installation has successfully completed and the
     * post-installation steps (ServiceLoader, interceptors, screen fetch) should be executed.
     */
    data object TriggerPostInstallSteps : DFInstallationMonitoringEvent()

    /**
     * Signals that the installation flow has terminated with a failure.
     * Includes the final error UI state.
     * @param errorState The specific DFComponentState.Error representing the failure.
     */
    data class InstallationFailedTerminal(val errorState: DFComponentState.Error) : DFInstallationMonitoringEvent()

    /**
     * Signals that the installation flow has terminated due to cancellation.
     */
    data object InstallationCancelledTerminal : DFInstallationMonitoringEvent()
}
