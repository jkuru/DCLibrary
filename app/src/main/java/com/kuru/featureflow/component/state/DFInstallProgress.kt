package com.kuru.featureflow.component.state

import com.google.android.play.core.splitinstall.SplitInstallSessionState

/**
 * Represents the progress of a dynamic feature module installation in an Android app.
 * This data class combines the framework's abstracted installation state with the detailed
 * session state from Google Play Core's SplitInstall API.
 *
 * ### Structure
 * - **frameworkState**: A [DFInstallationState] enum or class (assumed part of the framework)
 *   that provides a high-level, simplified representation of the installation progress
 *   (e.g., Downloading, Installed, RequiresConfirmation).
 * - **playCoreState**: An optional [SplitInstallSessionState] object from the Play Core library,
 *   which holds detailed session data like bytes downloaded or total bytes. It’s nullable and
 *   typically populated only when user confirmation is needed (e.g., for large downloads).
 *
 * ### Purpose
 * - Simplifies interaction with the Play Core library by wrapping its detailed state in a
 *   framework-specific abstraction.
 * - Facilitates handling of user confirmation scenarios by providing the necessary session
 *   state when required.
 * - Serves as a unified model for tracking and responding to installation progress in the app.
 *
 * ### Usage
 * - Use this class to monitor and manage the installation of dynamic feature modules.
 * - Check `frameworkState` to determine the current installation status and take appropriate
 *   actions (e.g., show a progress bar or prompt the user).
 * - Access `playCoreState` when it’s non-null to pass session details to UI components or
 *   Play Core APIs, such as when confirming a large download.
 *
 * ### Example
 * ```kotlin
 * val progress = DFInstallProgress(
 *     frameworkState = DFInstallationState.RequiresConfirmation,
 *     playCoreState = splitInstallSessionState
 * )
 * if (progress.frameworkState == DFInstallationState.RequiresConfirmation) {
 *     // Show confirmation dialog using progress.playCoreState
 * }
 * ```
 */
data class DFInstallProgress(
    val frameworkState: DFInstallationState,
    val playCoreState: SplitInstallSessionState? = null // Nullable, only set when relevant (e.g., RequiresConfirmation)
)