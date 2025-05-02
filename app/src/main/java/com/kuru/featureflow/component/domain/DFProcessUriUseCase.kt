package com.kuru.featureflow.component.domain

import android.util.Log
import com.kuru.featureflow.component.route.DFComponentUriRouteParser
import javax.inject.Inject

/**
 * Represents the interpreted outcome of processing a URI string.
 * It clarifies the intent derived from the URI structure.
 */
sealed class DFProcessUriResult {
    /**
     * Indicates the URI successfully resolved to a dynamic feature route.
     * @param name The extracted feature name (route).
     * @param params The list of query parameters from the URI.
     */
    data class FeatureRoute(val name: String, val params: List<String>) : DFProcessUriResult()

    /**
     * Indicates the URI successfully resolved to a navigation key.
     * Note: Further handling for navigation keys might be needed elsewhere.
     * @param key The extracted navigation key.
     * @param params The list of query parameters from the URI.
     */
    data class NavigationRoute(val key: String, val params: List<String>) : DFProcessUriResult()

    /**
     * Indicates the URI was invalid, could not be parsed, or did not match
     * any expected patterns.
     * @param reason A message explaining why the URI is considered invalid.
     */
    data class InvalidUri(val reason: String) : DFProcessUriResult()
}


/**
 * Use case responsible for parsing a URI string and determining the intended
 * action based on the framework's routing rules (e.g., loading a feature
 * module or navigating via a key).
 */
class DFProcessUriUseCase @Inject constructor(
    private val componentUriRouteParser: DFComponentUriRouteParser
) {
    companion object {
        private const val TAG = "ProcessUriUseCase"
    }

    /**
     * Parses the given URI string and returns the interpreted result.
     *
     * @param uri The raw URI string to process. Can be null.
     * @return A [DFProcessUriResult] indicating whether it's a feature route,
     * navigation route, or invalid.
     */
    operator fun invoke(uri: String?): DFProcessUriResult {
        Log.d(TAG, "Processing URI: $uri")

        // Use the injected parser to extract route information
        // The parser already handles null/blank checks and parsing errors internally.
        val dynamicRoute = componentUriRouteParser.extractRoute(uri)

        // Check the status determined by the parser
        if (dynamicRoute.status != "success") {
            Log.w(TAG, "URI parsing failed or resulted in non-success status for URI: $uri")
            return DFProcessUriResult.InvalidUri(
                "URI parsing failed or did not produce a successful route structure."
            )
        }

        // If successful, determine the type based on extracted values
        return when {
            // Prioritize feature route if 'route' is present
            dynamicRoute.route.isNotEmpty() -> {
                Log.i(TAG, "URI interpreted as FeatureRoute: ${dynamicRoute.route}")
                DFProcessUriResult.FeatureRoute(
                    name = dynamicRoute.route,
                    params = dynamicRoute.params
                )
            }
            // Check for navigation key if 'route' is empty
            dynamicRoute.navigationKey.isNotEmpty() -> {
                Log.i(TAG, "URI interpreted as NavigationRoute: ${dynamicRoute.navigationKey}")
                DFProcessUriResult.NavigationRoute(
                    key = dynamicRoute.navigationKey,
                    params = dynamicRoute.params
                )
            }
            // If status is "success" but neither route nor key is present, treat as invalid structure
            else -> {
                Log.w(TAG, "URI parsing succeeded but resulted in empty route and navigation key for URI: $uri")
                DFProcessUriResult.InvalidUri(
                    "Parsed route structure is incomplete (missing route or navigation key)."
                )
            }
        }
    }
}