package com.kuru.featureflow.component.domain

import com.kuru.featureflow.component.ui.ErrorType // Make sure this import is correct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DFLoadFeatureResultTest {

    @Test
    fun `ProceedToPostInstall is a distinct object`() {
        // Arrange & Act
        val result = DFLoadFeatureResult.ProceedToPostInstall

        // Assert
        assertNotNull(result)
        assertTrue(result is DFLoadFeatureResult.ProceedToPostInstall)
    }

    @Test
    fun `ProceedToInstallationMonitoring is a distinct object`() {
        // Arrange & Act
        val result = DFLoadFeatureResult.ProceedToInstallationMonitoring

        // Assert
        assertNotNull(result)
        assertTrue(result is DFLoadFeatureResult.ProceedToInstallationMonitoring)
    }

    @Test
    fun `Failure holds correct errorType and message`() {
        // Arrange
        val expectedErrorType = ErrorType.VALIDATION
        val expectedMessage = "Feature name cannot be empty"

        // Act
        val result = DFLoadFeatureResult.Failure(expectedErrorType, expectedMessage)

        // Assert
        assertEquals(expectedErrorType, result.errorType)
        assertEquals(expectedMessage, result.message)
    }

    @Test
    fun `Failure can be instantiated with different error types and messages`() {
        // Arrange
        val expectedErrorType = ErrorType.INSTALLATION
        val expectedMessage = "Failed to determine installation status"

        // Act
        val result = DFLoadFeatureResult.Failure(expectedErrorType, expectedMessage)

        // Assert
        assertEquals(expectedErrorType, result.errorType)
        assertEquals(expectedMessage, result.message)
    }
}
