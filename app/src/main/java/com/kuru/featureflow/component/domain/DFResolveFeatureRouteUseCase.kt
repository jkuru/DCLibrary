package com.kuru.featureflow.component.domain

import android.net.Uri
import android.util.Log
import com.kuru.featureflow.component.state.DFFeatureRoute
import javax.inject.Inject
import androidx.core.net.toUri

/**
 * Represents the interpreted outcome of processing a URI string.
 * It clarifies the intent derived from the URI structure.
 */
sealed class DFProcessUriState {
    /**
     * Indicates the URI successfully resolved to a dynamic feature route.
     * @param name The extracted feature name (route).
     * @param params The list of query parameters from the URI.
     */
    data class FeatureRoute(val name: String, val params: List<String>) : DFProcessUriState()

    /**
     * Indicates the URI successfully resolved to a navigation key.
     * Note: Further handling for navigation keys might be needed elsewhere.
     * @param key The extracted navigation key.
     * @param params The list of query parameters from the URI.
     */
    data class NavigationRoute(val key: String, val params: List<String>) : DFProcessUriState()

    /**
     * Indicates the URI was invalid, could not be parsed, or did not match
     * any expected patterns.
     * @param reason A message explaining why the URI is considered invalid.
     */
    data class InvalidUri(val reason: String) : DFProcessUriState()
}

/**
 * Use case responsible for parsing a URI string and determining the intended
 * action based on the framework's routing rules (e.g., loading a feature
 * module or navigating via a key).
 */
class DFResolveFeatureRouteUseCase @Inject constructor() {
    companion object {
        private const val TAG = "DFResolveFeatureRouteUseCase"
        private const val BASE_PATH_PREFIX = "/chase/df/"
        private const val ROUTE_SEGMENT = "route"
        private const val NAVIGATION_SEGMENT = "navigation"
        private const val KEY_SEGMENT = "key"
        private const val STATUS_SUCCESS = "success"
        private const val STATUS_FAILED = "failed"
    }

    /**
     * Parses the given URI string and returns the interpreted result.
     *
     * @param uri The raw URI string to process. Can be null.
     * @return A [DFProcessUriState] indicating whether it's a feature route,
     * navigation route, or invalid.
     */
    operator fun invoke(uri: String?): DFProcessUriState {
        Log.d(TAG, "Processing URI: $uri")

        // Extract route information using the internal parser
        val dynamicRoute = extractRoute(uri)

        // Check the status determined by the parser
        if (dynamicRoute.status != STATUS_SUCCESS) {
            Log.w(TAG, "URI parsing failed or resulted in non-success status for URI: $uri")
            return DFProcessUriState.InvalidUri(
                "URI parsing failed or did not produce a successful route structure."
            )
        }

        // If successful, determine the type based on extracted values
        return when {
            // Prioritize feature route if 'route' is present
            dynamicRoute.route.isNotEmpty() -> {
                Log.i(TAG, "URI interpreted as FeatureRoute: ${dynamicRoute.route}")
                DFProcessUriState.FeatureRoute(
                    name = dynamicRoute.route,
                    params = dynamicRoute.params
                )
            }
            // Check for navigation key if 'route' is empty
            dynamicRoute.navigationKey.isNotEmpty() -> {
                Log.i(TAG, "URI interpreted as NavigationRoute: ${dynamicRoute.navigationKey}")
                DFProcessUriState.NavigationRoute(
                    key = dynamicRoute.navigationKey,
                    params = dynamicRoute.params
                )
            }
            // If status is "success" but neither route nor key is present, treat as invalid structure
            else -> {
                Log.w(TAG, "URI parsing succeeded but resulted in empty route and navigation key for URI: $uri")
                DFProcessUriState.InvalidUri(
                    "Parsed route structure is incomplete (missing route or navigation key)."
                )
            }
        }
    }

    /**
     * Extracts the DFComponentRoute from the provided raw URI string.
     *
     * @param rawURI The URI string to parse. Can be null or empty.
     * @return A DFComponentRoute object. Status will be "failed" if parsing fails or URI is invalid.
     */
    private fun extractRoute(rawURI: String?): DFFeatureRoute {
        // Check if the input URI is null or blank
        if (rawURI.isNullOrBlank()) {
            Log.e(TAG, "Input URI is null or blank.")
            return createFailedRoute("Input URI is null or blank")
        }

        // Attempt to parse the URI string into a Uri object
        val uri: Uri = try {
            rawURI.toUri()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse URI: $rawURI", e)
            return createFailedRoute("URI parsing failed: ${e.message}")
        }

        // Validate the URI path and ensure it starts with the expected prefix
        val path = uri.path
        if (path == null || !path.startsWith(BASE_PATH_PREFIX)) {
            Log.e(TAG, "URI path is null or does not start with $BASE_PATH_PREFIX. Path: $path")
            return createFailedRoute("Invalid path prefix")
        }

        // Safely access path segments, defaulting to an empty list if null
        val pathSegments = uri.pathSegments ?: emptyList()
        if (pathSegments.isEmpty()) {
            Log.e(TAG, "URI path has no segments after prefix. Path: $path")
            return createFailedRoute("No path segments found")
        }

        // Extract query parameters safely into a list
        val params = mutableListOf<String>()
        try {
            uri.queryParameterNames?.forEach { paramName ->
                uri.getQueryParameter(paramName)?.let { paramValue ->
                    params.add("$paramName=$paramValue")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing query parameters for URI: $rawURI", e)
        }

        // Determine route type based on segments after "/chase/df/"
        val relevantSegments = pathSegments.drop(2) // Skip "chase", "df"

        return when {
            relevantSegments.firstOrNull() == ROUTE_SEGMENT && relevantSegments.size > 1 -> {
                // Route: /chase/df/route/{routeName}
                val routeValue = relevantSegments.getOrNull(1) ?: ""
                if (routeValue.isEmpty()) {
                    createFailedRoute("Route name missing after '/route/' segment")
                } else {
                    DFFeatureRoute(
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
                    DFFeatureRoute(
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
                val routeValue = relevantSegments.first()
                DFFeatureRoute(
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

    /**
     * Creates a DFComponentRoute object with a failed status.
     *
     * @param logMessage The message to log for debugging purposes.
     * @return A DFComponentRoute object with empty fields and "failed" status.
     */
    private fun createFailedRoute(logMessage: String): DFFeatureRoute {
        Log.e(TAG, "Route extraction failed: $logMessage")
        return DFFeatureRoute(
            path = "",
            route = "",
            navigationKey = "",
            params = emptyList(),
            status = STATUS_FAILED
        )
    }
}