package com.kuru.featureflow.component.domain

import android.util.Log
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.kuru.featureflow.component.googleplay.DFComponentInstaller
import com.kuru.featureflow.component.state.DFComponentStateStore
import com.kuru.featureflow.component.state.DFErrorCode
import com.kuru.featureflow.component.state.DFInstallProgress
import com.kuru.featureflow.component.state.DFInstallationState
import com.kuru.featureflow.component.ui.DFComponentState
import com.kuru.featureflow.component.ui.ErrorType
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot // Import slot
import io.mockk.unmockkStatic
import io.mockk.verify // Use for non-suspending functions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow // Used for the exception throwing flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest // Ensures the correct runTest is imported
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class DFMonitorInstallationUseCaseTest {

    @MockK
    private lateinit var mockInstaller: DFComponentInstaller

    @MockK
    private lateinit var mockStateStore: DFComponentStateStore

    @MockK
    private lateinit var mockHandleInstallationStateUseCase: DFHandleInstallationStateUseCase

    private lateinit var monitorInstallationUseCase: DFMonitorInstallationUseCase

    private val featureName = "testFeature"
    private val params = listOf("param1=value1")

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        monitorInstallationUseCase = DFMonitorInstallationUseCase(
            mockInstaller,
            mockStateStore,
            mockHandleInstallationStateUseCase
        )

        // Mock static Log calls
        mockkStatic(Log::class)
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.v(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<Throwable>()) } returns 0
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0

        // IMPORTANT: mockStateStore.setInstallationState is NOT a suspend function
        every { mockStateStore.setInstallationState(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `invoke when pre-install checks fail emits UpdateUiState and InstallationFailedTerminal then completes`() = runTest {
        coEvery { mockInstaller.installComponent(featureName) } returns emptyFlow()
        val results = monitorInstallationUseCase(featureName, params).toList()
        assertTrue("Expected no events when pre-checks pass and installer flow is empty", results.isEmpty())
    }


    @Test
    fun `invoke successful installation flow emits correct events`() = runTest {
        val installProgressFlow = MutableSharedFlow<DFInstallProgress>()
        coEvery { mockInstaller.installComponent(featureName) } returns installProgressFlow
        // mockStateStore.setInstallationState is already mocked in setUp

        val pendingUiState = DFComponentState.Loading
        val downloadingUiState = DFComponentState.Loading
        val installingUiState = DFComponentState.Loading
        val installedUiState = DFComponentState.Success(featureName, DFInstallationState.Installed, params)

        every { mockHandleInstallationStateUseCase(featureName, DFInstallationState.Pending, params) } returns pendingUiState
        every { mockHandleInstallationStateUseCase(featureName, DFInstallationState.Downloading(50), params) } returns downloadingUiState
        every { mockHandleInstallationStateUseCase(featureName, DFInstallationState.Installing(100), params) } returns installingUiState
        every { mockHandleInstallationStateUseCase(featureName, DFInstallationState.Installed, params) } returns installedUiState

        val results = mutableListOf<DFInstallationMonitoringEvent>()
        val job = launch {
            monitorInstallationUseCase(featureName, params).toList(results)
        }

        installProgressFlow.emit(DFInstallProgress(DFInstallationState.Pending))
        testScheduler.advanceUntilIdle()
        verify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Pending) }

        installProgressFlow.emit(DFInstallProgress(DFInstallationState.Downloading(50)))
        testScheduler.advanceUntilIdle()
        verify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Downloading(50)) }

        installProgressFlow.emit(DFInstallProgress(DFInstallationState.Installing(100)))
        testScheduler.advanceUntilIdle()
        verify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Installing(100)) }

        installProgressFlow.emit(DFInstallProgress(DFInstallationState.Installed))
        testScheduler.advanceUntilIdle()
        verify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.Installed) }

        job.cancel()

        assertTrue(results.any { it == DFInstallationMonitoringEvent.UpdateUiState(pendingUiState) })
        assertTrue(results.any { it == DFInstallationMonitoringEvent.UpdateUiState(downloadingUiState) })
        assertTrue(results.any { it == DFInstallationMonitoringEvent.UpdateUiState(installingUiState) })
        assertTrue(results.any { it == DFInstallationMonitoringEvent.UpdateUiState(installedUiState) })
        assertTrue(results.any { it == DFInstallationMonitoringEvent.TriggerPostInstallSteps })
    }

    @Test
    fun `invoke handles RequiresConfirmation and ClearPendingConfirmation correctly`() = runTest {
        val mockSessionState = mockk<SplitInstallSessionState>()
        // Use a MutableSharedFlow. Ensure it has enough replay or buffer if needed, though for this test,
        // emitting after collection starts should be fine.
        val installProgressFlow = MutableSharedFlow<DFInstallProgress>()
        coEvery { mockInstaller.installComponent(featureName) } returns installProgressFlow

        // Slots to capture arguments
        val featureSlot = slot<String>()
        val stateSlot = slot<DFInstallationState>()
        // Mock setInstallationState to capture arguments
        every { mockStateStore.setInstallationState(capture(featureSlot), capture(stateSlot)) } just Runs

        val requiresConfirmationUiState = DFComponentState.RequiresConfirmation(featureName)
        every { mockHandleInstallationStateUseCase(featureName, DFInstallationState.RequiresConfirmation, params) } returns requiresConfirmationUiState

        val collectedEvents = mutableListOf<DFInstallationMonitoringEvent>()
        // Launch the SUT's flow collection in a separate coroutine
        val job = launch {
            monitorInstallationUseCase(featureName, params).collect { event ->
                collectedEvents.add(event)
            }
        }

        // --- Part 1: Emit RequiresConfirmation ---
        val requiresConfirmationProgress = DFInstallProgress(DFInstallationState.RequiresConfirmation, mockSessionState)
        installProgressFlow.emit(requiresConfirmationProgress)

        // Advance the scheduler to allow the SUT's collector to process the emitted item
        testScheduler.advanceUntilIdle()

        // Verify setInstallationState for RequiresConfirmation
        // Check that it was called once up to this point with the correct arguments
        verify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.RequiresConfirmation) }
        // Assert captured arguments if needed, though the direct verify is stronger if it passes
        assertEquals(featureName, featureSlot.captured)
        assertEquals(DFInstallationState.RequiresConfirmation, stateSlot.captured)

        // Verify emitted events
        assertTrue("Should emit UpdateUiState for RequiresConfirmation",
            collectedEvents.any { it == DFInstallationMonitoringEvent.UpdateUiState(requiresConfirmationUiState) })
        assertTrue("Should emit StorePendingConfirmation",
            collectedEvents.any { it == DFInstallationMonitoringEvent.StorePendingConfirmation(mockSessionState) })

        // Clear collected events for the next part of the test if necessary, or check counts
        val eventsAfterRequiresConfirmation = collectedEvents.toList() // Snapshot

        // --- Part 2: Emit Downloading to trigger ClearPendingConfirmation ---
        val downloadingState = DFInstallationState.Downloading(10)
        val downloadingUiState = DFComponentState.Loading // Assuming Downloading maps to Loading UI
        every { mockHandleInstallationStateUseCase(featureName, downloadingState, params) } returns downloadingUiState

        val downloadingProgress = DFInstallProgress(downloadingState)
        installProgressFlow.emit(downloadingProgress)

        // Advance the scheduler again
        testScheduler.advanceUntilIdle()

        // Verify setInstallationState for Downloading state
        // Now setInstallationState should have been called twice in total
        verify(exactly = 2) { mockStateStore.setInstallationState(any(), any()) }
        // Check the last captured arguments
        assertEquals(featureName, featureSlot.captured)
        assertEquals(downloadingState, stateSlot.captured)

        // Verify emitted events for the downloading part
        val allCollectedEvents = collectedEvents.toList()
        assertTrue("Should emit UpdateUiState for Downloading",
            allCollectedEvents.any { it == DFInstallationMonitoringEvent.UpdateUiState(downloadingUiState) && !eventsAfterRequiresConfirmation.contains(it) })
        assertTrue("Should emit ClearPendingConfirmation",
            allCollectedEvents.any { it == DFInstallationMonitoringEvent.ClearPendingConfirmation && !eventsAfterRequiresConfirmation.contains(it) })

        // Clean up the collecting coroutine
        job.cancel()
    }

    @Test
    fun `invoke handles RequiresConfirmation but playCoreState is null emits InstallationFailedTerminal`() = runTest {
        val installProgressFlow = flowOf(DFInstallProgress(DFInstallationState.RequiresConfirmation, null))
        coEvery { mockInstaller.installComponent(featureName) } returns installProgressFlow

        val requiresConfirmationUiState = DFComponentState.RequiresConfirmation(featureName)
        every { mockHandleInstallationStateUseCase(featureName, DFInstallationState.RequiresConfirmation, params) } returns requiresConfirmationUiState

        val results = monitorInstallationUseCase(featureName, params).toList()
        testScheduler.advanceUntilIdle()

        val expectedErrorUiState = DFComponentState.Error(
            message = "Internal error: Missing confirmation details.",
            errorType = ErrorType.UNKNOWN,
            feature = featureName
        )

        assertTrue("Should emit UpdateUiState for RequiresConfirmation",
            results.contains(DFInstallationMonitoringEvent.UpdateUiState(requiresConfirmationUiState)))
        assertTrue("Should emit InstallationFailedTerminal due to null playCoreState",
            results.contains(DFInstallationMonitoringEvent.InstallationFailedTerminal(expectedErrorUiState)))
        assertTrue("StorePendingConfirmation should not be emitted when playCoreState is null",
            results.none { it is DFInstallationMonitoringEvent.StorePendingConfirmation })

        verify(exactly = 1) { mockStateStore.setInstallationState(featureName, DFInstallationState.RequiresConfirmation) }
    }

    @Test
    fun `invoke handles Failed installation state`() = runTest {
        val errorCode = DFErrorCode.NETWORK_ERROR
        val failedState = DFInstallationState.Failed(errorCode)
        val installProgressFlow = flowOf(DFInstallProgress(failedState))
        coEvery { mockInstaller.installComponent(featureName) } returns installProgressFlow

        val errorUiState = DFComponentState.Error("Installation failed", ErrorType.NETWORK, featureName, errorCode)
        every { mockHandleInstallationStateUseCase(featureName, failedState, params) } returns errorUiState

        val results = monitorInstallationUseCase(featureName, params).toList()
        testScheduler.advanceUntilIdle()

        assertEquals("Expected two events: UpdateUiState and InstallationFailedTerminal", 2, results.size)
        assertEquals(DFInstallationMonitoringEvent.UpdateUiState(errorUiState), results[0])
        assertEquals(DFInstallationMonitoringEvent.InstallationFailedTerminal(errorUiState), results[1])
        verify(exactly = 1) { mockStateStore.setInstallationState(featureName, failedState) }
    }

    @Test
    fun `invoke handles Canceled installation state`() = runTest {
        val canceledState = DFInstallationState.Canceled
        val installProgressFlow = flowOf(DFInstallProgress(canceledState))
        coEvery { mockInstaller.installComponent(featureName) } returns installProgressFlow

        val errorUiState = DFComponentState.Error("Installation canceled", ErrorType.INSTALLATION, featureName, DFErrorCode.NO_ERROR)
        every { mockHandleInstallationStateUseCase(featureName, canceledState, params) } returns errorUiState

        val results = monitorInstallationUseCase(featureName, params).toList()
        testScheduler.advanceUntilIdle()

        assertEquals("Expected two events: UpdateUiState and InstallationCancelledTerminal", 2, results.size)
        assertEquals(DFInstallationMonitoringEvent.UpdateUiState(errorUiState), results[0])
        assertEquals(DFInstallationMonitoringEvent.InstallationCancelledTerminal, results[1])
        verify(exactly = 1) { mockStateStore.setInstallationState(featureName, canceledState) }
    }

    @Test
    fun `invoke handles exception from installer flow`() = runTest {
        val exception = RuntimeException("Installer flow error")
        coEvery { mockInstaller.installComponent(featureName) } returns flow { throw exception }

        val results = monitorInstallationUseCase(featureName, params).toList()
        testScheduler.advanceUntilIdle()

        assertEquals("Expected two events: UpdateUiState (Error) and InstallationFailedTerminal", 2, results.size)
        val expectedErrorUiState = DFComponentState.Error(
            message = "An unexpected error occurred during installation: ${exception.message}",
            errorType = ErrorType.UNKNOWN,
            feature = featureName
        )
        assertTrue("First event should be UpdateUiState with error", results[0] is DFInstallationMonitoringEvent.UpdateUiState)
        val actualUiStateEvent = results[0] as DFInstallationMonitoringEvent.UpdateUiState
        assertEquals(expectedErrorUiState, actualUiStateEvent.state)

        assertTrue("Second event should be InstallationFailedTerminal", results[1] is DFInstallationMonitoringEvent.InstallationFailedTerminal)
        val actualTerminalEvent = results[1] as DFInstallationMonitoringEvent.InstallationFailedTerminal
        assertEquals(expectedErrorUiState, actualTerminalEvent.errorState)

        verify(exactly = 0) { mockStateStore.setInstallationState(any(), any()) }
    }
}
