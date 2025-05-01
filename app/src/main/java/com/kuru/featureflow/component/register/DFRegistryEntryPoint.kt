package com.kuru.featureflow.component.register

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent


/**
 * Entry point for accessing the [DFComponentRegistry] from dynamic feature modules.
 *
 * This interface provides a mechanism for dynamic feature modules (e.g., the "plants" module)
 * to retrieve the [DFComponentRegistry] instance, which is managed by Hilt in the base application
 * module. Dynamic feature modules are loaded at runtime and do not have direct access to Hilt's
 * dependency injection graph. This entry point serves as a bridge, allowing these modules to
 * interact with the singleton registry.
 *
 * ### Purpose
 * The [DFComponentRegistry] is a centralized singleton that manages configurations for dynamic
 * features in the app. Since dynamic modules cannot directly inject dependencies via Hilt due
 * to their runtime installation, this entry point enables them to access the registry using
 * the application context.
 *
 * ### How It Accesses DFComponentRegistry
 * - **Hilt Management**: The [DFComponentRegistry] is bound as a singleton in the base module
 *   using Hilt's dependency injection, installed in the [SingletonComponent].
 * - **Application Context**: Dynamic modules, such as "plants", receive the application context
 *   (typically passed via [DFComponentEntry.initialize]). This context is used to access Hilt's
 *   entry points.
 * - **EntryPointAccessors**: The dynamic module calls [EntryPointAccessors.fromApplication] with
 *   the application context and this interface ([DFRegistryEntryPoint]) to retrieve an instance
 *   of the entry point.
 * - **Registry Retrieval**: The [getComponentRegistry] function, implemented by Hilt, returns
 *   the singleton [DFComponentRegistry] instance from the base module's dependency graph.
 *
 * ### Usage in Dynamic Modules
 * For example, in the "plants" module's [PlantEntry] class:
 * 1. The [initialize] method receives the application context.
 * 2. It uses [EntryPointAccessors.fromApplication] to get this entry point.
 * 3. It calls [getComponentRegistry] to obtain the [DFComponentRegistry].
 * 4. The module then registers its configuration with the registry.
 *
 * ### Why SingletonComponent?
 * The [DFComponentRegistry] is a singleton to ensure a single, consistent registry across the
 * app. By installing this entry point in [SingletonComponent], it aligns with the registry's
 * lifecycle and scope, making it accessible throughout the app's lifetime.
 */
@EntryPoint
@InstallIn(SingletonComponent::class) // Aligns with DFComponentRegistry's singleton scope
interface DFRegistryEntryPoint {
    /**
     * Retrieves the singleton instance of [DFComponentRegistry] managed by Hilt.
     *
     * This function is automatically implemented by Hilt and provides access to the
     * [DFComponentRegistry] instance that is part of the base module's dependency graph.
     * Dynamic feature modules call this method via the entry point to register their
     * configurations or interact with the registry.
     *
     * @return The [DFComponentRegistry] singleton instance.
     */
    fun getComponentRegistry(): DFComponentRegistry
}