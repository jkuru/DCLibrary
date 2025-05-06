package com.kuru.featureflow.component.domain

import com.kuru.featureflow.component.state.DFErrorCode
import com.kuru.featureflow.component.state.DFInstallationState
import com.kuru.featureflow.component.ui.DFComponentState
import com.kuru.featureflow.component.ui.ErrorType
import io.mockk.core.ValueClassSupport.boxedValue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DFHandleInstallationStateUseCaseTest {

    private lateinit var useCase: DFHandleInstallationStateUseCase

    @Before
    fun setUp() {
        // Initialize use case
        useCase = DFHandleInstallationStateUseCase()
    }

    @Test
    fun `invoke with NotInstalled returns Loading state`() {
        // Arrange
        val feature = "test_feature"
        val installationState = DFInstallationState.NotInstalled
        val params = listOf("param1", "param2")

        // Act
        val result = useCase.invoke(feature, installationState, params)

        // Assert
        assertIs<DFComponentState.Loading>(result)
    }

    @Test
    fun `invoke with Pending returns Loading state`() {
        // Arrange
        val feature = "test_feature"
        val installationState = DFInstallationState.Pending
        val params = emptyList<String>()

        // Act
        val result = useCase.invoke(feature, installationState, params)

        // Assert
        assertIs<DFComponentState.Loading>(result)
    }

    @Test
    fun `invoke with Downloading returns Loading state`() {
        // Arrange
        val feature = "test_feature"
        val installationState = DFInstallationState.Downloading(progress = 50)
        val params = listOf("param1")

        // Act
        val result = useCase.invoke(feature, installationState, params)

        // Assert
        assertIs<DFComponentState.Loading>(result)
    }

    @Test
    fun `invoke with Installing returns Loading state`() {
        // Arrange
        val feature = "test_feature"
        val installationState = DFInstallationState.Installing(progress = 75)
        val params = emptyList<String>()

        // Act
        val result = useCase.invoke(feature, installationState, params)

        // Assert
        assertIs<DFComponentState.Loading>(result)
    }

    @Test
    fun `invoke with Canceling returns Loading state`() {
        // Arrange
        val feature = "test_feature"
        val installationState = DFInstallationState.Canceling
        val params = listOf("param1", "param2")

        // Act
        val result = useCase.invoke(feature, installationState, params)

        // Assert
        assertIs<DFComponentState.Loading>(result)
    }

    @Test
    fun `invoke with Installed returns Success state with correct params`() {
        // Arrange
        val feature = "test_feature"
        val installationState = DFInstallationState.Installed
        val params = listOf("param1", "param2")

        // Act
        val result = useCase.invoke(feature, installationState, params)

        // Assert
        assertIs<DFComponentState.Success>(result)
        assertEquals(feature, result.feature)
        assertEquals(installationState, result.featureInstallationState)
        assertEquals(params, result.params)
    }

    @Test
    fun `invoke with Failed returns Error state with mapped error type`() {
        // Arrange
        val feature = "test_feature"
        val errorCode = DFErrorCode.NETWORK_ERROR
        val installationState = DFInstallationState.Failed(errorCode)
        val params = emptyList<String>()

        // Act
        val result = useCase.invoke(feature, installationState, params)

        // Assert
        assertIs<DFComponentState.Error>(result)
        assertEquals("Installation failed (Code: ${errorCode.name})", result.message)
        assertEquals(ErrorType.NETWORK, result.errorType)
        assertEquals(feature, result.feature)
        assertEquals(errorCode, result.dfErrorCode)
    }

    @Test
    fun `invoke with RequiresConfirmation returns RequiresConfirmation state`() {
        // Arrange
        val feature = "test_feature"
        val installationState = DFInstallationState.RequiresConfirmation
        val params = listOf("param1")

        // Act
        val result = useCase.invoke(feature, installationState, params)

        // Assert
        assertIs<DFComponentState.RequiresConfirmation>(result)
        assertEquals(feature, result.feature)
    }

    @Test
    fun `invoke with Canceled returns Error state with INSTALLATION error type`() {
        // Arrange
        val feature = "test_feature"
        val installationState = DFInstallationState.Canceled
        val params = emptyList<String>()

        // Act
        val result = useCase.invoke(feature, installationState, params)

        // Assert
        assertIs<DFComponentState.Error>(result)
        assertEquals("Installation canceled by user or system.", result.message)
        assertEquals(ErrorType.INSTALLATION, result.errorType)
        assertEquals(feature, result.feature)
        assertEquals(DFErrorCode.NO_ERROR, result.dfErrorCode)
    }

    @Test
    fun `invoke with Unknown returns Error state with UNKNOWN error type`() {
        // Arrange
        val feature = "test_feature"
        val installationState = DFInstallationState.Unknown
        val params = listOf("param1", "param2")

        // Act
        val result = useCase.invoke(feature, installationState, params)

        // Assert
        assertIs<DFComponentState.Error>(result)
        assertEquals("Unknown installation state encountered.", result.message)
        assertEquals(ErrorType.UNKNOWN, result.errorType)
        assertEquals(feature, result.feature)
        assertEquals(DFErrorCode.UNKNOWN_ERROR, result.dfErrorCode)
    }

    @Test
    fun `mapDfErrorCodeToErrorType maps NETWORK_ERROR to NETWORK`() {
        // Arrange
        val errorCode = DFErrorCode.NETWORK_ERROR

        // Act
        val result = useCase.invoke("test_feature", DFInstallationState.Failed(errorCode))
        val errorState = result as DFComponentState.Error
        // Assert
        assertEquals(ErrorType.NETWORK.boxedValue, errorState.errorType)
    }

    @Test
    fun `mapDfErrorCodeToErrorType maps INSUFFICIENT_STORAGE to STORAGE`() {
        // Arrange
        val errorCode = DFErrorCode.INSUFFICIENT_STORAGE

        // Act
        val result = useCase.invoke("test_feature", DFInstallationState.Failed(errorCode))
        val errorState = result as DFComponentState.Error

        // Assert
        assertEquals(ErrorType.STORAGE.boxedValue, errorState.errorType)
    }

    @Test
    fun `mapDfErrorCodeToErrorType maps API_NOT_AVAILABLE to INSTALLATION`() {
        // Arrange
        val errorCode = DFErrorCode.API_NOT_AVAILABLE

        // Act
        val result = useCase.invoke("test_feature", DFInstallationState.Failed(errorCode))
        val errorState = result as DFComponentState.Error
        // Assert
        assertEquals(ErrorType.INSTALLATION.boxedValue, errorState.errorType)
    }

    @Test
    fun `mapDfErrorCodeToErrorType maps INTERNAL_ERROR to UNKNOWN`() {
        // Arrange
        val errorCode = DFErrorCode.INTERNAL_ERROR

        // Act
        val result = useCase.invoke("test_feature", DFInstallationState.Failed(errorCode))

        val errorState = result as DFComponentState.Error
        // Assert
        assertEquals(ErrorType.UNKNOWN.boxedValue,errorState.errorType )
    }
}