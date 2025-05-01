package com.kuru.featureflow.component.route

import android.net.Uri
import android.util.Log
import com.kuru.featureflow.component.register.DFComponentRegistryManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for parsing URIs into DFComponentRoute objects.
 * Provides a static method to extract route information from a given URI.
 */
@Singleton
class DFComponentUriRouteParser @Inject constructor() {

    init {
        Log.e(TAG, "DFComponentUriRouteParser Init")
    }
    companion object {
        private const val  TAG = "DFUriParser"
        private const val BASE_PATH_PREFIX = "/chase/df/"
        private const val ROUTE_SEGMENT = "route"
        private const val NAVIGATION_SEGMENT = "navigation"
        private const val KEY_SEGMENT = "key"
        private const val STATUS_SUCCESS = "success"
        private const val STATUS_FAILED = "failed"
    }

    /**
     * Extracts the DFComponentRoute from the provided raw URI string.
     *
     * @param rawURI The URI string to parse. Can be null or empty.
     * @return A DFComponentRoute object. Status will be "failed" if parsing fails or URI is invalid.
     */
    fun extractRoute(rawURI: String?): DFComponentRoute {
        if (rawURI.isNullOrBlank()) {
            Log.e(TAG, "Input URI is null or blank.")
            return createFailedRoute("Input URI is null or blank")
        }

        val uri: Uri = try {
            Uri.parse(rawURI)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse URI: $rawURI", e)
            return createFailedRoute("URI parsing failed: ${e.message}")
        }

        val path = uri.path
        if (path == null || !path.startsWith(BASE_PATH_PREFIX)) {
            Log.e(TAG, "URI path is null or does not start with $BASE_PATH_PREFIX. Path: $path")
            return createFailedRoute("Invalid path prefix")
        }

        // Use safe access for path segments
        val pathSegments = uri.pathSegments ?: emptyList()
        if (pathSegments.isEmpty()) {
            Log.e(TAG, "URI path has no segments after prefix. Path: $path")
            // Decide if this is an error or a default case
            return createFailedRoute("No path segments found")
        }


        // Extract parameters from query safely
        val params = mutableListOf<String>()
        try { // Catch potential exceptions during query parsing
            uri.queryParameterNames?.forEach { paramName ->
                uri.getQueryParameter(paramName)?.let { paramValue ->
                    params.add("$paramName=$paramValue")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing query parameters for URI: $rawURI", e)
            // Continue without query params or mark as failed? Decide based on requirements.
        }

        // Determine route type based on segments after "/chase/df/"
        // Example: /chase/df/route/myfeature -> segments = [route, myfeature]
        // Example: /chase/df/navigation/key/myactivity -> segments = [navigation, key, myactivity]
        val relevantSegments = pathSegments.drop(2) // Skip "chase", "df"

        return when {
            relevantSegments.firstOrNull() == ROUTE_SEGMENT && relevantSegments.size > 1 -> {
                // Route: /chase/df/route/{routeName}
                val routeValue = relevantSegments.getOrNull(1) ?: ""
                if (routeValue.isEmpty()) {
                    createFailedRoute("Route name missing after '/route/' segment")
                } else {
                    DFComponentRoute(
                        path = path,
                        route = routeValue,
                        navigationKey = "",
                        params = params,
                        status = STATUS_SUCCESS
                    )
                }
            }
            relevantSegments.firstOrNull() == NAVIGATION_SEGMENT && relevantSegments.getOrNull(1) == KEY_SEGMENT && relevantSegments.size > 2 -> {
                // Navigation Key: /chase/df/navigation/key/{keyName}
                val keyValue = relevantSegments.getOrNull(2) ?: ""
                if (keyValue.isEmpty()) {
                    createFailedRoute("Navigation key missing after '/navigation/key/' segment")
                } else {
                    DFComponentRoute(
                        path = path,
                        route = "",
                        navigationKey = keyValue,
                        params = params,
                        status = STATUS_SUCCESS
                    )
                }
            }
            relevantSegments.isNotEmpty() -> {
                // Fallback: Treat the first segment after /chase/df/ as the route name
                // Example: /chase/df/myfeature
                val routeValue = relevantSegments.first()
                DFComponentRoute(
                    path = path,
                    route = routeValue,
                    navigationKey = "",
                    params = params,
                    status = STATUS_SUCCESS
                )
            }
            else -> {
                Log.e(TAG, "URI path structure not recognized after prefix. Path: $path")
                createFailedRoute("Unrecognized path structure")
            }
        }
    }

    private fun createFailedRoute(logMessage: String): DFComponentRoute {
        Log.e(TAG, "Route extraction failed: $logMessage")
        return DFComponentRoute(
            path = "",
            route = "",
            navigationKey = "",
            params = emptyList(),
            status = STATUS_FAILED
        )
    }
}
