package com.kuru.featureflow.component.route

import android.net.Uri
import android.util.Log
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.`when` // Explicit import for `when`
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class) // Optional: if you use @Mock annotations more extensively
class DFComponentUriRouteParserTest {

    private lateinit var parser: DFComponentUriRouteParser

    // @Mock // Can use @Mock with MockitoJUnitRunner
    private lateinit var mockUri: Uri

    // MockedStatic needs to be managed. Using try-with-resources in each test is safest.
    // Or, ensure they are closed in an @After method if initialized in @Before.

    @Before
    fun setUp() {
        parser = DFComponentUriRouteParser()
        mockUri = Mockito.mock(Uri::class.java) // Initialize mockUri if not using @Mock annotation
    }

    // Helper function to set up mocks for a given URI parsing scenario
    private fun setupUriParsing(
        rawUri: String,
        parsedPath: String?,
        parsedPathSegments: List<String>?,
        parsedQueryParamNames: Set<String>? = null,
        queryParamsMap: Map<String, String>? = null
    ) {
        `when`(mockUri.path).thenReturn(parsedPath)
        `when`(mockUri.pathSegments).thenReturn(parsedPathSegments)

        val queryNames = parsedQueryParamNames ?: emptySet()
        `when`(mockUri.queryParameterNames).thenReturn(queryNames)
        queryNames.forEach { name ->
            `when`(mockUri.getQueryParameter(name)).thenReturn(queryParamsMap?.get(name))
        }
        // This line should be inside the try-with-resources block in the test method
        // mockedStaticUri.`when`<Uri> { Uri.parse(rawUri) }.thenReturn(mockUri)
    }


