package com.kuru.featureflow.component.register



import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Here’s the sequence breakdown:
 *
 * Application Running: Your base application module is already running. It has access to its own ApplicationContext.
 * Module Installed: The dynamic feature module ("plants") gets downloaded and installed. Its classes, including PlantEntry and the META-INF/services/ file, are now part of the application's classpath.
 * ServiceLoader Called (in Base Module): Your framework code, likely running in the base module (e.g., inside DFComponentViewModel.runServiceLoaderInitialization), calls ServiceLoader.load(). Crucially, the Context passed to ServiceLoader.load(..., context.classLoader) is typically the readily available ApplicationContext from the base module.
 * Instance Created (by ServiceLoader): ServiceLoader scans the classpath (using the provided classloader which now includes the dynamic module), finds the META-INF/services/ file, reads the class name (com.kuru.featureflow.plant.PlantEntry), and uses reflection to load and instantiate PlantEntry. At this point, the PlantEntry object exists.
 * initialize() Called (in Base Module): The framework code then iterates through the instances provided by ServiceLoader and calls entry.initialize().
 */

@EntryPoint
@InstallIn(SingletonComponent::class) // Since DFComponentRegistry is bound as Singleton
interface DFRegistryEntryPoint {
    // Function to get the DFComponentRegistry from Hilt
    fun getComponentRegistry(): DFComponentRegistry
}