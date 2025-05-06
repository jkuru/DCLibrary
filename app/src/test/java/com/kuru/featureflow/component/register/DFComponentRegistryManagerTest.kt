package com.kuru.featureflow.component.register

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.* // For verify, when, etc.
// If you prefer Mockito-Kotlin, you can use:
// import org.mockito.kotlin.*

class DFComponentRegistryManagerTest {

    // Lateinit var for the class under test
    private lateinit var registryManager: DFComponentRegistryManager

    // Mock for the dependency
    private lateinit var mockRegistryData: ComponentRegistryData

    // Sample data for testing
    private val testConfig = DFComponentConfig(route = "testRoute")
    private val testScreen: @Composable (NavController, List<String>) -> Unit = { _, _ -> /* Test Screen UI */ }

    @Before
    fun setUp() {
        // Create a mock for ComponentRegistryData before each test
        mockRegistryData = mock(ComponentRegistryData::class.java)
        // It's crucial to mock android.util.Log if its methods are called and not handled by testOptions
        // For simplicity here, we're assuming Log.e calls in constructor/methods won't crash if `returnDefaultValues = true` is set in build.gradle
        // or if we specifically mock Log. If not, the test environment needs to handle Android framework calls.
        // A better practice for production code is to inject a Logger interface.

        // Instantiate the class under test with the mock dependency
        registryManager = DFComponentRegistryManager(mockRegistryData)
        // Mock static Log calls
        mockkStatic(Log::class)
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0 // For Log.w(tag, msg)
        every { Log.w(any(), any<Throwable>()) } returns 0 // For Log.w(tag, Throwable)
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0 // For Log.w(tag, msg, Throwable)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0 // For Log.e(tag, msg, throwable)
    }

    @Test
    fun `register should call put on registryData`() {
        // Act
        registryManager.register(testConfig, testScreen)

        // Assert
        // Verify that registryData.put() was called exactly once with the correct arguments
        verify(mockRegistryData, times(1)).put(testConfig, testScreen)
    }

    @Test
    fun `getScreen should call get on registryData and return its result`() {
        // Arrange
        `when`(mockRegistryData.get(testConfig)).thenReturn(testScreen)

        // Act
        val resultScreen = registryManager.getScreen(testConfig)

        // Assert
        verify(mockRegistryData, times(1)).get(testConfig)
        assertSame("Returned screen should be the one from registryData", testScreen, resultScreen)
    }

    @Test
    fun `getScreen should return null if registryData_get returns null`() {
        // Arrange
        `when`(mockRegistryData.get(testConfig)).thenReturn(null)

        // Act
        val resultScreen = registryManager.getScreen(testConfig)

        // Assert
        verify(mockRegistryData, times(1)).get(testConfig)
        assertNull("Returned screen should be null", resultScreen)
    }

    @Test
    fun `unregister should call remove on registryData and return its result - true`() {
        // Arrange
        `when`(mockRegistryData.remove(testConfig)).thenReturn(true)

        // Act
        val result = registryManager.unregister(testConfig)

        // Assert
        verify(mockRegistryData, times(1)).remove(testConfig)
        assertTrue("Result of unregister should be true", result)
    }

    @Test
    fun `unregister should call remove on registryData and return its result - false`() {
        // Arrange
        `when`(mockRegistryData.remove(testConfig)).thenReturn(false)

        // Act
        val result = registryManager.unregister(testConfig)

        // Assert
        verify(mockRegistryData, times(1)).remove(testConfig)
        assertFalse("Result of unregister should be false", result)
    }

    @Test
    fun `isRegistrationValid should call contains on registryData and return its result - true`() {
        // Arrange
        `when`(mockRegistryData.contains(testConfig)).thenReturn(true)

        // Act
        val result = registryManager.isRegistrationValid(testConfig)

        // Assert
        verify(mockRegistryData, times(1)).contains(testConfig)
        assertTrue("Result of isRegistrationValid should be true", result)
    }

    @Test
    fun `isRegistrationValid should call contains on registryData and return its result - false`() {
        // Arrange
        `when`(mockRegistryData.contains(testConfig)).thenReturn(false)

        // Act
        val result = registryManager.isRegistrationValid(testConfig)

        // Assert
        verify(mockRegistryData, times(1)).contains(testConfig)
        assertFalse("Result of isRegistrationValid should be false", result)
    }

    @Test
    fun `getConfig should call getConfigByRoute on registryData and return its result`() {
        val route = "testRoute"
        // Arrange
        `when`(mockRegistryData.getConfigByRoute(route)).thenReturn(testConfig)

        // Act
        val resultConfig = registryManager.getConfig(route)

        // Assert
        verify(mockRegistryData, times(1)).getConfigByRoute(route)
        assertSame("Returned config should be the one from registryData", testConfig, resultConfig)
    }

    @Test
    fun `getConfig should return null if registryData_getConfigByRoute returns null`() {
        val route = "nonExistentRoute"
        // Arrange
        `when`(mockRegistryData.getConfigByRoute(route)).thenReturn(null)

        // Act
        val resultConfig = registryManager.getConfig(route)

        // Assert
        verify(mockRegistryData, times(1)).getConfigByRoute(route)
        assertNull("Returned config should be null", resultConfig)
    }
}