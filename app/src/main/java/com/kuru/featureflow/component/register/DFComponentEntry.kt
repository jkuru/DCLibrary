package com.kuru.featureflow.component.register

import android.content.Context

/**
 * This will be implemented by the dynamic feature, this is entry point for dynamic feature
 * the link using service loaders
 * It will register DFComponentRegistry to DFComponentRegistryManager
 * The Post Interceptors will be executed in framework
 */
interface DFComponentEntry {
    /**
     * Initializes the dynamic feature module with the application context.
     *
     * This method is the key entry point for a dynamic feature module after it’s installed.
     * It’s called by the framework (via ServiceLoader) to allow the module to:
     * - Perform setup tasks using the provided application context.
     * - Register its configuration with the DFComponentRegistryManager.
     * - Enable post-installation logic (like interceptors) to be executed by the framework.
     *
     * @param context The application context from the base module, used for initialization.
     */
    fun initialize(context: Context)
}