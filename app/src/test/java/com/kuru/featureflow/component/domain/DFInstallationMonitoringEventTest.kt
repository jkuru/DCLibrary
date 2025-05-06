package com.kuru.featureflow.component.domain

import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.kuru.featureflow.component.ui.DFComponentState
import com.kuru.featureflow.component.ui.ErrorType
import com.kuru.featureflow.component.state.DFErrorCode
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DFInstallationMonitoringEventTest {

    @Test
    fun `UpdateUiState holds correct DFComponentState`() {
        // Arrange
        val mockUiState = DFComponentState.Loading // Using a simple state for example

        // Act
        val event = DFInstallationMonitoringEvent.UpdateUiState(mockUiState)

        // Assert
        assertEquals(mockUiState, event.state)
    }

    @Test
    fun `UpdateUiState with Error state holds correct error details`() {
        // Arrange
        val errorState = DFComponentState.Error(
            message = "Network error occurred",
            errorType = ErrorType.NETWORK,
            feature = "testFeature",
            dfErrorCode = DFErrorCode.NETWORK_ERROR
        )

        // Act
        val event = DFInstallationMonitoringEvent.UpdateUiState(errorState)

        // Assert
        assertTrue(event.state is DFComponentState.Error)
        val actualErrorState = event.state as DFComponentState.Error
        assertEquals("Network error occurred", actualErrorState.message)
        assertEquals(ErrorType.NETWORK, actualErrorState.errorType)
        assertEquals("testFeature", actualErrorState.feature)
        assertEquals(DFErrorCode.NETWORK_ERROR, actualErrorState.dfErrorCode)
    }

    @Test
    fun `StorePendingConfirmation holds correct SplitInstallSessionState`() {
        // Arrange
        val mockSessionState = mockk<SplitInstallSessionState>() // Using MockK to create a mock instance

        // Act
        val event = DFInstallationMonitoringEvent.StorePendingConfirmation(mockSessionState)

        // Assert
        assertEquals(mockSessionState, event.sessionState)
    }

    @Test
    fun `ClearPendingConfirmation is a distinct object`() {
        // Arrange & Act
        val event = DFInstallationMonitoringEvent.ClearPendingConfirmation

        // Assert
        assertNotNull(event) // Basic check to ensure it's a valid object
        assertTrue(event is DFInstallationMonitoringEvent.ClearPendingConfirmation)
    }

    @Test
    fun `TriggerPostInstallSteps is a distinct object`() {
        // Arrange & Act
        val event = DFInstallationMonitoringEvent.TriggerPostInstallSteps

        // Assert
        assertNotNull(event) // Basic check
        assertTrue(event is DFInstallationMonitoringEvent.TriggerPostInstallSteps)
    }

    @Test
    fun `InstallationFailedTerminal holds correct errorState`() {
        // Arrange
        val errorState = DFComponentState.Error(
            message = "Installation failed critically",
            errorType = ErrorType.INSTALLATION,
            feature = "criticalFeature",
            dfErrorCode = DFErrorCode.INTERNAL_ERROR
        )

        // Act
        val event = DFInstallationMonitoringEvent.InstallationFailedTerminal(errorState)

        // Assert
        assertEquals(errorState, event.errorState)
        assertEquals("Installation failed critically", event.errorState.message)
        assertEquals(ErrorType.INSTALLATION, event.errorState.errorType)
        assertEquals("criticalFeature", event.errorState.feature)
        assertEquals(DFErrorCode.INTERNAL_ERROR, event.errorState.dfErrorCode)
    }

    @Test
    fun `InstallationCancelledTerminal is a distinct object`() {
        // Arrange & Act
        val event = DFInstallationMonitoringEvent.InstallationCancelledTerminal

        // Assert
        assertNotNull(event) // Basic check
        assertTrue(event is DFInstallationMonitoringEvent.InstallationCancelledTerminal)
    }
}
