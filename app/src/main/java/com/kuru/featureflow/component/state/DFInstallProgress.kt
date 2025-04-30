package com.kuru.featureflow.component.state

import com.google.android.play.core.splitinstall.SplitInstallSessionState

/**
 * Wraps the framework's InstallationState and the original Play Core state,
 * primarily used to pass the SplitInstallSessionState when confirmation is required.
 */
data class DFInstallProgress(
    val frameworkState: DFInstallationState,
    val playCoreState: SplitInstallSessionState? = null // Nullable, only set when relevant (e.g., RequiresConfirmation)
)