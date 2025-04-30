package com.kuru.featureflow.component.register

/**
 * This will be leveraged by the feature teams in their projects
 *  Post initialization intercepts
 *  Define the module name
 *
 */
data class DFComponentConfig(
    val route: String,  //route will be module name
    val listOfDFComponentInterceptor: List<DFComponentInterceptor> = emptyList()
)
