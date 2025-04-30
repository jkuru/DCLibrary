package com.kuru.featureflow.component.route

/**
 * Deep Link flow, we will limit the impact to Headless Component Install Compose function.
 * Plan is to have a generic route chase/df/ to support multiple flavours
 * "chase/df/route/ data as parameter"  --> Jetpack Navigation
 * "chase/df/navigation/key/ data as parameter"  --> Activity
 */
data class DFComponentRoute(
    val path: String,
    val route: String,  // e.g., navigation/Jetpack Nav route of dynamic component
    val navigationKey: String, // e.g., navigation key of dynamic component so we can raise a pending intent to navigate to dynamic component
    val params: List<String> = emptyList(), // data
    val status: String // status of the route processing
)