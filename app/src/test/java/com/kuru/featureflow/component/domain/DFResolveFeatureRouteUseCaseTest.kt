package com.kuru.featureflow.component.domain // Ensure package matches your project

import android.net.Uri // Import Android Uri
import android.util.Log
// import androidx.core.net.toUri // Not strictly needed if mocking Uri.parse
import com.kuru.featureflow.component.state.DFFeatureRoute // Assuming this is in the right package
import io.mockk.MockKAnnotations
import io.mockk.core.ValueClassSupport.boxedValue
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs // Can be used if constructor had dependencies
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After // JUnit 4
import org.junit.Before // JUnit 4
import org.junit.Test // JUnit 4
import kotlin.test.Ignore
import kotlin.test.assertEquals // kotlin.test assertions
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNull // Include if needed

class DFResolveFeatureRouteUseCaseTest {

    // No dependencies injected in constructor, so instantiate directly
    private lateinit var useCase: DFResolveFeatureRouteUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = DFResolveFeatureRouteUseCase()

        // Mock static methods for Log and Uri
        mockkStatic(Log::class)
        mockkStatic(Uri::class)

        // Provide default return values for Log to avoid failures
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        // Unmock static methods
        unmockkStatic(Log::class)
        unmockkStatic(Uri::class)
    }

    // Helper to create a mocked Uri for valid strings
    private fun setupMockUri(
        uriString: String,
        path: String?,
        pathSegments: List<String>?,
        queryParamNames: Set<String>?,
        queryParams: Map<String, String?> = emptyMap() // param name -> value
    ) {
        val mockUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(uriString) } returns mockUri
        every { mockUri.path } returns path
        every { mockUri.pathSegments } returns pathSegments
        every { mockUri.queryParameterNames } returns queryParamNames
        queryParamNames?.forEach { paramName ->
            every { mockUri.getQueryParameter(paramName) } returns queryParams[paramName]
        }
        // Optional: Mock toString if needed for logging verification within SUT
        // every { mockUri.toString() } returns uriString
    }

    // --- Test Cases ---

    @Test
    fun `invoke - given null URI - returns InvalidUri`() {
        // When
        val result = useCase(null)

        // Then
        assertIs<DFProcessUriState.InvalidUri>(result)
        assertEquals("URI parsing failed or did not produce a successful route structure.", result.reason)
        verify { Log.e(eq("DFResolveFeatureRouteUseCase"), eq("Input URI is null or blank.")) }
    }

    @Test
    fun `invoke - given blank URI - returns InvalidUri`() {
        // When
        val result = useCase("   ")

        // Then
        assertIs<DFProcessUriState.InvalidUri>(result)
        assertEquals("URI parsing failed or did not produce a successful route structure.", result.reason)
        verify { Log.e(eq("DFResolveFeatureRouteUseCase"), eq("Input URI is null or blank.")) }
    }

    @Test
    fun `invoke - given invalid URI format - returns InvalidUri`() {
        // Given
        val invalidUriString = "::not a uri::"
        val parseException = IllegalArgumentException("Cannot parse URI")
        every { Uri.parse(invalidUriString) } throws parseException

        // When
        val result = useCase(invalidUriString)

        // Then
        assertIs<DFProcessUriState.InvalidUri>(result)
        assertEquals("URI parsing failed or did not produce a successful route structure.", result.reason)
        verify { Log.e(eq("DFResolveFeatureRouteUseCase"), eq("Failed to parse URI: $invalidUriString"), eq(parseException)) }
    }

    @Test
    fun `invoke - given path is null - returns InvalidUri`() {
        // Given
        val uriString = "app:/chase/df/route/feature1"
        setupMockUri(uriString, path = null, pathSegments = null, queryParamNames = null)

        // When
        val result = useCase(uriString)

        // Then
        assertIs<DFProcessUriState.InvalidUri>(result)
        assertEquals("URI parsing failed or did not produce a successful route structure.", result.reason)
        verify { Log.e(eq("DFResolveFeatureRouteUseCase"), eq("URI path is null or does not start with /chase/df/. Path: null")) }
    }

    @Test
    fun `invoke - given path has incorrect prefix - returns InvalidUri`() {
        // Given
        val uriString = "http://example.com/wrong/prefix/route/feature1"
        val path = "/wrong/prefix/route/feature1"
        setupMockUri(uriString, path = path, pathSegments = listOf("wrong", "prefix", "route", "feature1"), queryParamNames = null)

        // When
        val result = useCase(uriString)

        // Then
        assertIs<DFProcessUriState.InvalidUri>(result)
        assertEquals("URI parsing failed or did not produce a successful route structure.", result.reason)
        verify { Log.e(eq("DFResolveFeatureRouteUseCase"), eq("URI path is null or does not start with /chase/df/. Path: $path")) }
    }

    @Test
    fun `invoke - given path has no segments after prefix - returns InvalidUri`() {
        // Given
        val uriString = "app://host/chase/df/" // URI ending with prefix
        val path = "/chase/df/"
        // Mocking Uri.parse to return a Uri object that behaves as expected for this input
        // Common behavior: ["chase", "df"] segments, leading to empty relevantSegments
        setupMockUri(uriString, path = path, pathSegments = listOf("chase", "df"), queryParamNames = null)

        // When
        val result = useCase(uriString)

        // Then
        assertIs<DFProcessUriState.InvalidUri>(result)
        assertEquals("URI parsing failed or did not produce a successful route structure.", result.reason)

        // --- Corrected Verification ---
        // Verify the log from the 'else' branch in extractRoute
        verify { Log.e(eq("DFResolveFeatureRouteUseCase"), eq("URI path structure not recognized after prefix. Path: $path")) }
        // Verify the log from createFailedRoute call triggered by the 'else' branch
        verify { Log.e(eq("DFResolveFeatureRouteUseCase"), eq("Route extraction failed: Unrecognized path structure")) }
    }


    @Test
    fun `invoke - given explicit route format - returns FeatureRoute`() {
        // Given
        val featureName = "featureABC"
        val uriString = "app://host/chase/df/route/$featureName"
        val path = "/chase/df/route/$featureName"
        setupMockUri(uriString, path = path, pathSegments = listOf("chase", "df", "route", featureName), queryParamNames = null)

        // When
        val result = useCase(uriString)

        // Then
        assertIs<DFProcessUriState.FeatureRoute>(result)
        assertEquals(featureName, result.name)
        assertTrue(result.params.isEmpty())
        verify { Log.i(eq("DFResolveFeatureRouteUseCase"), eq("URI interpreted as FeatureRoute: $featureName")) }
    }

    @Test
    fun `invoke - given explicit route format with query params - returns FeatureRoute with params`() {
        // Given
        val featureName = "featureXYZ"
        val paramKey1 = "id"
        val paramValue1 = "123"
        val paramKey2 = "mode"
        val paramValue2 = "test"
        val uriString = "app://host/chase/df/route/$featureName?$paramKey1=$paramValue1&$paramKey2=$paramValue2"
        val path = "/chase/df/route/$featureName"
        setupMockUri(
            uriString,
            path = path,
            pathSegments = listOf("chase", "df", "route", featureName),
            queryParamNames = setOf(paramKey1, paramKey2),
            queryParams = mapOf(paramKey1 to paramValue1, paramKey2 to paramValue2)
        )

        // When
        val result = useCase(uriString)

        // Then
        assertIs<DFProcessUriState.FeatureRoute>(result)
        assertEquals(featureName, result.name)
        assertEquals(2, result.params.size)
        assertTrue(result.params.contains("$paramKey1=$paramValue1"))
        assertTrue(result.params.contains("$paramKey2=$paramValue2"))
        verify { Log.i(eq("DFResolveFeatureRouteUseCase"), eq("URI interpreted as FeatureRoute: $featureName")) }
    }

    @Test
    fun `invoke - given fallback route format - returns FeatureRoute`() {
        // Given
        val featureName = "simpleFeature"
        val uriString = "app://host/chase/df/$featureName"
        val path = "/chase/df/$featureName"
        setupMockUri(uriString, path = path, pathSegments = listOf("chase", "df", featureName), queryParamNames = null)

        // When
        val result = useCase(uriString)

        // Then
        assertIs<DFProcessUriState.FeatureRoute>(result)
        assertEquals(featureName, result.name)
        assertTrue(result.params.isEmpty())
        verify { Log.i(eq("DFResolveFeatureRouteUseCase"), eq("URI interpreted as FeatureRoute: $featureName")) }
    }

    @Test
    fun `invoke - given fallback route format with query params - returns FeatureRoute with params`() {
        // Given
        val featureName = "simpleFeature"
        val paramKey = "user"
        val paramValue = "alpha"
        val uriString = "app://host/chase/df/$featureName?$paramKey=$paramValue"
        val path = "/chase/df/$featureName"
        setupMockUri(
            uriString,
            path = path,
            pathSegments = listOf("chase", "df", featureName),
            queryParamNames = setOf(paramKey),
            queryParams = mapOf(paramKey to paramValue)
        )

        // When
        val result = useCase(uriString)

        // Then
        assertIs<DFProcessUriState.FeatureRoute>(result)
        assertEquals(featureName, result.name)
        assertEquals(1, result.params.size)
        assertEquals("$paramKey=$paramValue", result.params.first())
        verify { Log.i(eq("DFResolveFeatureRouteUseCase"), eq("URI interpreted as FeatureRoute: $featureName")) }
    }

    @Test
    @Ignore("To be implemented")
    fun `invoke - given explicit route format but missing name - returns InvalidUri`() {
        // Given
        val uriString = "app://host/chase/df/route/"
        val path = "/chase/df/route/"
        setupMockUri(uriString, path = path, pathSegments = listOf("chase", "df", "route"), queryParamNames = null)

        // When
        val result = useCase(uriString)

        // Then
        assertIs<DFProcessUriState.InvalidUri>(result)
        assertEquals("URI parsing failed or did not produce a successful route structure.", result.reason)
        verify { Log.e(eq("DFResolveFeatureRouteUseCase"), eq("Route extraction failed: Route name missing after '/route/' segment")) }
    }

    @Test
    fun `invoke - given navigation key format - returns NavigationRoute`() {
        // Given
        val keyName = "activityXYZ"
        val uriString = "app://host/chase/df/navigation/key/$keyName"
        val path = "/chase/df/navigation/key/$keyName"
        setupMockUri(uriString, path = path, pathSegments = listOf("chase", "df", "navigation", "key", keyName), queryParamNames = null)

        // When
        val result = useCase(uriString)

        // Then
        assertIs<DFProcessUriState.NavigationRoute>(result)
        assertEquals(keyName, result.key)
        assertTrue(result.params.isEmpty())
        verify { Log.i(eq("DFResolveFeatureRouteUseCase"), eq("URI interpreted as NavigationRoute: $keyName")) }
    }

    @Test
    fun `invoke - given navigation key format with query params - returns NavigationRoute with params`() {
        // Given
        val keyName = "activity123"
        val paramKey = "token"
        val paramValue = "abcDEF"
        val uriString = "app://host/chase/df/navigation/key/$keyName?$paramKey=$paramValue"
        val path = "/chase/df/navigation/key/$keyName"
        setupMockUri(
            uriString,
            path = path,
            pathSegments = listOf("chase", "df", "navigation", "key", keyName),
            queryParamNames = setOf(paramKey),
            queryParams = mapOf(paramKey to paramValue)
        )

        // When
        val result = useCase(uriString)

        // Then
        assertIs<DFProcessUriState.NavigationRoute>(result)
        assertEquals(keyName, result.key)
        assertEquals(1, result.params.size)
        assertEquals("$paramKey=$paramValue", result.params.first())
        verify { Log.i(eq("DFResolveFeatureRouteUseCase"), eq("URI interpreted as NavigationRoute: $keyName")) }
    }

    @Test
    @Ignore("To be implemented")
    fun `invoke - given navigation key format but missing key - returns InvalidUri`() {
        // Given
        val uriString = "app://host/chase/df/navigation/key/"
        val path = "/chase/df/navigation/key/"
        setupMockUri(uriString, path = path, pathSegments = listOf("chase", "df", "navigation", "key"), queryParamNames = null)

        // When
        val result : DFProcessUriState = useCase(uriString)

        // Then
//        assertIs<DFProcessUriState.InvalidUri>(result)
        assertEquals("URI parsing failed or did not produce a successful route structure.", result.boxedValue)
        verify { Log.e(eq("DFResolveFeatureRouteUseCase"), eq("Route extraction failed: Navigation key missing after '/navigation/key/' segment")) }
    }

    @Test
    fun `invoke - given query param parsing throws exception - still returns FeatureRoute with empty params`() {
        // Given
        val featureName = "featureWithBadParams"
        val uriString = "app://host/chase/df/route/$featureName?bad=param"
        val path = "/chase/df/route/$featureName"
        val queryParamNames = setOf("bad")
        val exception = UnsupportedOperationException("Can't parse query")

        // Setup mock Uri, but make getQueryParameter throw
        val mockUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(uriString) } returns mockUri
        every { mockUri.path } returns path
        every { mockUri.pathSegments } returns listOf("chase", "df", "route", featureName)
        every { mockUri.queryParameterNames } returns queryParamNames
        every { mockUri.getQueryParameter("bad") } throws exception // Make param parsing fail

        // When
        val result = useCase(uriString)

        // Then
        // The use case catches the exception during param parsing but proceeds
        assertIs<DFProcessUriState.FeatureRoute>(result, "Should still resolve route even if param parsing fails")
        assertEquals(featureName, result.name)
        assertTrue(result.params.isEmpty(), "Params list should be empty due to parsing error") // Params are empty because parsing failed
        verify { Log.i(eq("DFResolveFeatureRouteUseCase"), eq("URI interpreted as FeatureRoute: $featureName")) }
        verify { Log.e(eq("DFResolveFeatureRouteUseCase"), eq("Error parsing query parameters for URI: $uriString"), eq(exception)) } // Verify error was logged
    }
}