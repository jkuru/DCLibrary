package com.kuru.featureflow.component.domain

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.kuru.featureflow.component.state.DFStateStore
import com.kuru.featureflow.component.state.DFFeatureInterceptorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for executing pre- and post-install interceptors and fetching dynamic screens
 * for a dynamic feature module.
 */
class DFHandleFeatureInterceptorsUseCase @Inject constructor(
    private val registry: DFFeatureRegistryUseCase,
    private val stateStore: DFStateStore
) {

    companion object {
        private const val TAG = "DFHandleFeatureInterceptorsUseCase"
    }

    /**
     * Runs pre-install interceptors for the specified feature.
     * @param feature The name of the feature module.
     * @return True if all interceptors succeed or no config is found, false if any fail.
     */
    suspend fun runPreInstallInterceptors(feature: String): Boolean {
        val config = registry.getConfig(feature) ?: run {
            Log.w(TAG, "No config found for $feature, skipping pre-install interceptors")
            return true
        }
        val preInstallInterceptors = config.listOfDFFeatureInterceptor.filter { it.preInstall }
        Log.d(TAG, "Running ${preInstallInterceptors.size} pre-install interceptors for $feature")
        for ((index, interceptor) in preInstallInterceptors.withIndex()) {
            val interceptorId = "$feature-pre-$index"
            stateStore.setInterceptorState(interceptorId, DFFeatureInterceptorState.Active)
            val result = try {
                withContext(Dispatchers.IO) { interceptor.task() }
            } catch (e: Exception) {
                Log.e(TAG, "Pre-install interceptor $index failed for $feature", e)
                false
            }
            val finalState = if (result) {
                DFFeatureInterceptorState.Completed
            } else {
                DFFeatureInterceptorState.Failed("Pre-install interceptor $index failed")
            }
            stateStore.setInterceptorState(interceptorId, finalState)
            if (!result) {
                Log.e(TAG, "Pre-install check failed for $feature")
                return false
            }
        }
        Log.d(TAG, "Pre-install interceptors completed successfully for $feature")
        return true
    }

    /**
     * Runs post-install interceptors for the specified feature.
     * @param feature The name of the feature module.
     * @return True if all interceptors succeed, false if any fail or no config is found.
     */
    suspend fun runPostInstallInterceptors(feature: String): Boolean {
        val config = registry.getConfig(feature) ?: run {
            Log.w(TAG, "No config found for $feature, cannot run post-install interceptors")
            return false
        }
        val postInstallInterceptors = config.listOfDFFeatureInterceptor.filter { !it.preInstall }
        Log.d(TAG, "Running ${postInstallInterceptors.size} post-install interceptors for $feature")
        for ((index, interceptor) in postInstallInterceptors.withIndex()) {
            val interceptorId = "$feature-post-$index"
            stateStore.setInterceptorState(interceptorId, DFFeatureInterceptorState.Active)
            val result = try {
                withContext(Dispatchers.IO) { interceptor.task() }
            } catch (e: Exception) {
                Log.e(TAG, "Post-install interceptor $index failed for $feature", e)
                false
            }
            val finalState = if (result) {
                DFFeatureInterceptorState.Completed
            } else {
                DFFeatureInterceptorState.Failed("Post-install interceptor $index failed")
            }
            stateStore.setInterceptorState(interceptorId, finalState)
            if (!result) {
                Log.w(TAG, "Post-install check $index failed for $feature")
                return false
            }
        }
        Log.d(TAG, "Post-install interceptors completed successfully for $feature")
        return true
    }

    /**
     * Fetches the dynamic screen Composable for the specified feature.
     * @param feature The name of the feature module.
     * @return The Composable screen lambda, or null if not found.
     */
    fun fetchDynamicScreen(feature: String): (@Composable (NavController, List<String>) -> Unit)? {
        Log.d(TAG, "Fetching screen lambda for feature: $feature")
        val config = registry.getConfig(feature)
        if (config != null) {
            val screenLambda = registry.getScreen(config)
            if (screenLambda != null) {
                Log.d(TAG, "Successfully fetched dynamic screen for $feature")
                return screenLambda
            } else {
                Log.w(TAG, "Config found for $feature, but screen lambda is null")
                return null
            }
        } else {
            Log.e(TAG, "No config found for $feature, cannot fetch screen")
            return null
        }
    }
}