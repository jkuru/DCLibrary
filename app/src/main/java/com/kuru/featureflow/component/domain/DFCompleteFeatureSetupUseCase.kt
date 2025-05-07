package com.kuru.featureflow.component.domain

import android.content.Context
import android.util.Log
import com.kuru.featureflow.component.state.DFFeatureSetupResult
import com.kuru.featureflow.component.state.FeatureSetupStep
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject


class DFCompleteFeatureSetupUseCase @Inject constructor(
    private val serviceLoader: DFInitializeFeatureUseCase,
    private val interceptors: DFHandleFeatureInterceptorsUseCase,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "DFCompleteFeatureSetupUseCase"
    }

    /**
     * Executes the post-installation steps for the given feature.
     *
     * @param feature The name of the feature module.
     * @return A [DFFeatureSetupResult] indicating success (with the screen lambda)
     * or failure (with details).
     */
    suspend operator fun invoke(feature: String): DFFeatureSetupResult {
        Log.i(TAG, "Running post-install steps for feature: $feature")

        // Step 1: Run ServiceLoader Initialization
        val serviceLoaderSuccess = try {
            Log.d(TAG, "Attempting ServiceLoader initialization for $feature...")
            serviceLoader.runServiceLoaderInitialization(feature, context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "ServiceLoader initialization failed for $feature", e)
            return DFFeatureSetupResult.Failure(
                featureSetupStep = FeatureSetupStep.SERVICE_LOADER_INITIALIZATION,
                message = "ServiceLoader initialization threw an exception for $feature.",
                cause = e
            )
        }

        if (!serviceLoaderSuccess) {
            Log.w(TAG, "ServiceLoader initialization reported failure for $feature.")
            return DFFeatureSetupResult.Failure(
                featureSetupStep = FeatureSetupStep.SERVICE_LOADER_INITIALIZATION,
                message = "ServiceLoader initialization failed for $feature (returned false)."
            )
        }
        Log.i(TAG, "ServiceLoader initialization successful for $feature.")

        // Step 2: Run Post-Install Interceptors
        val interceptorSuccess = try {
            Log.d(TAG, "Running post-install interceptors for $feature...")
            interceptors.runPostInstallInterceptors(feature)
        } catch (e: Exception) {
            Log.e(TAG, "Post-install interceptors failed for $feature", e)
            return DFFeatureSetupResult.Failure(
                featureSetupStep = FeatureSetupStep.POST_INSTALL_INTERCEPTORS,
                message = "Post-install interceptors threw an exception for $feature.",
                cause = e
            )
        }

        if (!interceptorSuccess) {
            Log.w(TAG, "Post-install interceptors reported failure for $feature.")
            return DFFeatureSetupResult.Failure(
                featureSetupStep = FeatureSetupStep.POST_INSTALL_INTERCEPTORS,
                message = "One or more post-install interceptors failed for $feature."
            )
        }
        Log.i(TAG, "Post-install interceptors successful for $feature.")

        // Step 3: Fetch Dynamic Screen Lambda
        val screenLambda = try {
            Log.d(TAG, "Fetching dynamic screen lambda for $feature...")
            interceptors.fetchDynamicScreen(feature)
        } catch (e: Exception) {
            Log.e(TAG, "Fetching dynamic screen lambda failed for $feature", e)
            return DFFeatureSetupResult.Failure(
                featureSetupStep = FeatureSetupStep.FETCH_DYNAMIC_SCREEN,
                message = "Fetching dynamic screen lambda threw an exception for $feature.",
                cause = e
            )
        }

        return if (screenLambda != null) {
            Log.i(TAG, "Successfully fetched dynamic screen lambda for $feature.")
            DFFeatureSetupResult.Success(screenLambda)
        } else {
            Log.e(TAG, "Failed to fetch dynamic screen lambda for $feature (returned null).")
            DFFeatureSetupResult.Failure(
                featureSetupStep = FeatureSetupStep.FETCH_DYNAMIC_SCREEN,
                message = "Could not retrieve the screen content for $feature after installation and interceptors."
            )
        }
    }
}