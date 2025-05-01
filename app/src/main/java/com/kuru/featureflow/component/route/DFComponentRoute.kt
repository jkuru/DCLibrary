package com.kuru.featureflow.component.route

/**
 * Data class representing a parsed route from a URI for dynamic feature navigation.
 * Used by [DFComponentUriRouteParser] to encapsulate route information and by
 * [DFComponentActivity] to determine navigation or feature loading actions.
 *
 * Supported URI examples:
 * - "chase/df/route/myfeature?param1=value1" → route = "myfeature", params = ["param1=value1"]
 * - "chase/df/navigation/key/myactivity" → navigationKey = "myactivity"
 */
data class DFComponentRoute(
    /** The full URI path (e.g., "/chase/df/route/myfeature"). Retained for logging/debugging. */
    val path: String,
    /** The route name extracted from the URI (e.g., "myfeature"). Used for feature loading. */
    val route: String,
    /** The navigation key extracted from the URI (e.g., "myactivity"). Used for navigation. */
    val navigationKey: String,
    /**
     * List of query parameters from the URI (e.g., ["param1=value1"]).
     * Currently unused in the provided code but retained for potential future use.
     */
    val params: List<String> = emptyList(),
    /** Status of the parsing process ("success" or "failed"). Used to validate parsing outcome. */
    val status: String // status of the route processing
)