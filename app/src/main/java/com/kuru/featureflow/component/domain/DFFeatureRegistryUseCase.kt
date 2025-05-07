package com.kuru.featureflow.component.domain

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.kuru.featureflow.component.register.DFFeatureConfig
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for managing the registration and retrieval of dynamic feature configurations
 * and their associated UI screens in an Android application. Acts as a central hub for
 * dynamic feature modules, storing configurations and screens in a thread-safe manner.
 */
@Singleton
class DFFeatureRegistryUseCase @Inject constructor() {

    private val registry = ConcurrentHashMap<DFFeatureConfig, @Composable (NavController, List<String>) -> Unit>()

    companion object {
        private const val TAG = "DFFeatureRegistryUseCase"
    }

    /**
     * Registers a dynamic feature's configuration and its associated screen Composable.
     * @param dfFeatureConfig The configuration containing the feature's details.
     * @param screen A Composable function that renders the feature's UI.
     */
    fun register(
        dfFeatureConfig: DFFeatureConfig,
        screen: @Composable (NavController, List<String>) -> Unit
    ) {
        registry[dfFeatureConfig] = screen
        Log.d(TAG, "Registered screen for route: ${dfFeatureConfig.route}")
    }

    /**
     * Retrieves the screen Composable associated with a given configuration.
     * @param dfFeatureConfig The configuration to retrieve the screen for.
     * @return The Composable screen, or null if not registered.
     */
    fun getScreen(
        dfFeatureConfig: DFFeatureConfig
    ): (@Composable (NavController, List<String>) -> Unit)? {
        return registry[dfFeatureConfig]
    }

    /**
     * Retrieves the screen Composable directly by route.
     * @param route The route identifying the feature (e.g., "feature_plants").
     * @return The Composable screen, or null if not registered.
     */
    fun getScreenByRoute(
        route: String
    ): (@Composable (NavController, List<String>) -> Unit)? {
        return registry.keys.find { it.route == route }?.let { config -> registry[config] }
    }

    /**
     * Unregisters a dynamic feature's configuration and screen.
     * @param dfFeatureConfig The configuration to remove.
     * @return True if removed, false if not found.
     */
    fun unregister(dfFeatureConfig: DFFeatureConfig): Boolean {
        val removed = registry.remove(dfFeatureConfig) != null
        if (removed) {
            Log.d(TAG, "Unregistered screen for route: ${dfFeatureConfig.route}")
        }
        return removed
    }

    /**
     * Checks if a configuration is registered.
     * @param dfFeatureConfig The configuration to check.
     * @return True if registered, false otherwise.
     */
    fun isRegistrationValid(dfFeatureConfig: DFFeatureConfig): Boolean {
        return registry.containsKey(dfFeatureConfig)
    }

    /**
     * Retrieves the configuration associated with a given route.
     * @param route The route to search for (e.g., "feature_plants").
     * @return The configuration, or null if not found.
     */
    fun getConfig(route: String): DFFeatureConfig? {
        return registry.keys.find { it.route == route }
    }
}