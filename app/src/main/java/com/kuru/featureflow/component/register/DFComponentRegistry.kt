package com.kuru.featureflow.component.register

import androidx.compose.runtime.Composable
import androidx.navigation.NavController


interface DFComponentRegistry {
    // This function will be invoked by Feature module (via ServiceLoader -> DFComponentEntry)
    fun register(dfComponentConfig: DFComponentConfig, screen: @Composable (NavController) -> Unit)

    // This function will be invoked by App module (e.g., ViewModel)
    fun getScreen(dfComponentConfig: DFComponentConfig): (@Composable (NavController) -> Unit)?

    // TODO: Implement unregister logic if needed
    fun unregister(dfComponentConfig: DFComponentConfig): Boolean

    // TODO: Implement validation logic if needed
    fun isRegistrationValid(dfComponentConfig: DFComponentConfig): Boolean

    // Get config by route name (module name)
    fun getConfig(route: String): DFComponentConfig?

}
