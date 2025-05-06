package com.kuru.featureflow.component.serviceloader


import android.content.Context
import android.util.Log
import com.kuru.featureflow.component.register.DFComponentEntry
import com.kuru.featureflow.component.register.DFComponentRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DFServiceLoaderManager @Inject constructor(
    private val registry: DFComponentRegistry,
    private val serviceLoaderWrapper: ServiceLoaderWrapper
) : DFServiceLoader {

    override suspend fun runServiceLoaderInitialization(
        feature: String,
        context: Context
    ): Boolean {
        return withContext(Dispatchers.IO) {
            withContext(Dispatchers.IO) {
                var foundAndInitializedSuccessfully = false
                try {
                    Log.e(
                        TAG,
                        "Running ServiceLoader initialization for feature: $feature on ${Thread.currentThread().name}"
                    )
                    val classLoader = context.classLoader
                    val serviceLoader = serviceLoaderWrapper.loadDFComponentEntry(DFComponentEntry::class.java, classLoader)
                    // Use iterator explicitly to check if any entries were found at all
                    val iterator = serviceLoader.iterator()
                    if (!iterator.hasNext()) {
                        Log.w(
                            TAG,
                            "ServiceLoader found no implementations of DFComponentEntry for feature $feature using classloader: $classLoader."
                        )
                        return@withContext false
                    }
                    serviceLoader.forEach { entry ->
                        try {
                            entry.initialize(context.applicationContext)
                            Log.i(TAG, "Initialized DFComponentEntry: ${entry.javaClass.name}")
                            if (registry.getConfig(feature) != null) {
                                foundAndInitializedSuccessfully = true
                            } else {
                                Log.w(
                                    TAG,
                                    "DFComponentEntry ${entry.javaClass.name} initialized but config for $feature not found in registry."
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(
                                TAG,
                                "Failed to initialize DFComponentEntry ${entry.javaClass.name}",
                                e
                            )
                            return@withContext false
                        }
                    }
                    if (!foundAndInitializedSuccessfully) {
                        Log.e(
                            TAG,
                            "ServiceLoader ran, but failed to find/initialize/register config for $feature"
                        )
                    } else {
                        Log.i(TAG, "ServiceLoader initialization appears successful for $feature")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to run ServiceLoader for feature $feature", e)
                }
                foundAndInitializedSuccessfully
            }
        }
    }

    companion object {
        private const val TAG = "DFServiceLoaderManager"
    }
}