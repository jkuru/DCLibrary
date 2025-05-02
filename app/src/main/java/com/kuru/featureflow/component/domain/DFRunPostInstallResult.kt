package com.kuru.featureflow.component.domain


import androidx.compose.runtime.Composable
import androidx.navigation.NavController

/**
 * Represents the outcome of executing the post-installation steps for a feature.
 */
sealed class DFRunPostInstallResult {
    /**
     * Indicates all post-install steps succeeded and the feature's screen
     * Composable lambda was successfully retrieved.
     *
     * @param screen The Composable function for the feature's UI.
     */
    data class Success(
        val screen: @Composable (NavController, List<String>) -> Unit
    ) : DFRunPostInstallResult()

    /**
     * Indicates that one of the post-install steps failed.
     *
     * @param step The specific step that failed.
     * @param message A description of the failure.
     * @param cause An optional underlying exception that caused the failure.
     */
    data class Failure(
        val step: Step,
        val message: String,
        val cause: Throwable? = null
    ) : DFRunPostInstallResult()
}

/**
 * Represents the specific step within the post-installation process.
 * Used in [DFRunPostInstallResult.Failure] to identify the point of failure.
 */
enum class Step {
    SERVICE_LOADER_INITIALIZATION,
    POST_INSTALL_INTERCEPTORS,
    FETCH_DYNAMIC_SCREEN
}
