package com.kuru.featureflow.component.register

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class ComponentRegistryDataTest {

    private lateinit var registryData: ComponentRegistryData

    // Mock a NavController - its specific behavior isn't crucial for these tests
    private val mockNavController: NavController = mock(NavController::class.java)

    // Sample Composable screen functions for testing
    private val screen1: @Composable (NavController, List<String>) -> Unit = { _, _ -> /* Screen 1 UI */ }
    private val screen2: @Composable (NavController, List<String>) -> Unit = { _, _ -> /* Screen 2 UI */ }
    private val screen3: @Composable (NavController, List<String>) -> Unit = { _, _ -> /* Screen 3 UI */ }


    @Before
    fun setUp() {
        registryData = ComponentRegistryData()
    }

    @Test
    fun `put should add a config and screen to the registry`() {
        val config1 = DFComponentConfig(route = "feature1")
        registryData.put(config1, screen1)

        assertTrue("Registry should contain the added config", registryData.contains(config1))
        assertSame("Retrieved screen should be the same as the one put", screen1, registryData.get(config1))
    }

    @Test
    fun `get should return the correct screen for a registered config`() {
        val config1 = DFComponentConfig(route = "feature1")
        registryData.put(config1, screen1)

        val retrievedScreen = registryData.get(config1)
        assertNotNull("Retrieved screen should not be null for a registered config", retrievedScreen)
        assertSame("Retrieved screen should be the one registered", screen1, retrievedScreen)
    }

    @Test
    fun `get should return null for an unregistered config`() {
        val config1 = DFComponentConfig(route = "feature1")
        val unregisteredConfig = DFComponentConfig(route = "feature_unknown")

        registryData.put(config1, screen1)

        val retrievedScreen = registryData.get(unregisteredConfig)
        assertNull("Retrieved screen should be null for an unregistered config", retrievedScreen)
    }

    @Test
    fun `put should overwrite an existing registration for the same config`() {
        val config1 = DFComponentConfig(route = "feature1")

        registryData.put(config1, screen1) // Initial registration
        assertSame("Initially registered screen should be screen1", screen1, registryData.get(config1))

        registryData.put(config1, screen2) // Overwrite with screen2
        assertSame("Overwritten screen should now be screen2", screen2, registryData.get(config1))
    }

    @Test
    fun `remove should delete a config and screen from the registry and return true`() {
        val config1 = DFComponentConfig(route = "feature1")
        registryData.put(config1, screen1)

        assertTrue("Registry should contain config before removal", registryData.contains(config1))
        val wasRemoved = registryData.remove(config1)
        assertTrue("remove should return true for an existing config", wasRemoved)
        assertFalse("Registry should not contain config after removal", registryData.contains(config1))
        assertNull("Getting screen after removal should return null", registryData.get(config1))
    }

    @Test
    fun `remove should return false for a non-existent config`() {
        val config1 = DFComponentConfig(route = "feature1")
        val nonExistentConfig = DFComponentConfig(route = "feature_non_existent")

        registryData.put(config1, screen1) // Add something to make sure registry isn't empty

        val wasRemoved = registryData.remove(nonExistentConfig)
        assertFalse("remove should return false for a non-existent config", wasRemoved)
        assertTrue("Registry should still contain the initially added config", registryData.contains(config1))
    }

    @Test
    fun `contains should return true for a registered config`() {
        val config1 = DFComponentConfig(route = "feature1")
        registryData.put(config1, screen1)
        assertTrue("contains should return true for a registered config", registryData.contains(config1))
    }

    @Test
    fun `contains should return false for an unregistered config`() {
        val config1 = DFComponentConfig(route = "feature1")
        val unregisteredConfig = DFComponentConfig(route = "feature_unregistered")
        registryData.put(config1, screen1) // Add some data

        assertFalse("contains should return false for an unregistered config", registryData.contains(unregisteredConfig))
    }

    @Test
    fun `contains should return false for an empty registry`() {
        val config1 = DFComponentConfig(route = "feature1")
        assertFalse("contains should return false for an empty registry", registryData.contains(config1))
    }

    @Test
    fun `getConfigByRoute should return the correct config for a registered route`() {
        val interceptors = listOf(DFComponentInterceptor(preInstall = true) { true })
        val config1 = DFComponentConfig(route = "feature1/home", listOfDFComponentInterceptor = interceptors)
        val config2 = DFComponentConfig(route = "feature2/settings")
        registryData.put(config1, screen1)
        registryData.put(config2, screen2)

        val retrievedConfig = registryData.getConfigByRoute("feature1/home")
        assertNotNull("Retrieved config should not be null", retrievedConfig)
        assertEquals("Retrieved config should be config1", config1, retrievedConfig)
        assertEquals("Retrieved config route should match", "feature1/home", retrievedConfig?.route)
        assertEquals("Retrieved config interceptors should match", interceptors, retrievedConfig?.listOfDFComponentInterceptor)
    }

    @Test
    fun `getConfigByRoute should return null if no config matches the route`() {
        val config1 = DFComponentConfig(route = "feature1")
        registryData.put(config1, screen1)

        val retrievedConfig = registryData.getConfigByRoute("non_existent_route")
        assertNull("Retrieved config should be null for a non-existent route", retrievedConfig)
    }

    @Test
    fun `getConfigByRoute should return the first matching config if multiple configs have the same route (though unlikely)`() {
        // This scenario is generally not expected as DFComponentConfig is a data class and used as a key.
        // If two distinct DFComponentConfig instances have the same route, they are still different keys
        // unless their other properties (listOfDFComponentInterceptor) are also identical.
        // For this test, we'll use identical configs which would point to the same entry,
        // or slightly different ones to see how `find` behaves on keys.
        // `ConcurrentHashMap.keys.find` will return the first one it encounters.

        val config1a = DFComponentConfig(route = "feature_shared")
        val config1b = DFComponentConfig(route = "feature_shared", listOfDFComponentInterceptor = listOf(DFComponentInterceptor(true){false}))
        // config1a and config1b are different objects and would be different keys.

        registryData.put(config1a, screen1)
        registryData.put(config1b, screen2) // Different config (due to interceptors) but same route for find

        val retrievedConfig = registryData.getConfigByRoute("feature_shared")
        assertNotNull("Retrieved config should not be null", retrievedConfig)
        // The exact one returned (config1a or config1b) depends on the iteration order of the map's keySet.
        // We just need to ensure one of them whose route matches is returned.
        assertEquals("feature_shared", retrievedConfig?.route)
        assertTrue("Retrieved config should be one of the registered configs with that route",
            retrievedConfig == config1a || retrievedConfig == config1b)
    }

    @Test
    fun `getConfigByRoute should handle routes with special characters if DFComponentConfig allows them`() {
        // Assuming route can contain such characters.
        val routeWithSpecialChars = "feature/with-hyphen_and_numbers123"
        val configSpecial = DFComponentConfig(route = routeWithSpecialChars)
        registryData.put(configSpecial, screen1)

        val retrievedConfig = registryData.getConfigByRoute(routeWithSpecialChars)
        assertNotNull("Config should be found for route with special characters", retrievedConfig)
        assertEquals(configSpecial, retrievedConfig)
    }

    @Test
    fun `operations should work correctly after multiple puts and removes`() {
        val config1 = DFComponentConfig(route = "route1")
        val config2 = DFComponentConfig(route = "route2")
        val config3 = DFComponentConfig(route = "route3")

        // Add 1 and 2
        registryData.put(config1, screen1)
        registryData.put(config2, screen2)
        assertTrue(registryData.contains(config1))
        assertTrue(registryData.contains(config2))
        assertEquals(screen1, registryData.get(config1))
        assertEquals(screen2, registryData.get(config2))

        // Remove 1, Add 3
        assertTrue(registryData.remove(config1))
        assertFalse(registryData.contains(config1))
        registryData.put(config3, screen3)
        assertTrue(registryData.contains(config3))
        assertEquals(screen3, registryData.get(config3))
        assertEquals(config3, registryData.getConfigByRoute("route3"))

        // Check 2 is still there
        assertTrue(registryData.contains(config2))
        assertEquals(screen2, registryData.get(config2))
        assertEquals(config2, registryData.getConfigByRoute("route2"))

        // Remove 3
        assertTrue(registryData.remove(config3))
        assertFalse(registryData.contains(config3))

        // Try to get 1 and 3 (should be null)
        assertNull(registryData.get(config1))
        assertNull(registryData.get(config3))
        assertNull(registryData.getConfigByRoute("route1"))
        assertNull(registryData.getConfigByRoute("route3"))
    }
}