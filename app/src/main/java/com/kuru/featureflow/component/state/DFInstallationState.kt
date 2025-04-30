package com.kuru.featureflow.component.state

import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode

/**
 * Represents the installation state of a dynamic feature module.
 * Includes progress and specific error codes.
 */
sealed class DFInstallationState {
    /** Module is not installed. */
    data object NotInstalled : DFInstallationState()

    /** Module installation is pending. */
    data object Pending : DFInstallationState()

    /** Module is currently downloading. */
    data class Downloading(val progress: Int) : DFInstallationState() // Progress 0-100

    /** Module is currently installing after download. */
    data class Installing(val progress: Int) : DFInstallationState() // Progress 0-100

    /** Module is successfully installed. */
    data object Installed : DFInstallationState()

    /** Module installation failed. */
    data class Failed(val errorCode: DFErrorCode) : DFInstallationState()

    /** User confirmation is required to continue installation (e.g., large download). */
    data object RequiresConfirmation : DFInstallationState()

    /** Installation is currently canceling. */
    data object Canceling : DFInstallationState()

    /** Installation has been canceled. */
    data object Canceled : DFInstallationState()

    /** Module is unknown (should not typically happen). */
    data object Unknown : DFInstallationState()
}

/**
 * Maps Play Core SplitInstallErrorCodes to framework-specific error codes.
 */
enum class DFErrorCode(val code: Int) {
    NO_ERROR(SplitInstallErrorCode.NO_ERROR),
    ACTIVE_SESSIONS_LIMIT_EXCEEDED(SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED),
    MODULE_UNAVAILABLE(SplitInstallErrorCode.MODULE_UNAVAILABLE),
    INVALID_REQUEST(SplitInstallErrorCode.INVALID_REQUEST),
    SESSION_NOT_FOUND(SplitInstallErrorCode.SESSION_NOT_FOUND),
    API_NOT_AVAILABLE(SplitInstallErrorCode.API_NOT_AVAILABLE),
    NETWORK_ERROR(SplitInstallErrorCode.NETWORK_ERROR),
    ACCESS_DENIED(SplitInstallErrorCode.ACCESS_DENIED),
    INCOMPATIBLE_WITH_EXISTING_SESSION(SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION),
    INSUFFICIENT_STORAGE(SplitInstallErrorCode.INSUFFICIENT_STORAGE),
    SPLITCOMPAT_VERIFICATION_ERROR(SplitInstallErrorCode.SPLITCOMPAT_VERIFICATION_ERROR),
    INTERNAL_ERROR(SplitInstallErrorCode.INTERNAL_ERROR),
    SPLITCOMPAT_EMULATION_ERROR(SplitInstallErrorCode.SPLITCOMPAT_EMULATION_ERROR),
    PLAY_STORE_NOT_FOUND(SplitInstallErrorCode.PLAY_STORE_NOT_FOUND),
    APP_NOT_OWNED(-15), // SplitInstallErrorCode.APP_NOT_OWNED is hidden
    DOWNLOAD_SIZE_EXCEEDED(-100), // Custom code if needed for RequiresConfirmation mapping
    UNKNOWN_ERROR(-99); // For mapping errors not explicitly covered

    companion object {
        fun fromSplitInstallErrorCode(errorCode: Int): DFErrorCode {
            return entries.find { it.code == errorCode } ?: UNKNOWN_ERROR
        }
    }
}

