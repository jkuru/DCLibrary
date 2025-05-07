package com.kuru.featureflow.component.domain


import android.content.Context
import android.util.Log
import com.kuru.featureflow.component.register.DFRegistryComponentEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ServiceLoader
import javax.inject.Inject

/**
 * Interface for wrapping ServiceLoader loading, enabling testability.
 */
interface ServiceLoaderWrapper {
    /**
     * Loads implementations of DFRegistryComponentEntry using the provided class loader.
     * @param service The DFRegistryComponentEntry class to load.
     * @param classLoader The class loader to use.
     * @return An iterable of loaded DFRegistryComponentEntry instances.
     */
    fun loadDFComponentEntry(
        service: Class<DFRegistryComponentEntry>,
        classLoader: ClassLoader
    ): Iterable<DFRegistryComponentEntry>
}

/**
 * Default implementation of ServiceLoaderWrapper using Java's ServiceLoader.
 */
class DefaultServiceLoaderWrapper @Inject constructor() : ServiceLoaderWrapper {
    override fun loadDFComponentEntry(
        service: Class<DFRegistryComponentEntry>,
        classLoader: ClassLoader
    ): Iterable<DFRegistryComponentEntry> {
        return ServiceLoader.load(service, classLoader)
    }
}

/**
 * This use case is responsible for initializing dynamic feature modules by loading
 *  and executing their DFRegistryComponentEntry implementations via ServiceLoader.
 */
class DFInitializeFeatureUseCase @Inject constructor(
    private val registry: DFFeatureRegistryUseCase,
    private val serviceLoaderWrapper: ServiceLoaderWrapper
) {

    companion object {
        private const val TAG = "DFInitializeFeatureUseCase"
    }

    /**
     * Runs ServiceLoader initialization for the specified feature, loading and
     * initializing DFRegistryComponentEntry implementations.
     * @param feature The name of the feature module.
     * @param context The application context.
     * @return True if initialization succeeds and a config is registered, false otherwise.
     */
    suspend fun runServiceLoaderInitialization(feature: String, context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            var foundAndInitializedSuccessfully = false
            try {
                Log.d(TAG, "Running ServiceLoader initialization for feature: $feature")
                val classLoader = context.classLoader
                val serviceLoader = serviceLoaderWrapper.loadDFComponentEntry(
                    DFRegistryComponentEntry::class.java,
                    classLoader
                )
                val iterator = serviceLoader.iterator()
                if (!iterator.hasNext()) {
                    Log.w(TAG, "No DFRegistryComponentEntry implementations found for $feature")
                    return@withContext false
                }
                serviceLoader.forEach { entry ->
                    try {
                        entry.initialize(context.applicationContext)
                        Log.i(TAG, "Initialized DFRegistryComponentEntry: ${entry.javaClass.name}")
                        if (registry.getConfig(feature) != null) {
                            foundAndInitializedSuccessfully = true
                        } else {
                            Log.w(
                                TAG,
                                "DFRegistryComponentEntry ${entry.javaClass.name} initialized but no config found for $feature"
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Failed to initialize DFRegistryComponentEntry ${entry.javaClass.name}",
                            e
                        )
                        return@withContext false
                    }
                }
                if (!foundAndInitializedSuccessfully) {
                    Log.e(TAG, "ServiceLoader ran but failed to register config for $feature")
                } else {
                    Log.i(TAG, "ServiceLoader initialization successful for $feature")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run ServiceLoader for feature $feature", e)
                return@withContext false
            }
            foundAndInitializedSuccessfully
        }
    }
}