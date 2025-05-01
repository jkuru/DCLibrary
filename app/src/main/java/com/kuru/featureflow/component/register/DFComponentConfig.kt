package com.kuru.featureflow.component.register

/**
 * This will be leveraged by the feature teams in their projects
 *  Post initialization intercepts
 *
 */
data class DFComponentConfig(
    val route: String,  //route will be module name
    val listOfDFComponentInterceptor: List<DFComponentInterceptor> = emptyList()
)
