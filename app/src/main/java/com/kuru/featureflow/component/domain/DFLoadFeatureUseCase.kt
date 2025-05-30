package com.kuru.featureflow.component.domain


import android.util.Log
import com.kuru.featureflow.component.state.DFStateStore
import com.kuru.featureflow.component.ui.ErrorType // Assuming ErrorType enum is accessible
import javax.inject.Inject

/**
 * Represents the outcome of the initial decision-making process when loading a feature.
 * It tells the caller (ViewModel) what the next logical step should be.
 */
sealed class DFLoadFeatureResult {
    /**
     * Indicates the feature is already installed and the caller should proceed
     * directly to executing the post-installation steps.
     */
    data object ProceedToPostInstall : DFLoadFeatureResult()

    /**
     * Indicates the feature is not installed and the caller should proceed
     * to monitor the installation process.
     */
    data object ProceedToInstallationMonitoring : DFLoadFeatureResult()

    /**
     * Indicates that the load feature request failed validation or an
     * unexpected error occurred during the initial checks.
     *
     * @param errorType The category of the error.
     * @param message A description of the failure.
     */
    data class Failure(val errorType: ErrorType, val message: String) : DFLoadFeatureResult()
}

/**
 * This use case manages the initial steps of loading a feature.
 * It validates the feature name, records it as the last attempted feature, checks if the feature is already installed,
 *and determines the subsequent action (proceed to post-install steps or monitor installation).
 */
class DFLoadFeatureUseCase @Inject constructor(
    private val installer: DFFeatureInstaller,
    private val stateStore: DFStateStore
) {
    companion object {
        private const val TAG = "DFLoadFeatureUseCase"
    }

    /**
     * Executes the initial feature loading logic.
     *
     * @param feature The name of the feature module to load.
     * @return A [DFLoadFeatureResult] indicating the outcome and the next step.
     */
    suspend operator fun invoke(feature: String): DFLoadFeatureResult {
        Log.i(TAG, "Initiating load check for feature: $feature")

        // Step 1: Validate Input
        if (feature.isBlank()) {
            Log.w(TAG, "Feature name cannot be blank.")
            return DFLoadFeatureResult.Failure(
                ErrorType.VALIDATION,
                "Feature name cannot be empty"
            )
        }

        // Step 2: Persist Last Attempted Feature
        try {
            stateStore.setLastAttemptedFeature(feature)
            Log.d(TAG, "Stored '$feature' as last attempted feature.")
        } catch (e: Exception) {
            // Log error but potentially continue? Depends on requirements.
            // If storing this is critical, could return Failure here.
            Log.e(TAG, "Failed to store last attempted feature '$feature'", e)
            // Optionally: return LoadFeatureResult.Failure(ErrorType.STORAGE, "Failed to save state")
        }

        // Step 3: Check Installation Status
        return try {
            Log.d(
                TAG,
                "Feature '$feature' installer.isComponentInstalled(feature) going to be called "
            )
            val isInstalled = installer.isFeatureInstalled(feature)
            Log.d(TAG, "Feature '$feature' installed status: $isInstalled")
            if (isInstalled) {
                DFLoadFeatureResult.ProceedToPostInstall
            } else {
                DFLoadFeatureResult.ProceedToInstallationMonitoring
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check installation status for feature '$feature'", e)
            DFLoadFeatureResult.Failure(
                ErrorType.INSTALLATION, // Or ErrorType.UNKNOWN
                "Failed to determine installation status for $feature: ${e.message}"
            )
        }
    }
}