package com.kuru.featureflow.component.register

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A singleton registry that stores mappings between dynamic feature configurations
 * ([DFComponentConfig]) and their corresponding screen Composables.
 *
 * This class serves as a centralized, thread-safe manager for dynamic feature modules'
 * configurations and UI screens in an Android application. It enables dynamic features
 * to register their configurations and associated Composables, and provides methods
 * to retrieve, check, or remove these registrations as needed.
 *
 * ### Purpose
 * - **Centralized Storage**: Maintains a mapping of [DFComponentConfig] to Composable
 *   functions representing UI screens for dynamic features.
 * - **Thread Safety**: Uses a mutable map within a singleton instance (injected via Hilt)
 *   to ensure safe access and modification in a multi-threaded environment.
 * - **Dynamic Feature Integration**: Facilitates the registration and rendering of
 *   dynamic feature UI screens, supporting modular and scalable app architecture.
 */
@Singleton
class ComponentRegistryData @Inject constructor() {
    // Internal mutable map storing dynamic feature configurations and their screen Composables.
    private val registry: ConcurrentHashMap<DFComponentConfig, @Composable (NavController, List<String>) -> Unit> = ConcurrentHashMap()


    /**
     * Registers a dynamic feature's configuration and its associated screen Composable.
     *
     * This method allows dynamic feature modules to add their configuration and UI screen
     * to the registry, typically called during module initialization.
     *
     * @param config The [DFComponentConfig] defining the dynamic feature's configuration.
     * @param screen The Composable function that renders the feature's UI, accepting a
     *               [NavController] for navigation purposes.
     */
    fun put(config: DFComponentConfig, screen: @Composable (NavController,List<String>) -> Unit) {
        registry[config] = screen
    }

    /**
     * Retrieves the screen Composable associated with a given configuration.
     *
     * This method is used to fetch the UI screen for a dynamic feature when it needs to
     * be displayed, such as during navigation or rendering.
     *
     * @param config The [DFComponentConfig] for which to retrieve the screen.
     * @return The Composable function representing the screen, or null if no screen is
     *         registered for the given configuration.
     */
    fun get(config: DFComponentConfig): (@Composable (NavController, List<String>) -> Unit)? {
        return registry[config]
    }

    /**
     * Removes a configuration and its associated screen from the registry.
     *
     * This method can be used to deregister a dynamic feature, such as when it is
     * uninstalled or no longer needed, though such use cases may be uncommon.
     *
     * @param config The [DFComponentConfig] to remove from the registry.
     * @return True if the configuration was successfully removed, false if it was not
     *         found in the registry.
     */
    fun remove(config: DFComponentConfig): Boolean {
        return registry.remove(config) != null
    }

    /**
     * Checks if a specific configuration is registered in the registry.
     *
     * This method helps verify whether a dynamic feature has been registered before
     * attempting to access its screen or configuration.
     *
     * @param config The [DFComponentConfig] to check for presence in the registry.
     * @return True if the configuration is registered, false otherwise.
     */
    fun contains(config: DFComponentConfig): Boolean {
        return registry.containsKey(config)
    }

    /**
     * Finds the configuration associated with a given route string.
     *
     * This method is useful for navigation scenarios, such as mapping a route from a
     * deep link or navigation graph to the corresponding dynamic feature configuration.
     *
     * @param route The route string to search for (e.g., "feature_plants").
     * @return The [DFComponentConfig] associated with the route, or null if no matching
     *         configuration is found.
     */
    fun getConfigByRoute(route: String): DFComponentConfig? {
        return registry.keys.find { it.route == route }
    }
}