package com.kuru.featureflow.component.interceptor

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.kuru.featureflow.component.register.DFComponentRegistry
import com.kuru.featureflow.component.state.DFComponentStateStore
import com.kuru.featureflow.component.state.DFInterceptorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DFInterceptorManager @Inject constructor(
    private val registry: DFComponentRegistry,
    private val stateStore: DFComponentStateStore
) : DFInterceptor {

    override suspend fun runPreInstallInterceptors(feature: String): Boolean {
        val config = registry.getConfig(feature) ?: run {
            Log.w(TAG, "Cannot run pre-install interceptors: Config not found for $feature")
            return true
        }
        val preInstallInterceptors = config.listOfDFComponentInterceptor.filter { it.preInstall }
        Log.e(TAG, "Running ${preInstallInterceptors.size} pre-install interceptors for $feature")
        for ((index, interceptor) in preInstallInterceptors.withIndex()) {
            val interceptorId = "$feature-pre-$index"
            stateStore.setInterceptorState(interceptorId, DFInterceptorState.Active)
            val result = try {
                withContext(Dispatchers.IO) { interceptor.task() }
            } catch (e: Exception) {
                Log.e(TAG, "Pre-install interceptor $index threw exception for $feature", e)
                false
            }
            val finalState =
                if (result) DFInterceptorState.Completed else DFInterceptorState.Failed("Pre-install interceptor $index failed")
            stateStore.setInterceptorState(interceptorId, finalState)
            if (!result) {
                val error = "Pre-install check failed for $feature"
                Log.e(TAG, error)
                return false
            }
        }
        return true
    }

    override suspend fun runPostInstallInterceptors(feature: String): Boolean {
        val config = registry.getConfig(feature) ?: run {
            Log.w(TAG, "Cannot run post-install interceptors: Config not found for $feature")
            return false
        }
        val postInstallInterceptors = config.listOfDFComponentInterceptor.filter { !it.preInstall }
        Log.e(TAG, "Running ${postInstallInterceptors.size} post-install interceptors for $feature")
        for ((index, interceptor) in postInstallInterceptors.withIndex()) {
            val interceptorId = "$feature-post-$index"
            stateStore.setInterceptorState(interceptorId, DFInterceptorState.Active)
            val result = try {
                withContext(Dispatchers.IO) { interceptor.task() }
            } catch (e: Exception) {
                Log.e(TAG, "Post-install interceptor $index threw exception for $feature", e)
                false
            }
            val finalState =
                if (result) DFInterceptorState.Completed else DFInterceptorState.Failed("Post-install interceptor $index failed")
            stateStore.setInterceptorState(interceptorId, finalState)
            if (!result) {
                val error = "Post-install check $index failed for $feature"
                Log.w(TAG, error)
                return false
            }
        }
        return true
    }

    override fun fetchAndSetDynamicScreen(feature: String): @Composable ((NavController, List<String>) -> Unit)? {
        Log.e(TAG, "Attempting to fetch screen lambda for feature: $feature")
        val config = registry.getConfig(feature)
        if (config != null) {
            val screenLambda = registry.getScreen(config)
            if (screenLambda != null) {
                Log.e(TAG, "Successfully fetched and set dynamic screen content for $feature.")
                return screenLambda
            } else {
                Log.e(TAG, "Feature $feature config found, but screen lambda is null in registry.")
                return null
            }
        } else {
            // This might happen if ServiceLoader initialization failed or was slow
            Log.e(
                TAG,
                "Feature $feature config not found in registry after installation/check. Cannot get screen."
            )
            return null
        }
    }

    companion object {
        private const val TAG = "DFInterceptorManager"
    }
}