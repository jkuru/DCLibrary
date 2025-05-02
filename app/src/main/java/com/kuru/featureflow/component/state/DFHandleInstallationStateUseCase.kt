package com.kuru.featureflow.component.state


import com.kuru.featureflow.component.ui.DFComponentState
import com.kuru.featureflow.component.ui.ErrorType
import javax.inject.Inject

/**
 * Use case responsible for mapping the detailed DFInstallationState
 * to the high-level UI state (DFComponentState).
 */
class DFHandleInstallationStateUseCase @Inject constructor() {

    /**
     * Executes the mapping logic.
     *
     * @param feature The name of the feature whose state is being processed.
     * @param installationState The latest installation state received for the feature.
     * @param currentParams The parameters associated with the current feature load attempt,
     * needed for the Success state.
     * @return The corresponding DFComponentState representing the UI state.
     */
    operator fun invoke(
        feature: String,
        installationState: DFInstallationState,
        currentParams: List<String> = emptyList() // Provide params for success state
    ): DFComponentState {
        return when (installationState) {
            // States mapping to Loading UI
            DFInstallationState.NotInstalled, // Treat as initial loading state
            DFInstallationState.Pending,
            is DFInstallationState.Downloading, // Show progress if needed, but Loading state is simple
            is DFInstallationState.Installing, // Show progress if needed, but Loading state is simple
            DFInstallationState.Canceling -> DFComponentState.Loading

            // State mapping to Success UI
            DFInstallationState.Installed -> DFComponentState.Success(
                feature = feature,
                featureInstallationState = installationState, // Pass the specific state
                params = currentParams
            )

            // State mapping to Failure UI
            is DFInstallationState.Failed -> DFComponentState.Error(
                message = "Installation failed (Code: ${installationState.errorCode.name})",
                errorType = mapDfErrorCodeToErrorType(installationState.errorCode),
                feature = feature,
                dfErrorCode = installationState.errorCode
            )

            // State mapping to Confirmation UI
            DFInstallationState.RequiresConfirmation -> DFComponentState.RequiresConfirmation(
                feature = feature
            )

            // State mapping to Canceled UI (treated as an error)
            DFInstallationState.Canceled -> DFComponentState.Error(
                message = "Installation canceled by user or system.",
                errorType = ErrorType.INSTALLATION, // Specific error type for cancellation
                feature = feature,
                dfErrorCode = DFErrorCode.NO_ERROR // Or a specific 'Canceled' code if defined
            )

            // State mapping to Unknown Error UI
            DFInstallationState.Unknown -> DFComponentState.Error(
                message = "Unknown installation state encountered.",
                errorType = ErrorType.UNKNOWN,
                feature = feature,
                dfErrorCode = DFErrorCode.UNKNOWN_ERROR
            )
        }
    }

    /**
     * Maps a specific framework installation error code (DFErrorCode)
     * to a broader UI error category (ErrorType).
     *
     * Note: This could be extracted into a separate injectable ErrorMapper class
     * if the mapping logic becomes more complex or is needed elsewhere.
     *
     * @param errorCode The detailed error code from the installation process.
     * @return The corresponding UI ErrorType category.
     */
    private fun mapDfErrorCodeToErrorType(errorCode: DFErrorCode): ErrorType {
        return when (errorCode) {
            DFErrorCode.NETWORK_ERROR -> ErrorType.NETWORK
            DFErrorCode.INSUFFICIENT_STORAGE -> ErrorType.STORAGE
            DFErrorCode.API_NOT_AVAILABLE,
            DFErrorCode.PLAY_STORE_NOT_FOUND,
            DFErrorCode.MODULE_UNAVAILABLE,
            DFErrorCode.INVALID_REQUEST,
            DFErrorCode.SESSION_NOT_FOUND,
            DFErrorCode.ACCESS_DENIED,
            DFErrorCode.APP_NOT_OWNED, // Assuming APP_NOT_OWNED implies an installation issue
            DFErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED,
            DFErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION,
            DFErrorCode.SPLITCOMPAT_VERIFICATION_ERROR,
            DFErrorCode.SPLITCOMPAT_EMULATION_ERROR,
            DFErrorCode.DOWNLOAD_SIZE_EXCEEDED // Part of the installation flow needing user action/failure
                -> ErrorType.INSTALLATION

            DFErrorCode.INTERNAL_ERROR -> ErrorType.UNKNOWN // Or potentially INSTALLATION?
            DFErrorCode.NO_ERROR -> ErrorType.UNKNOWN // Should not map to an error type ideally
            DFErrorCode.UNKNOWN_ERROR -> ErrorType.UNKNOWN
        }
    }
}