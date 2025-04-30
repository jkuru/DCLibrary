package com.kuru.featureflow.component.register


import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComponentRegistryData @Inject constructor() {
    private val registry: MutableMap<DFComponentConfig, @Composable (NavController) -> Unit> = mutableMapOf()

    fun put(config: DFComponentConfig, screen: @Composable (NavController) -> Unit) {
        registry[config] = screen
    }

    fun get(config: DFComponentConfig): (@Composable (NavController) -> Unit)? {
        return registry[config]
    }

    fun remove(config: DFComponentConfig): Boolean {
        return registry.remove(config) != null
    }

    fun contains(config: DFComponentConfig): Boolean {
        return registry.containsKey(config)
    }

    fun getConfigByRoute(route: String): DFComponentConfig? {
        return registry.keys.find { it.route == route }
    }
}
