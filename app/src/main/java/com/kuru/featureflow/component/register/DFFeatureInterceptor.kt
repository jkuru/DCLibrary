package com.kuru.featureflow.component.register

/**
 * Represents an interceptor used in the dynamic feature framework to perform tasks
 * either before or after the installation of a dynamic feature module.
 *
 * This data class defines an interceptor that can be executed at specific points in
 * the installation process of a dynamic feature module. Interceptors are used to
 * perform checks, validations, or other operations that need to happen either before
 * the installation (pre-install) or after the installation (post-install).
 *
 * ### Properties
 * - **preInstall**: A boolean flag indicating whether the interceptor should be
 *   executed before the installation (`true`) or after the installation (`false`).
 * - **task**: A lambda function that defines the operation to be performed by the
 *   interceptor. This function returns a boolean indicating success (`true`) or
 *   failure (`false`).
 *
 * ### Usage
 * Interceptors are typically included in the configuration of a dynamic feature
 * module and are executed by the framework at the appropriate time during the
 * installation process.
 *
 * - **Pre-install interceptors** might perform tasks such as checking network
 *   availability, verifying user permissions, or ensuring sufficient storage space.
 * - **Post-install interceptors** might handle tasks like initializing data, logging
 *   analytics events, or setting up UI components.
 *
 * ### Example
 * ```kotlin
 * val networkCheckInterceptor = DFComponentInterceptor(preInstall = true) {
 *     val isNetworkAvailable = checkNetwork() // Hypothetical function
 *     if (!isNetworkAvailable) {
 *         Log.e("Interceptor", "No network available for feature installation")
 *     }
 *     isNetworkAvailable
 * }
 * ```
 */

/**
 * Type alias for a task executed by an interceptor.
 * Returns true for success, false for failure.
 */
typealias InterceptorTask = () -> Boolean

/**
 * Defines an interceptor for tasks before or after dynamic feature installation.
 * @param preInstall True if the task runs before installation, false if after.
 * @param task The task to execute, returning true for success, false for failure.
 */
data class DFFeatureInterceptor(
    val preInstall: Boolean,
    val task: InterceptorTask
)