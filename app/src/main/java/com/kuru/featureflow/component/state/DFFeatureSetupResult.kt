package com.kuru.featureflow.component.state


import androidx.compose.runtime.Composable
import androidx.navigation.NavController

/**
 * Represents the outcome of executing the post-installation steps for a feature.
 */
sealed class DFFeatureSetupResult {
    /**
     * Indicates all post-install steps succeeded and the feature's screen
     * Composable lambda was successfully retrieved.
     *
     * @param screen The Composable function for the feature's UI.
     */
    data class Success(
        val screen: @Composable (NavController, List<String>) -> Unit
    ) : DFFeatureSetupResult()

    /**
     * Indicates that one of the post-install steps failed.
     *
     * @param featureSetupStep The specific step that failed.
     * @param message A description of the failure.
     * @param cause An optional underlying exception that caused the failure.
     */
    data class Failure(
        val featureSetupStep: FeatureSetupStep,
        val message: String,
        val cause: Throwable? = null
    ) : DFFeatureSetupResult()
}

/**
 * Represents the specific step within the post-installation process.
 * Used in [DFFeatureSetupResult.Failure] to identify the point of failure.
 */
enum class FeatureSetupStep {
    SERVICE_LOADER_INITIALIZATION,
    POST_INSTALL_INTERCEPTORS,
    FETCH_DYNAMIC_SCREEN
}
