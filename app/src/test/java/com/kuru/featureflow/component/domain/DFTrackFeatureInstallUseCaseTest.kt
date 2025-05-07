package com.kuru.featureflow.component.domain

import android.util.Log
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.kuru.featureflow.component.state.DFErrorCode
import com.kuru.featureflow.component.state.DFFeatureError
import com.kuru.featureflow.component.state.DFFeatureInstallProgress
import com.kuru.featureflow.component.state.DFInstallationMonitoringState
import com.kuru.featureflow.component.state.DFInstallationState
import com.kuru.featureflow.component.state.DFStateStore
import com.kuru.featureflow.component.ui.DFComponentState
import com.kuru.featureflow.component.ui.ErrorType
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.*

@Ignore("TODO: Implement tests")
@OptIn(ExperimentalCoroutinesApi::class)
class DFTrackFeatureInstallUseCaseTest {

    @MockK
    lateinit var mockInstaller: DFFeatureInstaller

    @MockK(relaxUnitFun = true) // Relaxed as setInstallationState returns Unit implicitly
    lateinit var mockStateStore: DFStateStore

    private lateinit var useCase: DFTrackFeatureInstallUseCase

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val featureName = "testFeature"
    private val params = listOf("param1=value1")

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        // Use 'open' class constructor or create a subclass for testing private methods if needed,
        // but prefer testing through public API. For 'handleError' (internal), we can call it directly.
        useCase = DFTrackFeatureInstallUseCase(mockInstaller, mockStateStore)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.v(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // --- mapDfErrorCodeToErrorType Tests ---
    @Test
    fun `mapDfErrorCodeToErrorType - maps NETWORK_ERROR correctly`() {
        assertEquals(ErrorType.NETWORK, useCase.mapDfErrorCodeToErrorType(DFErrorCode.NETWORK_ERROR))
    }

    @Test
    fun `mapDfErrorCodeToErrorType - maps INSUFFICIENT_STORAGE correctly`() {
        assertEquals(ErrorType.STORAGE, useCase.mapDfErrorCodeToErrorType(DFErrorCode.INSUFFICIENT_STORAGE))
    }

    @Test
    fun `mapDfErrorCodeToErrorType - maps various installation errors correctly`() {
        assertEquals(ErrorType.INSTALLATION, useCase.mapDfErrorCodeToErrorType(DFErrorCode.API_NOT_AVAILABLE))
        assertEquals(ErrorType.INSTALLATION, useCase.mapDfErrorCodeToErrorType(DFErrorCode.MODULE_UNAVAILABLE))
        // ... add more installation codes if needed ...
    }

    @Test
    fun `mapDfErrorCodeToErrorType - maps UNKNOWN_ERROR correctly`() {
        assertEquals(ErrorType.UNKNOWN, useCase.mapDfErrorCodeToErrorType(DFErrorCode.UNKNOWN_ERROR))
        assertEquals(ErrorType.UNKNOWN, useCase.mapDfErrorCodeToErrorType(DFErrorCode.INTERNAL_ERROR))
        assertEquals(ErrorType.UNKNOWN, useCase.mapDfErrorCodeToErrorType(DFErrorCode.NO_ERROR)) // Maps NO_ERROR to UNKNOWN
    }

    // --- handleError Tests (internal function, test directly) ---
    @Test
    fun `handleError - with feature name - returns DFFeatureError with stateToStore`() {
        val feature = "featA"
        val current = "featA"
        val errorType = ErrorType.NETWORK
        val message = "Network unavailable"
        val dfErrorCode = DFErrorCode.NETWORK_ERROR

        val result = useCase.handleError(feature, current, errorType, message, dfErrorCode)

        assertIs<DFComponentState.Error>(result.uiErrorState)
        assertEquals(message, result.uiErrorState.message)
        assertEquals(errorType, result.uiErrorState.errorType)
        assertEquals(feature, result.uiErrorState.feature)
        assertEquals(dfErrorCode, result.uiErrorState.dfErrorCode)

        assertNotNull(result.installationStateToStore)
        assertEquals(dfErrorCode, result.installationStateToStore?.errorCode)
    }

    @Test
    fun `handleError - without feature name but with currentFeature - uses currentFeature`() {
        val feature = null
        val current = "currentFeat"
        val errorType = ErrorType.STORAGE
        val message = "Out of space"
        val dfErrorCode = DFErrorCode.INSUFFICIENT_STORAGE

        val result = useCase.handleError(feature, current, errorType, message, dfErrorCode)

        assertIs<DFComponentState.Error>(result.uiErrorState)
        assertEquals(message, result.uiErrorState.message)
        assertEquals(errorType, result.uiErrorState.errorType)
        assertEquals(current, result.uiErrorState.feature) // Uses currentFeature
        assertEquals(dfErrorCode, result.uiErrorState.dfErrorCode)

        assertNull(result.installationStateToStore, "Should not create stateToStore if feature is null")
    }
    @Test
    fun `handleError - without feature name or currentFeature - uses 'unknown'`() {
        val feature = null
        val current = null
        val errorType = ErrorType.UNKNOWN
        val message = "Something weird"
        // dfErrorCode defaults to UNKNOWN_ERROR if null is passed

        val result = useCase.handleError(feature, current, errorType, message, null)

        assertIs<DFComponentState.Error>(result.uiErrorState)
        assertEquals(message, result.uiErrorState.message)
        assertEquals(errorType, result.uiErrorState.errorType)
        assertEquals("unknown", result.uiErrorState.feature) // Uses "unknown"
        assertEquals(DFErrorCode.UNKNOWN_ERROR, result.uiErrorState.dfErrorCode) // Defaults to UNKNOWN

        assertNull(result.installationStateToStore)
    }


    // --- invoke Operator Tests ---

    @Test
    fun `invoke - happy path - emits correct sequence including TriggerPostInstallSteps`() = testScope.runTest {
        // Arrange
        val flowEvents = listOf(
            DFFeatureInstallProgress(DFInstallationState.Pending),
            DFFeatureInstallProgress(DFInstallationState.Downloading(50)),
            DFFeatureInstallProgress(DFInstallationState.Installing(100)),
            DFFeatureInstallProgress(DFInstallationState.Installed)
        )
        val installFlow = flowOf(*flowEvents.toTypedArray())
        coEvery { mockInstaller.installFeature(featureName) } returns installFlow

        // Act
        val result = useCase(featureName, params).toList()

        // Assert
        assertEquals(5, result.size) // UpdateUiState for each + TriggerPostInstallSteps

        // Check UpdateUiState events
        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[0])
        assertIs<DFComponentState.Loading>(result[0]) // Pending maps to Loading

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[1])
        assertIs<DFComponentState.Loading>(result[1]) // Downloading maps to Loading

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[2])
        assertIs<DFComponentState.Loading>(result[2]) // Installing maps to Loading

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[3])
        assertIs<DFComponentState.Success>(result[3]) // Installed maps to Success
        assertEquals(featureName, (result[3] as DFComponentState.Success).feature)
        assertEquals(params, (result[3] as DFComponentState.Success).params)
        assertIs<DFInstallationState.Installed>((result[3] as DFComponentState.Success).featureInstallationState)

        // Check final event
        assertIs<DFInstallationMonitoringState.TriggerPostInstallSteps>(result[4])

        // Verify state store interactions
        coVerify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Pending) }
        coVerify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Downloading(50)) }
        coVerify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Installing(100)) }
        coVerify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Installed) }
    }

    @Test
    fun `invoke - install fails - emits UpdateUiState(Error) and InstallationFailedTerminal`() = testScope.runTest {
        // Arrange
        val errorCode = DFErrorCode.NETWORK_ERROR
        val flowEvents = listOf(
            DFFeatureInstallProgress(DFInstallationState.Pending),
            DFFeatureInstallProgress(DFInstallationState.Failed(errorCode))
        )
        val installFlow = flowOf(*flowEvents.toTypedArray())
        coEvery { mockInstaller.installFeature(featureName) } returns installFlow

        // Act
        val result = useCase(featureName, params).toList()

        // Assert
        assertEquals(3, result.size) // UpdateUiState(Loading), UpdateUiState(Error), InstallationFailedTerminal

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[0])
        assertIs<DFComponentState.Loading>(result[0]) // Pending

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[1])
        assertIs<DFComponentState.Error>(result[1]) // Failed
        val errorUiState = result[1] as DFComponentState.Error
        assertEquals(ErrorType.NETWORK, errorUiState.errorType)
        assertEquals(featureName, errorUiState.feature)
        assertEquals(errorCode, errorUiState.dfErrorCode)
        assertTrue(errorUiState.message.contains("Installation failed"))

        assertIs<DFInstallationMonitoringState.InstallationFailedTerminal>(result[2])
        assertEquals(errorUiState, (result[2] as DFInstallationMonitoringState.InstallationFailedTerminal).errorState )// Terminal event contains the same error state

        // Verify state store interactions
        coVerify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Pending) }
        coVerify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Failed(errorCode)) }
    }

    @Test
    fun `invoke - install cancelled - emits UpdateUiState(Error) and InstallationCancelledTerminal`() = testScope.runTest {
        // Arrange
        val flowEvents = listOf(
            DFFeatureInstallProgress(DFInstallationState.Downloading(10)), // Simulate some progress
            DFFeatureInstallProgress(DFInstallationState.Canceling),
            DFFeatureInstallProgress(DFInstallationState.Canceled)
        )
        val installFlow = flowOf(*flowEvents.toTypedArray())
        coEvery { mockInstaller.installFeature(featureName) } returns installFlow

        // Act
        val result = useCase(featureName, params).toList()

        // Assert
        assertEquals(4, result.size) // Loading, Loading, Error, CancelledTerminal

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[0])
        assertIs<DFComponentState.Loading>(result[0]) // Downloading

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[1])
        assertIs<DFComponentState.Loading>(result[1]) // Canceling

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[2])
        assertIs<DFComponentState.Error>(result[2]) // Canceled maps to Error
        val errorUiState = result[2] as DFComponentState.Error
        assertEquals(ErrorType.INSTALLATION, errorUiState.errorType)
        assertEquals(featureName, errorUiState.feature)
        assertEquals(DFErrorCode.NO_ERROR, errorUiState.dfErrorCode) // Canceled uses NO_ERROR internally
        assertTrue(errorUiState.message.contains("canceled"))

        assertIs<DFInstallationMonitoringState.InstallationCancelledTerminal>(result[3])

        // Verify state store
        coVerify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Downloading(10)) }
        coVerify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Canceling) }
        coVerify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Canceled) }
    }


    @Test
    fun `invoke - requires confirmation - emits StorePendingConfirmation then ClearPendingConfirmation`() = testScope.runTest {
        // Arrange
        val mockPlayCoreState = mockk<SplitInstallSessionState>()
        val requiresConfirmationProgress = DFFeatureInstallProgress(
            DFInstallationState.RequiresConfirmation,
            mockPlayCoreState // Include playCoreState
        )
        val pendingProgress = DFFeatureInstallProgress(DFInstallationState.Pending) // Simulate confirmation accepted implicitly
        val installedProgress = DFFeatureInstallProgress(DFInstallationState.Installed)

        val flowEvents = listOf(requiresConfirmationProgress, pendingProgress, installedProgress)
        val installFlow = flowOf(*flowEvents.toTypedArray())
        coEvery { mockInstaller.installFeature(featureName) } returns installFlow

        // Act
        val result = useCase(featureName, params).toList()

        // Assert
        // RequiresConfirmation -> UpdateUiState(RequiresConfirmation), StorePendingConfirmation
        // Pending -> UpdateUiState(Loading), ClearPendingConfirmation
        // Installed -> UpdateUiState(Success), TriggerPostInstallSteps
        assertEquals(6, result.size)

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[0])
        assertIs<DFComponentState.RequiresConfirmation>(result[0])
        assertEquals(featureName, (result[0] as DFComponentState.RequiresConfirmation).feature)

        assertIs<DFInstallationMonitoringState.StorePendingConfirmation>(result[1])
        //assertEquals(mockPlayCoreState, (result[1] as DFComponentState.RequiresConfirmation))

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[2])
        assertIs<DFComponentState.Loading>(result[2]) // Pending

        assertIs<DFInstallationMonitoringState.ClearPendingConfirmation>(result[3])

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[4])
        assertIs<DFComponentState.Success>(result[4]) // Installed

        assertIs<DFInstallationMonitoringState.TriggerPostInstallSteps>(result[5])

        // Verify state store
        coVerify { mockStateStore.setInstallationState(featureName, DFInstallationState.RequiresConfirmation)}
        coVerify { mockStateStore.setInstallationState(featureName, DFInstallationState.Pending)}
        coVerify { mockStateStore.setInstallationState(featureName, DFInstallationState.Installed)}
    }

    @Test
    fun `invoke - requires confirmation but playCoreState is null - emits Failure`() = testScope.runTest {
        // Arrange
        val requiresConfirmationProgress = DFFeatureInstallProgress(
            DFInstallationState.RequiresConfirmation,
            null // Simulate missing playCoreState
        )

        val installFlow = flowOf(requiresConfirmationProgress) // Only emit this state
        coEvery { mockInstaller.installFeature(featureName) } returns installFlow

        // Act
        val result = useCase(featureName, params).toList()

        // Assert
        // RequiresConfirmation -> UpdateUiState(RequiresConfirmation), InstallationFailedTerminal
        assertEquals(2, result.size)

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[0])
        assertIs<DFComponentState.RequiresConfirmation>(result[0])

        assertIs<DFInstallationMonitoringState.InstallationFailedTerminal>(result[1])
        val errorState = result[1] as DFInstallationMonitoringState.InstallationFailedTerminal
        assertEquals(ErrorType.UNKNOWN.name, errorState.errorState.dfErrorCode?.name ?: "")
        assertEquals(featureName, errorState.errorState.feature)
        assertTrue(errorState.errorState.message.contains("Missing confirmation details"))

        // Verify state store (it still stores the RequiresConfirmation state before failing)
        coVerify { mockStateStore.setInstallationState(featureName, DFInstallationState.RequiresConfirmation)}
        // Verify it tries to store Failed state after the internal error
        coVerify { mockStateStore.setInstallationState(featureName, DFInstallationState.Failed(DFErrorCode.UNKNOWN_ERROR))}
    }


    @Test
    fun `invoke - installer flow throws exception - emits UpdateUiState(Error) and InstallationFailedTerminal`() = testScope.runTest {
        // Arrange
        val exception = IOException("Network failed during flow")
        val installFlow = flow<DFFeatureInstallProgress> { throw exception }
        coEvery { mockInstaller.installFeature(featureName) } returns installFlow

        // Act
        val result = useCase(featureName, params).toList()

        // Assert
        assertEquals(2, result.size) // UpdateUiState(Error), InstallationFailedTerminal

        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[0])
        assertIs<DFComponentState.Error>(result[0])
        val errorUiState = result[0] as DFComponentState.Error
        assertEquals(ErrorType.UNKNOWN, errorUiState.errorType) // From catch block
        assertEquals(featureName, errorUiState.feature)
        assertTrue(errorUiState.message.contains("An unexpected error occurred"))
        assertTrue(errorUiState.message.contains(exception.message.toString()))

        assertIs<DFInstallationMonitoringState.InstallationFailedTerminal>(result[1])
        assertEquals(errorUiState, (result[1] as DFInstallationMonitoringState.InstallationFailedTerminal).errorState)

        // Verify state store attempt for the final error
        coVerify { mockStateStore.setInstallationState(featureName, DFInstallationState.Failed(DFErrorCode.UNKNOWN_ERROR)) }
    }

    @Test
    fun `invoke - stateStore setInstallationState fails - logs error but continues`() = testScope.runTest {
        // Arrange
        val stateStoreException = RuntimeException("StateStore error")
        coEvery { mockStateStore.setInstallationState(any(), any()) } throws stateStoreException

        val flowEvents = listOf(
            DFFeatureInstallProgress(DFInstallationState.Pending),
            DFFeatureInstallProgress(DFInstallationState.Installed) // Will trigger second setInstallationState call
        )
        val installFlow = flowOf(*flowEvents.toTypedArray())
        coEvery { mockInstaller.installFeature(featureName) } returns installFlow

        // Act
        val result = useCase(featureName, params).toList()

        // Assert - Flow should still complete successfully based on installer events
        assertEquals(3, result.size) // UpdateUiState(Loading), UpdateUiState(Success), TriggerPostInstallSteps
        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[0])
        assertIs<DFComponentState.Loading>(result[0])
        assertIs<DFInstallationMonitoringState.UpdateUiState>(result[1])
        assertIs<DFComponentState.Success>(result[1])
        assertIs<DFInstallationMonitoringState.TriggerPostInstallSteps>(result[2])

        // Verify state store was called and error was logged
        coVerify(exactly = 2) { mockStateStore.setInstallationState(eq(featureName), any()) }
        verify { Log.e(any(), eq("Failed to update StateStore for $featureName with state ${DFInstallationState.Pending}"), eq(stateStoreException)) }
        verify { Log.e(any(), eq("Failed to update StateStore for $featureName with state ${DFInstallationState.Installed}"), eq(stateStoreException)) }
    }

    // Note: Testing runGenericPreInstallChecks=false path requires modifying the SUT or using reflection.
    // As it's hardcoded to true, we only test that path.
}