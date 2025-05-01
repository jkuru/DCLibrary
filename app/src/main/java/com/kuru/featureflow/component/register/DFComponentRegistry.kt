package com.kuru.featureflow.component.register

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

/**
 * Interface for managing the registration and retrieval of dynamic feature configurations
 * and their associated UI screens in an Android application.
 *
 * This interface defines the contract for a registry that handles dynamic feature modules.
 * It acts as a central hub in the dynamic feature framework, allowing feature modules to
 * register their configurations and UI screens, and enabling the base application to
 * retrieve and render them as needed.
 */
interface DFComponentRegistry {

    /**
     * Registers a dynamic feature's configuration and its associated screen Composable.
     *
     * This function is invoked by the dynamic feature module, typically through an
     * implementation of [DFComponentEntry] loaded via [ServiceLoader]. It enables the
     * module to integrate with the base application by registering its configuration
     * and UI screen.
     *
     * @param dfComponentConfig The [DFComponentConfig] containing the feature's configuration details.
     * @param screen A Composable function that renders the feature's UI, taking a
     *               [NavController] for navigation purposes.
     */
    fun register(dfComponentConfig: DFComponentConfig, screen: @Composable (NavController, List<String>) -> Unit)

    /**
     * Retrieves the screen Composable associated with a given configuration.
     *
     * This function is called by the base application (e.g., from a ViewModel) to obtain
     * the UI screen for a dynamic feature when it needs to be displayed, such as during
     * navigation or rendering.
     *
     * @param dfComponentConfig The [DFComponentConfig] for which to retrieve the screen.
     * @return The Composable function representing the screen, or null if no screen is
     *         registered for the given configuration.
     */
    fun getScreen(dfComponentConfig: DFComponentConfig): (@Composable (NavController, List<String>) -> Unit)?

    /**
     * Unregisters a dynamic feature's configuration from the registry.
     *
     * This function provides a mechanism to remove a feature's configuration and screen
     * from the registry. It is marked as TODO, indicating that its implementation is
     * optional and depends on whether the application requires dynamic unregistration
     * (e.g., for feature uninstallation).
     *
     * @param dfComponentConfig The [DFComponentConfig] to remove from the registry.
     * @return True if the configuration was successfully removed, false if it was not found.
     */
    fun unregister(dfComponentConfig: DFComponentConfig): Boolean

    /**
     * Checks if a given configuration is valid for registration.
     *
     * This function is intended to validate a configuration before or after registration,
     * ensuring it meets specific criteria (e.g., unique routes or required fields). It is
     * marked as TODO, suggesting that its implementation is optional and may be added
     * later if validation logic is needed.
     *
     * @param dfComponentConfig The [DFComponentConfig] to validate.
     * @return True if the configuration is valid, false otherwise.
     */
    fun isRegistrationValid(dfComponentConfig: DFComponentConfig): Boolean

    /**
     * Retrieves the configuration associated with a given route string.
     *
     * This function supports navigation by allowing the base application to find a
     * dynamic feature's configuration based on its route (e.g., module name). It is
     * useful for scenarios like deep linking or navigation graph integration.
     *
     * @param route The route string to search for (e.g., "feature_plants").
     * @return The [DFComponentConfig] associated with the route, or null if no matching
     *         configuration is found.
     */
    fun getConfig(route: String): DFComponentConfig?
}