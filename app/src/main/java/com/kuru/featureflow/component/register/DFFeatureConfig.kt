package com.kuru.featureflow.component.register

/**
 * Configuration for a dynamic feature module, specifying its route and interceptors.
 * The route identifies the module (e.g., "feature_plants"), and interceptors define
 * pre- or post-installation tasks executed by the framework.
 */
data class DFFeatureConfig(
    val route: String,
    val listOfDFFeatureInterceptor: List<DFFeatureInterceptor> = emptyList()
) {
    companion object {
        /**
         * Creates a configuration with pre- and post-install tasks.
         * @param route The module's route (e.g., "feature_plants").
         * @param preInstallTasks Tasks to run before installation.
         * @param postInstallTasks Tasks to run after installation.
         * @return A configured DFComponentConfig instance.
         */
        fun create(
            route: String,
            preInstallTasks: List<InterceptorTask> = emptyList(),
            postInstallTasks: List<InterceptorTask> = emptyList()
        ): DFFeatureConfig {
            val interceptors = preInstallTasks.map { DFFeatureInterceptor(true, it) } +
                    postInstallTasks.map { DFFeatureInterceptor(false, it) }
            return DFFeatureConfig(route, interceptors)
        }

        /**
         * Creates a configuration with explicitly defined interceptors.
         * @param route The module's route (e.g., "feature_plants").
         * @param interceptors Variable number of interceptors.
         * @return A configured DFComponentConfig instance.
         */
        fun create(
            route: String,
            vararg interceptors: DFFeatureInterceptor
        ): DFFeatureConfig {
            return DFFeatureConfig(route, interceptors.toList())
        }
    }
}