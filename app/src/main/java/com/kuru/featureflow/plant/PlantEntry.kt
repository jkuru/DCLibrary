package com.kuru.featureflow.plant

import android.content.Context
import com.kuru.featureflow.component.register.DFComponentConfig
import com.kuru.featureflow.component.register.DFComponentEntry
import com.kuru.featureflow.component.register.DFComponentInterceptor
import com.kuru.featureflow.component.register.DFComponentRegistry
import com.kuru.featureflow.component.register.DFRegistryEntryPoint
import dagger.hilt.android.EntryPointAccessors


object PlantEntry : DFComponentEntry { // Implement the SPI

    override fun initialize(context: Context) { // Method from DFComponentEntry interface
        // 1. Get the Entry Point accessor
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DFRegistryEntryPoint::class.java // Use the specific Entry Point interface
        )

        // 2. Get the DFComponentRegistry via the Entry Point
        val registry: DFComponentRegistry = entryPoint.getComponentRegistry()

        // 3. Now use the registry
        val config = plantConfig() // Your feature-specific config
        registry.register(config) { navController ->
            PlantsScreen(navController) // Your feature's screen
        }
        println("Plant feature initialized and registered!")
    }

    // Helper function to create config
    private fun plantConfig(): DFComponentConfig {
        return DFComponentConfig(
            route = "plants",
            listOfDFComponentInterceptor = listOf(
                // Pre-install interceptor: Check network
                DFComponentInterceptor(preInstall = true) {
                    val isNetworkAvailable = true // Hypothetical function
                    if (!isNetworkAvailable) println("No network for plants feature")
                    isNetworkAvailable
                },
                // Post-install interceptor
                DFComponentInterceptor(preInstall = false) {
                    try {
                        // Analytics
                        println("Analytics for plants feature")
                        true
                    } catch (e: Exception) {
                        println("Failed  $e")
                        false
                    }
                }
            )
        )
    }
}