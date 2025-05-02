package com.kuru.featureflow.component.state


import com.kuru.featureflow.component.ui.DFComponentState

/**
 * Represents the outcome of processing an error, providing the necessary
 * state updates for the ViewModel.
 *
 * @property uiErrorState The DFComponentState.Error to be emitted to the UI.
 * @property installationStateToStore An optional DFInstallationState.Failed to be
 * stored in the state store if the feature context is known.
 */
data class DFErrorHandlingResult(
    val uiErrorState: DFComponentState.Error,
    val installationStateToStore: DFInstallationState.Failed?
)
