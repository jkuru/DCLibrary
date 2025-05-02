package com.kuru.featureflow.component.domain 

import android.content.Context
import android.util.Log
import com.kuru.featureflow.component.interceptor.DFInterceptor
import com.kuru.featureflow.component.serviceloader.DFServiceLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Use case responsible for executing the sequence of post-installation steps
 * required for a dynamic feature module after it's confirmed to be installed.
 * This includes ServiceLoader initialization, running post-install interceptors,
 * and fetching the dynamic screen Composable.
 */
class DFRunPostInstallStepsUseCase @Inject constructor(
    private val serviceLoader: DFServiceLoader,
    private val interceptor: DFInterceptor,
    @ApplicationContext private val context: Context // Inject ApplicationContext
) {

    companion object {
        private const val TAG = "RunPostInstallUseCase"
    }

    /**
     * Executes the post-installation steps for the given feature.
     *
     * @param feature The name of the feature module.
     * @return A [DFRunPostInstallResult] indicating success (with the screen lambda)
     * or failure (with details).
     */
    suspend operator fun invoke(feature: String): DFRunPostInstallResult {
        Log.i(TAG, "Running post-install steps for feature: $feature")

        // Step 1: Run ServiceLoader Initialization
        val serviceLoaderSuccess = try {
            Log.d(TAG, "Attempting ServiceLoader initialization for $feature...")
            serviceLoader.runServiceLoaderInitialization(feature, context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "ServiceLoader initialization failed for $feature", e)
            return DFRunPostInstallResult.Failure(
                step = Step.SERVICE_LOADER_INITIALIZATION,
                message = "ServiceLoader initialization threw an exception for $feature.",
                cause = e
            )
        }

        if (!serviceLoaderSuccess) {
            Log.w(TAG, "ServiceLoader initialization reported failure for $feature.")
            return DFRunPostInstallResult.Failure(
                step = Step.SERVICE_LOADER_INITIALIZATION,
                message = "ServiceLoader initialization failed for $feature (returned false)."
            )
        }
        Log.i(TAG, "ServiceLoader initialization successful for $feature.")

        // Step 2: Run Post-Install Interceptors
        val interceptorSuccess = try {
            Log.d(TAG, "Running post-install interceptors for $feature...")
            interceptor.runPostInstallInterceptors(feature)
        } catch (e: Exception) {
            Log.e(TAG, "Post-install interceptors failed for $feature", e)
            return DFRunPostInstallResult.Failure(
                step = Step.POST_INSTALL_INTERCEPTORS,
                message = "Post-install interceptors threw an exception for $feature.",
                cause = e
            )
        }

        if (!interceptorSuccess) {
            Log.w(TAG, "Post-install interceptors reported failure for $feature.")
            // Note: DFInterceptorManager currently returns true even if config not found,
            // but returns false if an interceptor task fails. Adjust message if needed.
            return DFRunPostInstallResult.Failure(
                step = Step.POST_INSTALL_INTERCEPTORS,
                message = "One or more post-install interceptors failed for $feature."
            )
        }
        Log.i(TAG, "Post-install interceptors successful for $feature.")

        // Step 3: Fetch Dynamic Screen Lambda
        val screenLambda = try {
            Log.d(TAG, "Fetching dynamic screen lambda for $feature...")
            interceptor.fetchAndSetDynamicScreen(feature)
        } catch (e: Exception) {
            Log.e(TAG, "Fetching dynamic screen lambda failed for $feature", e)
            return DFRunPostInstallResult.Failure(
                step = Step.FETCH_DYNAMIC_SCREEN,
                message = "Fetching dynamic screen lambda threw an exception for $feature.",
                cause = e
            )
        }

        return if (screenLambda != null) {
            Log.i(TAG, "Successfully fetched dynamic screen lambda for $feature.")
            DFRunPostInstallResult.Success(screenLambda)
        } else {
            Log.e(TAG, "Failed to fetch dynamic screen lambda for $feature (returned null).")
            DFRunPostInstallResult.Failure(
                step = Step.FETCH_DYNAMIC_SCREEN,
                message = "Could not retrieve the screen content for $feature after installation and interceptors."
            )
        }
    }
}