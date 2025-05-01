package com.kuru.featureflow.component.state

/**
 * Represents the possible states of an interceptor in the dynamic feature framework.
 *
 * This sealed class defines the various states that an interceptor can be in during
 * the execution of tasks related to the installation or management of dynamic feature
 * modules. Interceptors are used to perform specific operations either before or after
 * the installation of a feature module, and this class helps track their lifecycle.
 *
 * ### States
 * - **Active**: The interceptor is currently running its task.
 * - **Inactive**: The interceptor is not currently active or has not been started.
 * - **Completed**: The interceptor has successfully completed its task.
 * - **Failed**: The interceptor has failed to complete its task, with an associated
 *   error message.
 *
 * ### Usage
 * This class is used within the framework to manage and observe the state of interceptors.
 * It allows the framework to:
 * - Track the progress of interceptors.
 * - Handle different outcomes of interceptor tasks.
 * - Provide feedback or take actions based on the state of interceptors.
 *
 * ### Example
 * ```kotlin
 * when (interceptorState) {
 *     is DFInterceptorState.Active -> Log.d("Interceptor", "Interceptor is active")
 *     is DFInterceptorState.Inactive -> Log.d("Interceptor", "Interceptor is inactive")
 *     is DFInterceptorState.Completed -> Log.d("Interceptor", "Interceptor completed successfully")
 *     is DFInterceptorState.Failed -> Log.e("Interceptor", "Interceptor failed: ${interceptorState.message}")
 * }
 * ```
 */
sealed class DFInterceptorState {
    /**
     * The interceptor is currently executing its task.
     */
    data object Active : DFInterceptorState()

    /**
     * The interceptor is not active and has not been started.
     */
    data object Inactive : DFInterceptorState()

    /**
     * The interceptor has successfully completed its task.
     */
    data object Completed : DFInterceptorState()

    /**
     * The interceptor has failed to complete its task.
     *
     * @param message A string describing the reason for the failure.
     */
    data class Failed(val message: String) : DFInterceptorState()
}