    @Test
    fun `extractRoute should return failed for null URI`() {
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
            val result = parser.extractRoute(null)
            assertEquals("failed", result.status)
            assertEquals("", result.route)
            assertEquals("", result.navigationKey)
            assertTrue(result.params.isEmpty())
        }
    }

    @Test
    fun `extractRoute should return failed for blank URI`() {
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
            val result = parser.extractRoute("   ")
            assertEquals("failed", result.status)
        }
    }

    @Test
    fun `extractRoute should return failed for malformed URI that causes Uri_parse to throw`() {
        val malformedUri = "http://[:]"
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            mockedLog.`when`<Int> { Log.e(anyString(), anyString(), Mockito.any(Throwable::class.java)) }.thenReturn(0)
            mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0) // For createFailedRoute

            Mockito.mockStatic(Uri::class.java).use { mockedStaticUri ->
                mockedStaticUri.`when`<Uri> { Uri.parse(malformedUri) }.thenThrow(IllegalArgumentException("Test URI Parse Exception"))
                val result = parser.extractRoute(malformedUri)
                assertEquals("failed", result.status)
            }
        }
    }

    @Test
    fun `extractRoute should return failed for path not starting with prefix`() {
        val rawUri = "app://example/wrongprefix/myfeature"
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
            Mockito.mockStatic(Uri::class.java).use { mockedStaticUri ->
                setupUriParsing(rawUri, "/wrongprefix/myfeature", listOf("wrongprefix", "myfeature"))
                mockedStaticUri.`when`<Uri> { Uri.parse(rawUri) }.thenReturn(mockUri) // Ensure Uri.parse returns our mock

                val result = parser.extractRoute(rawUri)
                assertEquals("failed", result.status)
            }
        }
    }

    @Test
    fun `extractRoute should return failed for null path from Uri`() {
        val rawUri = "app:feature" // Example that might yield null path
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
            Mockito.mockStatic(Uri::class.java).use { mockedStaticUri ->
                setupUriParsing(rawUri, null, null) // path is null
                mockedStaticUri.`when`<Uri> { Uri.parse(rawUri) }.thenReturn(mockUri)

                val result = parser.extractRoute(rawUri)
                assertEquals("failed", result.status)
            }
        }
    }
    @Test
    fun `extractRoute should parse simple feature route (fallback)`() {
        val rawUri = "app://example.com/chase/df/myfeature"
        val expectedPath = "/chase/df/myfeature"
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0) // Suppress logs
            Mockito.mockStatic(Uri::class.java).use { mockedStaticUri ->
                setupUriParsing(rawUri, expectedPath, listOf("chase", "df", "myfeature"))
                mockedStaticUri.`when`<Uri> { Uri.parse(rawUri) }.thenReturn(mockUri)

                val result = parser.extractRoute(rawUri)

                assertEquals("success", result.status)
                assertEquals(expectedPath, result.path)
                assertEquals("myfeature", result.route)
                assertEquals("", result.navigationKey)
                assertTrue(result.params.isEmpty())
            }
        }
    }

    @Test
    fun `extractRoute should parse explicit feature route`() {
        val rawUri = "app://example.com/chase/df/route/myexplicitfeature"
        val expectedPath = "/chase/df/route/myexplicitfeature"
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            Mockito.mockStatic(Uri::class.java).use { mockedStaticUri ->
                setupUriParsing(rawUri, expectedPath, listOf("chase", "df", "route", "myexplicitfeature"))
                mockedStaticUri.`when`<Uri> { Uri.parse(rawUri) }.thenReturn(mockUri)

                val result = parser.extractRoute(rawUri)

                assertEquals("success", result.status)
                assertEquals(expectedPath, result.path)
                assertEquals("myexplicitfeature", result.route)
                assertEquals("", result.navigationKey)
                assertTrue(result.params.isEmpty())
            }
        }
    }

    @Test
    fun `extractRoute should parse navigation key route`() {
        val rawUri = "app://example.com/chase/df/navigation/key/myactivitykey"
        val expectedPath = "/chase/df/navigation/key/myactivitykey"
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            Mockito.mockStatic(Uri::class.java).use { mockedStaticUri ->
                setupUriParsing(rawUri, expectedPath, listOf("chase", "df", "navigation", "key", "myactivitykey"))
                mockedStaticUri.`when`<Uri> { Uri.parse(rawUri) }.thenReturn(mockUri)

                val result = parser.extractRoute(rawUri)

                assertEquals("success", result.status)
                assertEquals(expectedPath, result.path)
                assertEquals("", result.route)
                assertEquals("myactivitykey", result.navigationKey)
                assertTrue(result.params.isEmpty())
            }
        }
    }

    @Test
    fun `extractRoute should parse route with query parameters`() {
        val rawUri = "app://example.com/chase/df/myfeature?param1=value1&param2=value2"
        val expectedPath = "/chase/df/myfeature"
        val queryParamNames = setOf("param1", "param2")
        val queryParamsMap = mapOf("param1" to "value1", "param2" to "value2")

        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            Mockito.mockStatic(Uri::class.java).use { mockedStaticUri ->
                setupUriParsing(rawUri, expectedPath, listOf("chase", "df", "myfeature"), queryParamNames, queryParamsMap)
                mockedStaticUri.`when`<Uri> { Uri.parse(rawUri) }.thenReturn(mockUri)

                val result = parser.extractRoute(rawUri)

                assertEquals("success", result.status)
                assertEquals(expectedPath, result.path)
                assertEquals("myfeature", result.route)
                assertEquals(listOf("param1=value1", "param2=value2"), result.params)
            }
        }
    }

    @Test
    fun `extractRoute should return failed for explicit route with missing route name`() {
        val rawUri = "app://example.com/chase/df/route/"
        val expectedPath = "/chase/df/route/"
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
            Mockito.mockStatic(Uri::class.java).use { mockedStaticUri ->
                setupUriParsing(rawUri, expectedPath, listOf("chase", "df", "route"))
                mockedStaticUri.`when`<Uri> { Uri.parse(rawUri) }.thenReturn(mockUri)

                val result = parser.extractRoute(rawUri)
                assertEquals("failed", result.status)
            }
        }
    }

    @Test
    fun `extractRoute should return failed for navigation route with missing key name`() {
        val rawUri = "app://example.com/chase/df/navigation/key/"
        val expectedPath = "/chase/df/navigation/key/"
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0)
            Mockito.mockStatic(Uri::class.java).use { mockedStaticUri ->
                setupUriParsing(rawUri, expectedPath, listOf("chase", "df", "navigation", "key"))
                mockedStaticUri.`when`<Uri> { Uri.parse(rawUri) }.thenReturn(mockUri)

                val result = parser.extractRoute(rawUri)
                assertEquals("failed", result.status)
            }
        }
    }
    @Test
    fun `extractRoute should return failed for URI path only containing prefix`() {
        val rawUri = "app://example.com/chase/df/"
        val expectedPath = "/chase/df/"
        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            mockedLog.`when`<Int> { Log.e(anyString(), anyString()) }.thenReturn(0) // For logs in SUT
            Mockito.mockStatic(Uri::class.java).use { mockedStaticUri ->
                // When Uri.parse is called with rawUri, return our mockUri
                mockedStaticUri.`when`<Uri> { Uri.parse(rawUri) }.thenReturn(mockUri)
                // Configure mockUri behavior
                `when`(mockUri.path).thenReturn(expectedPath)
                // Path segments for "/chase/df/" would be ["chase", "df"].
                // relevantSegments will be emptyList().
                `when`(mockUri.pathSegments).thenReturn(listOf("chase", "df"))
                `when`(mockUri.queryParameterNames).thenReturn(emptySet())


                val result = parser.extractRoute(rawUri)
                assertEquals("failed", result.status) // This hits the final else case
            }
        }
    }

    @Test
    fun `extractRoute handles query parameter parsing exception gracefully`() {
        val rawUri = "app://example.com/chase/df/myfeature?param1=value1"
        val expectedPath = "/chase/df/myfeature"

        Mockito.mockStatic(Log::class.java).use { mockedLog ->
            mockedLog.`when`<Int> { Log.e(anyString(), anyString(), Mockito.any(Throwable::class.java)) }.thenReturn(0) // For the catch block
            Mockito.mockStatic(Uri::class.java).use { mockedStaticUri ->
                setupUriParsing(rawUri, expectedPath, listOf("chase", "df", "myfeature"), setOf("param1"), mapOf("param1" to "value1"))
                mockedStaticUri.`when`<Uri> { Uri.parse(rawUri) }.thenReturn(mockUri)
                // Make getQueryParameterNames throw an exception
                `when`(mockUri.queryParameterNames).thenThrow(RuntimeException("Query parsing failed"))

                val result = parser.extractRoute(rawUri)

                assertEquals("success", result.status) // Should still succeed for the route part
                assertEquals("myfeature", result.route)
                assertTrue("Params should be empty due to exception", result.params.isEmpty())
            }
        }
    }
}