package com.kuru.featureflow.component.domain

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DFRunPostInstallResultTest {

    @Test
    fun `Success holds correct screen lambda`() {
        // Arrange
        val mockNavController = mockk<NavController>()
        val mockParams = listOf("testParam")
        val expectedScreen: @Composable (NavController, List<String>) -> Unit = { navController, params ->
            // Dummy composable for testing
            assertEquals(mockNavController, navController)
            assertEquals(mockParams, params)
        }

        // Act
        val result = DFRunPostInstallResult.Success(expectedScreen)

        // Assert
        // We can't directly compare lambdas for equality in a meaningful way for this test.
        // Instead, we'll invoke it and check if it behaves as expected (though this is a bit of an integration test for the lambda itself).
        // A simpler assertion is to check if the reference is the same.
        assertSame(expectedScreen, result.screen)

        // To actually test the lambda's behavior (optional for a unit test of DFRunPostInstallResult itself):
        // result.screen(mockNavController, mockParams) // This would execute the dummy composable's assertions
    }

    @Test
    fun `Failure holds correct step, message, and null cause by default`() {
        // Arrange
        val expectedStep = Step.SERVICE_LOADER_INITIALIZATION
        val expectedMessage = "ServiceLoader failed to initialize."

        // Act
        val result = DFRunPostInstallResult.Failure(expectedStep, expectedMessage)

        // Assert
        assertEquals(expectedStep, result.step)
        assertEquals(expectedMessage, result.message)
        assertNull(result.cause)
    }

    @Test
    fun `Failure holds correct step, message, and cause when provided`() {
        // Arrange
        val expectedStep = Step.POST_INSTALL_INTERCEPTORS
        val expectedMessage = "Post-install interceptor threw an exception."
        val expectedCause = RuntimeException("Interceptor error")

        // Act
        val result = DFRunPostInstallResult.Failure(expectedStep, expectedMessage, expectedCause)

        // Assert
        assertEquals(expectedStep, result.step)
        assertEquals(expectedMessage, result.message)
        assertEquals(expectedCause, result.cause)
    }

    @Test
    fun `Step enum contains all expected values`() {
        // Arrange
        val expectedSteps = setOf(
            Step.SERVICE_LOADER_INITIALIZATION,
            Step.POST_INSTALL_INTERCEPTORS,
            Step.FETCH_DYNAMIC_SCREEN
        )

        // Act
        val actualSteps = Step.values().toSet()

        // Assert
        assertEquals(expectedSteps.size, actualSteps.size)
        assertTrue(actualSteps.containsAll(expectedSteps))
    }

    @Test
    fun `Step enum values have correct ordinals and names (optional check)`() {
        assertEquals(0, Step.SERVICE_LOADER_INITIALIZATION.ordinal)
        assertEquals("SERVICE_LOADER_INITIALIZATION", Step.SERVICE_LOADER_INITIALIZATION.name)

        assertEquals(1, Step.POST_INSTALL_INTERCEPTORS.ordinal)
        assertEquals("POST_INSTALL_INTERCEPTORS", Step.POST_INSTALL_INTERCEPTORS.name)

        assertEquals(2, Step.FETCH_DYNAMIC_SCREEN.ordinal)
        assertEquals("FETCH_DYNAMIC_SCREEN", Step.FETCH_DYNAMIC_SCREEN.name)
    }
}
