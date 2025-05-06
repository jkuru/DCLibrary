package com.kuru.featureflow.component.domain

import android.util.Log
import com.kuru.featureflow.component.state.DFErrorCode
import com.kuru.featureflow.component.state.DFInstallationState
import com.kuru.featureflow.component.ui.DFComponentState
import com.kuru.featureflow.component.ui.ErrorType
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DFHandleErrorUseCaseTest {

    private lateinit var useCase: DFHandleErrorUseCase

    @Before
    fun setUp() {
        // Mock Log class to capture logging calls
        // Mock logging to avoid issues with Log calls
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

        // Initialize use case
        useCase = DFHandleErrorUseCase()
    }

    @Test
    fun `invoke with known feature returns Failed state and logs debug message`() {
        // Arrange
        val feature = "test_feature"
        val currentFeature = "current_feature"
        val errorType = ErrorType.NETWORK
        val message = "Network error occurred"
        val dfErrorCode = DFErrorCode.NETWORK_ERROR

        // Act
        val result = useCase.invoke(
            feature = feature,
            currentFeature = currentFeature,
            errorType = errorType,
            message = message,
            dfErrorCode = dfErrorCode
        )

        // Assert
        assertIs<DFComponentState.Error>(result.uiErrorState)
        assertEquals(message, result.uiErrorState.message)
        assertEquals(errorType, result.uiErrorState.errorType)
        assertEquals(feature, result.uiErrorState.feature)
        assertEquals(dfErrorCode, result.uiErrorState.dfErrorCode)

        assertIs<DFInstallationState.Failed>(result.installationStateToStore)
        assertEquals(dfErrorCode, result.installationStateToStore?.errorCode)

        verify {
            Log.d("HandleErrorUseCase", "Error associated with known feature '$feature'. Preparing Failed state to store.")
        }
    }

    @Test
    fun `invoke with null feature uses currentFeature and does not store Failed state`() {
        // Arrange
        val feature: String? = null
        val currentFeature = "current_feature"
        val errorType = ErrorType.STORAGE
        val message = "Storage error occurred"
        val dfErrorCode = DFErrorCode.INSUFFICIENT_STORAGE

        // Act
        val result = useCase.invoke(
            feature = feature,
            currentFeature = currentFeature,
            errorType = errorType,
            message = message,
            dfErrorCode = dfErrorCode
        )

        // Assert
        assertIs<DFComponentState.Error>(result.uiErrorState)
        assertEquals(message, result.uiErrorState.message)
        assertEquals(errorType, result.uiErrorState.errorType)
        assertEquals(currentFeature, result.uiErrorState.feature)
        assertEquals(dfErrorCode, result.uiErrorState.dfErrorCode)

        assertNull(result.installationStateToStore)

        verify {
            Log.w("HandleErrorUseCase", "Error not associated with a specific feature ('feature' param was null). Won't store Failed installation state.")
        }
    }

    @Test
    fun `invoke with null feature and currentFeature uses unknown and does not store Failed state`() {
        // Arrange
        val feature: String? = null
        val currentFeature: String? = null
        val errorType = ErrorType.UNKNOWN
        val message = "Unknown error occurred"
        val dfErrorCode = DFErrorCode.UNKNOWN_ERROR

        // Act
        val result = useCase.invoke(
            feature = feature,
            currentFeature = currentFeature,
            errorType = errorType,
            message = message,
            dfErrorCode = dfErrorCode
        )

        // Assert
        assertIs<DFComponentState.Error>(result.uiErrorState)
        assertEquals(message, result.uiErrorState.message)
        assertEquals(errorType, result.uiErrorState.errorType)
        assertEquals("unknown", result.uiErrorState.feature)
        assertEquals(dfErrorCode, result.uiErrorState.dfErrorCode)

        assertNull(result.installationStateToStore)

        verify {
            Log.w("HandleErrorUseCase", "Error not associated with a specific feature ('feature' param was null). Won't store Failed installation state.")
        }
    }

    @Test
    fun `invoke with null dfErrorCode uses UNKNOWN_ERROR`() {
        // Arrange
        val feature = "test_feature"
        val currentFeature = "current_feature"
        val errorType = ErrorType.INSTALLATION
        val message = "Installation error occurred"
        val dfErrorCode: DFErrorCode? = null

        // Act
        val result = useCase.invoke(
            feature = feature,
            currentFeature = currentFeature,
            errorType = errorType,
            message = message,
            dfErrorCode = dfErrorCode
        )

        // Assert
        assertIs<DFComponentState.Error>(result.uiErrorState)
        assertEquals(message, result.uiErrorState.message)
        assertEquals(errorType, result.uiErrorState.errorType)
        assertEquals(feature, result.uiErrorState.feature)
        assertEquals(DFErrorCode.UNKNOWN_ERROR, result.uiErrorState.dfErrorCode)

        assertIs<DFInstallationState.Failed>(result.installationStateToStore)
        assertEquals(DFErrorCode.UNKNOWN_ERROR, result.installationStateToStore?.errorCode)

        verify {
            Log.d("HandleErrorUseCase", "Error associated with known feature '$feature'. Preparing Failed state to store.")
        }
    }
}