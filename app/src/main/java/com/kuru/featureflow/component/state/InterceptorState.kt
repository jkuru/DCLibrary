package com.kuru.featureflow.component.state

sealed class InterceptorState {
    data object Active : InterceptorState()
    data object Inactive : InterceptorState()
    data object Completed : InterceptorState()
    data class Failed(val message: String) : InterceptorState()
}