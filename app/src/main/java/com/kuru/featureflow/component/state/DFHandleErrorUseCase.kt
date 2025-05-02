package com.kuru.featureflow.component.state


import android.util.Log
import com.kuru.featureflow.component.ui.DFComponentState
import com.kuru.featureflow.component.ui.ErrorType
import javax.inject.Inject

/**
 * Use case responsible for processing various error conditions,
 * determining the appropriate UI error state, and deciding if
 * a failed installation state should be persisted.
 */
class DFHandleErrorUseCase @Inject constructor() { // Inject dependencies if needed

    companion object {
        private const val TAG = "HandleErrorUseCase"
    }

    /**
     * Processes an error condition and returns the necessary state updates.
     *
     * @param feature The name of the feature directly associated with this error, if known.
     * @param currentFeature The feature currently being processed or last attempted by the ViewModel.
     * @param errorType The category of the error.
     * @param message A descriptive error message for the UI.
     * @param dfErrorCode An optional specific installation error code, if applicable.
     * @return An [DFErrorHandlingResult] containing the UI state and optional installation state update.
     */
    operator fun invoke(
        feature: String?, // Feature specifically tied to this error occurrence
        currentFeature: String?, // Feature currently in context in the ViewModel
        errorType: ErrorType,
        message: String,
        dfErrorCode: DFErrorCode? = null
    ): DFErrorHandlingResult {

        // Determine the most relevant feature name for reporting
        val effectiveFeature = feature ?: currentFeature ?: "unknown"

        // Determine the final error code, defaulting to UNKNOWN_ERROR
        val finalErrorCode = dfErrorCode ?: DFErrorCode.UNKNOWN_ERROR

        // Create the UI error state object
        val uiErrorState = DFComponentState.Error(
            message = message,
            errorType = errorType,
            feature = effectiveFeature,
            dfErrorCode = finalErrorCode // Pass the determined code
        )

        // Determine if we should update the persistent installation state
        // Only update if the error is directly associated with a *known* feature
        // and it represents an installation failure type.
        val installationStateToStore: DFInstallationState.Failed? = if (feature != null) {
            // We only store a 'Failed' state if the error explicitly belongs
            // to a known feature module's installation/loading process.
            // Avoid setting unrelated features to Failed state based on general errors.
            Log.d(TAG, "Error associated with known feature '$feature'. Preparing Failed state to store.")
            DFInstallationState.Failed(finalErrorCode)
        } else {
            Log.w(TAG, "Error not associated with a specific feature ('feature' param was null). Won't store Failed installation state.")
            null // Do not store if the feature context is unclear for this specific error
        }

        return DFErrorHandlingResult(
            uiErrorState = uiErrorState,
            installationStateToStore = installationStateToStore
        )
    }
}