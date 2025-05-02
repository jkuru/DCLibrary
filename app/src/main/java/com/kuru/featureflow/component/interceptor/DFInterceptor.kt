package com.kuru.featureflow.component.interceptor

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

interface DFInterceptor {

    suspend fun runPreInstallInterceptors(feature: String): Boolean
    suspend fun runPostInstallInterceptors(feature: String) : Boolean
    fun fetchAndSetDynamicScreen(feature: String) : (@Composable (NavController, List<String>) -> Unit)?
}