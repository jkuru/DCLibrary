package com.kuru.featureflow.component.domain

import android.util.Log
import com.kuru.featureflow.component.route.DFComponentRoute
import com.kuru.featureflow.component.route.DFComponentUriRouteParser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DFProcessUriUseCaseTest {

    @MockK
    private lateinit var mockRouteParser: DFComponentUriRouteParser

    private lateinit var processUriUseCase: DFProcessUriUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        processUriUseCase = DFProcessUriUseCase(mockRouteParser)

        // Mock static Log calls
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `invoke with valid feature URI returns FeatureRoute`() {
        // Arrange
        val uri = "app://chase/df/route/myFeature?param1=value1"
        val expectedRouteName = "myFeature"
        val expectedParams = listOf("param1=value1")
        val mockParsedRoute = DFComponentRoute(
            path = "/chase/df/route/myFeature",
            route = expectedRouteName,
            navigationKey = "",
            params = expectedParams,
            status = "success"
        )
        every { mockRouteParser.extractRoute(uri) } returns mockParsedRoute

        // Act
        val result = processUriUseCase(uri)

        // Assert
        assertTrue(result is DFProcessUriResult.FeatureRoute)
        val featureRouteResult = result as DFProcessUriResult.FeatureRoute
        assertEquals(expectedRouteName, featureRouteResult.name)
        assertEquals(expectedParams, featureRouteResult.params)
    }

    @Test
    fun `invoke with valid navigation key URI returns NavigationRoute`() {
        // Arrange
        val uri = "app://chase/df/navigation/key/settingsScreen?id=100"
        val expectedNavKey = "settingsScreen"
        val expectedParams = listOf("id=100")
        val mockParsedRoute = DFComponentRoute(
            path = "/chase/df/navigation/key/settingsScreen",
            route = "",
            navigationKey = expectedNavKey,
            params = expectedParams,
            status = "success"
        )
        every { mockRouteParser.extractRoute(uri) } returns mockParsedRoute

        // Act
        val result = processUriUseCase(uri)

        // Assert
        assertTrue(result is DFProcessUriResult.NavigationRoute)
        val navRouteResult = result as DFProcessUriResult.NavigationRoute
        assertEquals(expectedNavKey, navRouteResult.key)
        assertEquals(expectedParams, navRouteResult.params)
    }

    @Test
    fun `invoke with URI that parser marks as failed returns InvalidUri`() {
        // Arrange
        val uri = "app://invalid/uri"
        val mockParsedRoute = DFComponentRoute(
            path = "",
            route = "",
            navigationKey = "",
            params = emptyList(),
            status = "failed" // Parser indicates failure
        )
        every { mockRouteParser.extractRoute(uri) } returns mockParsedRoute

        // Act
        val result = processUriUseCase(uri)

        // Assert
        assertTrue(result is DFProcessUriResult.InvalidUri)
        val invalidResult = result as DFProcessUriResult.InvalidUri
        assertEquals("URI parsing failed or did not produce a successful route structure.", invalidResult.reason)
    }

    @Test
    fun `invoke with null URI returns InvalidUri from parser`() {
        // Arrange
        val uri: String? = null
        val mockParsedRoute = DFComponentRoute( // Assuming parser handles null and returns a "failed" route
            path = "",
            route = "",
            navigationKey = "",
            params = emptyList(),
            status = "failed"
        )
        every { mockRouteParser.extractRoute(uri) } returns mockParsedRoute

        // Act
        val result = processUriUseCase(uri)

        // Assert
        assertTrue(result is DFProcessUriResult.InvalidUri)
        val invalidResult = result as DFProcessUriResult.InvalidUri
        assertEquals("URI parsing failed or did not produce a successful route structure.", invalidResult.reason)
    }

    @Test
    fun `invoke with URI that parser marks success but has no route or navKey returns InvalidUri`() {
        // Arrange
        val uri = "app://chase/df/" // Example URI that might parse successfully but be incomplete
        val mockParsedRoute = DFComponentRoute(
            path = "/chase/df/",
            route = "", // No route
            navigationKey = "", // No navKey
            params = emptyList(),
            status = "success" // Parser status is success
        )
        every { mockRouteParser.extractRoute(uri) } returns mockParsedRoute

        // Act
        val result = processUriUseCase(uri)

        // Assert
        assertTrue(result is DFProcessUriResult.InvalidUri)
        val invalidResult = result as DFProcessUriResult.InvalidUri
        assertEquals("Parsed route structure is incomplete (missing route or navigation key).", invalidResult.reason)
    }

    @Test
    fun `invoke with URI that parser returns feature route with empty params`() {
        // Arrange
        val uri = "app://chase/df/route/simpleFeature"
        val expectedRouteName = "simpleFeature"
        val mockParsedRoute = DFComponentRoute(
            path = "/chase/df/route/simpleFeature",
            route = expectedRouteName,
            navigationKey = "",
            params = emptyList(), // Empty params
            status = "success"
        )
        every { mockRouteParser.extractRoute(uri) } returns mockParsedRoute

        // Act
        val result = processUriUseCase(uri)

        // Assert
        assertTrue(result is DFProcessUriResult.FeatureRoute)
        val featureRouteResult = result as DFProcessUriResult.FeatureRoute
        assertEquals(expectedRouteName, featureRouteResult.name)
        assertTrue(featureRouteResult.params.isEmpty())
    }
}
