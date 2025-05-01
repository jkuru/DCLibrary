package com.kuru.featureflow.component.register

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.kuru.featureflow.component.googleplay.DFComponentInstallerManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DFComponentRegistryManager @Inject constructor(
    private val registryData: ComponentRegistryData,
    private val services: MutableMap<Class<*>, Any>
) : DFComponentRegistry {

    init {
        Log.e(TAG, "DFComponentRegistryManager Init")
    }
    companion object {
        private const val TAG = "DFRegistryManager"
    }

    override fun register(dfComponentConfig: DFComponentConfig, screen: @Composable (NavController) -> Unit) {
        registryData.put(dfComponentConfig, screen)
        Log.d(TAG, "Registered screen for route: ${dfComponentConfig.route}")
    }

    override fun getScreen(dfComponentConfig: DFComponentConfig): (@Composable (NavController) -> Unit)? {
        return registryData.get(dfComponentConfig)
    }

    override fun unregister(dfComponentConfig: DFComponentConfig): Boolean {
        val removed = registryData.remove(dfComponentConfig)
        if (removed) {
            Log.d(TAG, "Unregistered screen for route: ${dfComponentConfig.route}")
        }
        return removed
    }

    override fun isRegistrationValid(dfComponentConfig: DFComponentConfig): Boolean {
        return registryData.contains(dfComponentConfig)
    }

    override fun getConfig(route: String): DFComponentConfig? {
        return registryData.getConfigByRoute(route)
    }
}