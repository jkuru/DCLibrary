package com.kuru.featureflow.component.state

import kotlinx.coroutines.flow.StateFlow

// Interface remains the same for now, but could be updated
// if we want Flow-based getters for non-installation states too.
interface DFComponentStateStore {
    // --- Persistence ---
    suspend fun getLastAttemptedFeature(): String?
    suspend fun setLastAttemptedFeature(uri: String)

    // --- In-Memory State Management (Could also be persisted if needed) ---
    fun getInstallationState(feature: String): DFInstallationState // Immediate value
    fun setInstallationState(feature: String, state: DFInstallationState) // Update in-memory
    fun getInstallationStateFlow(feature: String): StateFlow<DFInstallationState> // Observe in-memory

    // Interceptor states (kept in-memory for this example)
    fun getInterceptorState(interceptorId: String): DFInterceptorState
    fun setInterceptorState(interceptorId: String, state: DFInterceptorState)
}