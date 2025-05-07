package com.kuru.featureflow.component.domain

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.kuru.featureflow.component.register.DFFeatureConfig
import com.kuru.featureflow.component.register.DFFeatureInterceptor // Assuming DFFeatureInterceptor exists
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DFFeatureRegistryUseCaseTest {

    private lateinit var useCase: DFFeatureRegistryUseCase

    // Dummy composable screen for testing
    private val dummyScreen1: @Composable (NavController, List<String>) -> Unit = { _, _ -> /* Screen 1 */ }
    private val dummyScreen2: @Composable (NavController, List<String>) -> Unit = { _, _ -> /* Screen 2 */ }

    private lateinit var mockNavController: NavController
    private lateinit var mockInterceptor: DFFeatureInterceptor // For creating varied DFFeatureConfig instances

    @BeforeTest
    fun setUp() {
        // Mock Android's Log methods
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0

        mockNavController = mockk() // Mock NavController for the Composable signature
        mockInterceptor = mockk()   // Mock an interceptor

        useCase = DFFeatureRegistryUseCase()
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `register - should add config and screen to registry`() {
        // Given
        val config1 = DFFeatureConfig(route = "feature1/home")

        // When
        useCase.register(config1, dummyScreen1)

        // Then
        val retrievedScreen = useCase.getScreen(config1)
        assertEquals(dummyScreen1, retrievedScreen, "Screen for config1 should be dummyScreen1")
        assertTrue(useCase.isRegistrationValid(config1), "Config1 should be registered")
        verify(exactly = 1) { Log.d("DFFeatureRegistryUseCase", "Registered screen for route: ${config1.route}") }
    }

    @Test
    fun `register - should overwrite existing registration for the same config object`() {
        // Given
        val config1 = DFFeatureConfig(route = "feature1/home")
        useCase.register(config1, dummyScreen1) // Initial registration

        // When
        useCase.register(config1, dummyScreen2) // Re-register with a new screen

        // Then
        val retrievedScreen = useCase.getScreen(config1)
        assertEquals(dummyScreen2, retrievedScreen, "Screen for config1 should be updated to dummyScreen2")
        assertTrue(useCase.isRegistrationValid(config1), "Config1 should still be registered")
    }

    @Test
    fun `getScreen - should return registered screen for a config`() {
        // Given
        val config1 = DFFeatureConfig(route = "feature1/home")
        useCase.register(config1, dummyScreen1)

        // When
        val screen = useCase.getScreen(config1)

        // Then
        assertEquals(dummyScreen1, screen)
    }

    @Test
    fun `getScreen - should return null for an unregistered config`() {
        // Given
        val unregisteredConfig = DFFeatureConfig(route = "unregistered/feature")

        // When
        val screen = useCase.getScreen(unregisteredConfig)

        // Then
        assertNull(screen, "Screen for an unregistered config should be null")
    }

    @Test
    fun `getScreenByRoute - should return registered screen for a known route`() {
        // Given
        val route1 = "feature1/details"
        val config1 = DFFeatureConfig(route = route1)
        useCase.register(config1, dummyScreen1)

        // When
        val screen = useCase.getScreenByRoute(route1)

        // Then
        assertEquals(dummyScreen1, screen, "Screen for route '$route1' should be dummyScreen1")
    }

    @Test
    fun `getScreenByRoute - should return null for an unknown route`() {
        // Given
        val unknownRoute = "nonexistent/route"

        // When
        val screen = useCase.getScreenByRoute(unknownRoute)

        // Then
        assertNull(screen, "Screen for an unknown route '$unknownRoute' should be null")
    }

    @Test
    fun `getScreenByRoute - should return correct screen when multiple configs are registered`() {
        // Given
        val route1 = "featureA/main"
        val configA = DFFeatureConfig(route = route1)
        useCase.register(configA, dummyScreen1)

        val route2 = "featureB/settings"
        // Create a config with different interceptors to ensure it's a distinct key if routes were the same
        // For this test, routes are different, so even default interceptors would work.
        val configB = DFFeatureConfig(route = route2, listOfDFFeatureInterceptor = listOf(mockInterceptor))
        useCase.register(configB, dummyScreen2)

        // When & Then
        assertEquals(dummyScreen1, useCase.getScreenByRoute(route1))
        assertEquals(dummyScreen2, useCase.getScreenByRoute(route2))
    }


    @Test
    fun `unregister - should remove config and screen from registry`() {
        // Given
        val config1 = DFFeatureConfig(route = "featureToRemove")
        useCase.register(config1, dummyScreen1)
        assertTrue(useCase.isRegistrationValid(config1), "Config should be registered initially")

        // When
        val wasRemoved = useCase.unregister(config1)

        // Then
        assertTrue(wasRemoved, "Unregister should return true for a registered feature")
        assertFalse(useCase.isRegistrationValid(config1), "Config should no longer be registered")
        assertNull(useCase.getScreen(config1), "Screen for the unregistered config should be null")
        verify(exactly = 1) { Log.d("DFFeatureRegistryUseCase", "Unregistered screen for route: ${config1.route}") }
    }

    @Test
    fun `unregister - should return false for an unregistered config`() {
        // Given
        val unregisteredConfig = DFFeatureConfig(route = "neverRegistered")

        // When
        val wasRemoved = useCase.unregister(unregisteredConfig)

        // Then
        assertFalse(wasRemoved, "Unregister should return false for a non-existent feature")
    }

    @Test
    fun `isRegistrationValid - should return true for a registered config`() {
        // Given
        val config1 = DFFeatureConfig(route = "validFeature")
        useCase.register(config1, dummyScreen1)

        // When & Then
        assertTrue(useCase.isRegistrationValid(config1))
    }

    @Test
    fun `isRegistrationValid - should return false for an unregistered config`() {
        // Given
        val config1 = DFFeatureConfig(route = "invalidFeature")

        // When & Then
        assertFalse(useCase.isRegistrationValid(config1))
    }

    @Test
    fun `getConfig - should return registered config for a known route`() {
        // Given
        val route1 = "configFeature/path"
        val config1 = DFFeatureConfig(route = route1)
        useCase.register(config1, dummyScreen1)

        // When
        val retrievedConfig = useCase.getConfig(route1)

        // Then
        assertEquals(config1, retrievedConfig, "Retrieved config for route '$route1' should match the registered one")
    }

    @Test
    fun `getConfig - should return null for an unknown route`() {
        // Given
        val unknownRoute = "noConfigHere/route"

        // When
        val retrievedConfig = useCase.getConfig(unknownRoute)

        // Then
        assertNull(retrievedConfig, "Config for an unknown route '$unknownRoute' should be null")
    }

    @Test
    fun `getConfig - should return correct config when multiple are registered`() {
        // Given
        val routeX = "routeX"
        val configX = DFFeatureConfig(route = routeX)
        useCase.register(configX, dummyScreen1)

        val routeY = "routeY"
        val configY = DFFeatureConfig(route = routeY, listOfDFFeatureInterceptor = listOf(mockInterceptor))
        useCase.register(configY, dummyScreen2)

        // When & Then
        assertEquals(configX, useCase.getConfig(routeX))
        assertEquals(configY, useCase.getConfig(routeY))
    }
}