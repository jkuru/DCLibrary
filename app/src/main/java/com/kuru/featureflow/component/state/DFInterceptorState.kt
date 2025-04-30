package com.kuru.featureflow.component.state

sealed class DFInterceptorState {
    data object Active : DFInterceptorState()
    data object Inactive : DFInterceptorState()
    data object Completed : DFInterceptorState()
    data class Failed(val message: String) : DFInterceptorState()
}