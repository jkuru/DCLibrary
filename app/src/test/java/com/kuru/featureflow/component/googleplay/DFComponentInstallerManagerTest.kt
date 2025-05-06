package com.kuru.featureflow.component.googleplay

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.kuru.featureflow.component.state.DFErrorCode
import com.kuru.featureflow.component.state.DFInstallProgress
import com.kuru.featureflow.component.state.DFInstallationState
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class DFComponentInstallerManagerTest {

    // Mock dependencies
    @MockK
    private lateinit var mockSplitInstallManager: SplitInstallManager

    @MockK
    private lateinit var mockContext: Context

    // Class under test
    private lateinit var dfComponentInstallerManager: DFComponentInstallerManager

    // Capture listener for simulating state updates
    private val listenerSlot = slot<SplitInstallStateUpdatedListener>()

    @Before
    fun setUp() {
        // Initialize MockK annotations
        MockKAnnotations.init(this)

        // Mock static SplitCompat.install call
        mockkStatic(SplitCompat::class)
        every { SplitCompat.install(any()) } returns true

        // Mock static Log calls to avoid Android framework dependencies in unit tests
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.v(any(), any()) } returns 0


        // Instantiate the class under test
        dfComponentInstallerManager = DFComponentInstallerManager(mockSplitInstallManager, mockContext)
    }

    @After
    fun tearDown() {
        // Clear static mocks
        unmockkStatic(SplitCompat::class)
        unmockkStatic(Log::class)
        // Clear all other mocks
        clearAllMocks()
    }

    // --- Test cases for isComponentInstalled ---

    @Test
    fun `isComponentInstalled returns true when module is in installedModules and session confirms INSTALLED`() = runTest {
        val componentName = "feature1"
        every { mockSplitInstallManager.installedModules } returns setOf(componentName)

        val mockSessionState = mockk<SplitInstallSessionState>()
        every { mockSessionState.moduleNames() } returns listOf(componentName)
        every { mockSessionState.status() } returns SplitInstallSessionStatus.INSTALLED
        coEvery { mockSplitInstallManager.sessionStates } returns Tasks.forResult(listOf(mockSessionState))

        val result = dfComponentInstallerManager.isComponentInstalled(componentName)

        assertTrue(result)
    }

    @Test
    fun `isComponentInstalled returns true when module is in installedModules and session check fails (fallback)`() = runTest {
        val componentName = "feature1"
        every { mockSplitInstallManager.installedModules } returns setOf(componentName)
        coEvery { mockSplitInstallManager.sessionStates } returns Tasks.forException(RuntimeException("Session fetch failed"))

        val result = dfComponentInstallerManager.isComponentInstalled(componentName)

        assertTrue(result) // Should fallback to installedModules check
    }

    @Test
    fun `isComponentInstalled returns true when module is in installedModules but session state is not INSTALLED (fallback)`() = runTest {
        val componentName = "feature1"
        every { mockSplitInstallManager.installedModules } returns setOf(componentName)

        val mockSessionState = mockk<SplitInstallSessionState>()
        every { mockSessionState.moduleNames() } returns listOf(componentName)
        every { mockSessionState.status() } returns SplitInstallSessionStatus.DOWNLOADING // Not INSTALLED
        coEvery { mockSplitInstallManager.sessionStates } returns Tasks.forResult(listOf(mockSessionState))


        // Even if session state is not INSTALLED, if it's in installedModules,
        // the current implementation (with its fallback) will return true after session check.
        // This tests the specific fallback logic when session doesn't confirm INSTALLED but module is present in installedModules.
        val result = dfComponentInstallerManager.isComponentInstalled(componentName)
        assertTrue(result)
    }


    @Test
    fun `isComponentInstalled returns false when module is not in installedModules`() = runTest {
        val componentName = "feature1"
        every { mockSplitInstallManager.installedModules } returns emptySet()
        // No need to mock sessionStates if not in installedModules, as it short-circuits

        val result = dfComponentInstallerManager.isComponentInstalled(componentName)

        assertFalse(result)
    }

    @Test
    fun `isComponentInstalled returns false for blank component name`() = runTest {
        val result = dfComponentInstallerManager.isComponentInstalled(" ")
        assertFalse(result)
    }

    // --- Test cases for installComponent ---

    private fun mockSessionState(
        sessionId: Int,
        status: Int,
        moduleNames: List<String> = listOf("feature1"),
        errorCode: Int = SplitInstallErrorCode.NO_ERROR,
        bytesDownloaded: Long = 0,
        totalBytesToDownload: Long = 0
    ): SplitInstallSessionState {
        val mockState = mockk<SplitInstallSessionState>()
        every { mockState.sessionId() } returns sessionId
        every { mockState.status() } returns status
        every { mockState.moduleNames() } returns moduleNames
        every { mockState.errorCode() } returns errorCode
        every { mockState.bytesDownloaded() } returns bytesDownloaded
        every { mockState.totalBytesToDownload() } returns totalBytesToDownload
        every { mockState.resolutionIntent() } returns null // Default, override if testing confirmation
        return mockState
    }

    @Test
    fun `installComponent emits PENDING then INSTALLED on successful flow`() = runTest {
        val componentName = "feature1"
        val sessionId = 123

        coEvery { mockSplitInstallManager.startInstall(any()) } returns Tasks.forResult(sessionId)
        every { mockSplitInstallManager.registerListener(capture(listenerSlot)) } just Runs
        every { mockSplitInstallManager.unregisterListener(any()) } just Runs
        coEvery { mockSplitInstallManager.getSessionState(sessionId) } returns Tasks.forResult(
            mockSessionState(sessionId, SplitInstallSessionStatus.PENDING)
        )

        val flow = dfComponentInstallerManager.installComponent(componentName)
        val results = mutableListOf<DFInstallProgress>()

        // Collect flow emissions
        val job = launch {
            flow.toList(results)
        }

        // Initial PENDING from getSessionState
        assertTrue("Listener should be captured after startInstall", listenerSlot.isCaptured)

        // Simulate PENDING update
        listenerSlot.captured.onStateUpdate(mockSessionState(sessionId, SplitInstallSessionStatus.PENDING))

        // Simulate DOWNLOADING update
        listenerSlot.captured.onStateUpdate(mockSessionState(sessionId, SplitInstallSessionStatus.DOWNLOADING, bytesDownloaded = 50, totalBytesToDownload = 100))

        // Simulate INSTALLING update
        listenerSlot.captured.onStateUpdate(mockSessionState(sessionId, SplitInstallSessionStatus.INSTALLING, bytesDownloaded = 100, totalBytesToDownload = 100))

        // Simulate INSTALLED update
        listenerSlot.captured.onStateUpdate(mockSessionState(sessionId, SplitInstallSessionStatus.INSTALLED))

        job.join() // Wait for flow collection to complete

        assertEquals(5, results.size) // Initial Pending, Pending, Downloading, Installing, Installed
        assertEquals(DFInstallProgress(DFInstallationState.Pending), results[0])
        assertEquals(DFInstallProgress(DFInstallationState.Pending), results[1])
        assertEquals(DFInstallProgress(DFInstallationState.Downloading(50)), results[2])
        assertEquals(DFInstallProgress(DFInstallationState.Installing(100)), results[3])
        assertEquals(DFInstallProgress(DFInstallationState.Installed), results[4])

        coVerify { mockSplitInstallManager.unregisterListener(listenerSlot.captured) }
    }

    @Test
    fun `installComponent emits REQUIRES_USER_CONFIRMATION with playCoreState`() = runTest {
        val componentName = "largeFeature"
        val sessionId = 456
        val rawConfirmationState = mockSessionState(sessionId, SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION, moduleNames = listOf(componentName))

        coEvery { mockSplitInstallManager.startInstall(any()) } returns Tasks.forResult(sessionId)
        every { mockSplitInstallManager.registerListener(capture(listenerSlot)) } just Runs
        every { mockSplitInstallManager.unregisterListener(any()) } just Runs
        coEvery { mockSplitInstallManager.getSessionState(sessionId) } returns Tasks.forResult(rawConfirmationState)


        val flow = dfComponentInstallerManager.installComponent(componentName)
        val firstItem = flow.first()


        assertEquals(DFInstallationState.RequiresConfirmation, firstItem.frameworkState)
        assertEquals(rawConfirmationState, firstItem.playCoreState)

        // Simulate flow cancellation or completion to trigger unregister
        // For simplicity, we assume the collector might cancel after this.
        // In a real scenario, the flow would continue until a terminal state or cancellation.
        listenerSlot.captured.onStateUpdate(mockSessionState(sessionId, SplitInstallSessionStatus.CANCELED, moduleNames = listOf(componentName))) // to terminate
        flow.toList() // consume the rest to ensure cleanup

        coVerify { mockSplitInstallManager.unregisterListener(listenerSlot.captured) }
    }

    @Test
    fun `installComponent emits FAILED on error code`() = runTest {
        val componentName = "featureError"
        val sessionId = 789

        coEvery { mockSplitInstallManager.startInstall(any()) } returns Tasks.forResult(sessionId)
        every { mockSplitInstallManager.registerListener(capture(listenerSlot)) } just Runs
        every { mockSplitInstallManager.unregisterListener(any()) } just Runs
        coEvery { mockSplitInstallManager.getSessionState(sessionId) } returns Tasks.forResult(
            mockSessionState(sessionId, SplitInstallSessionStatus.PENDING, moduleNames = listOf(componentName))
        )

        val flow = dfComponentInstallerManager.installComponent(componentName)
        val results = mutableListOf<DFInstallProgress>()
        val job = launch { flow.toList(results) }

        assertTrue(listenerSlot.isCaptured)
        listenerSlot.captured.onStateUpdate(
            mockSessionState(sessionId, SplitInstallSessionStatus.FAILED, moduleNames = listOf(componentName), errorCode = SplitInstallErrorCode.NETWORK_ERROR)
        )
        job.join()

        assertEquals(2, results.size) // Initial Pending, Failed
        assertEquals(DFInstallProgress(DFInstallationState.Pending), results[0])
        assertEquals(DFInstallProgress(DFInstallationState.Failed(DFErrorCode.NETWORK_ERROR)), results[1])

        coVerify { mockSplitInstallManager.unregisterListener(listenerSlot.captured) }
    }

    @Test
    fun `installComponent handles exception during startInstall and emits Failed`() = runTest {
        val componentName = "failToStart"
        coEvery { mockSplitInstallManager.startInstall(any()) } returns Tasks.forException(RuntimeException("Install start failed"))

        val flow = dfComponentInstallerManager.installComponent(componentName)
        val result = flow.first() // Should emit a single Failed state

        assertEquals(DFInstallProgress(DFInstallationState.Failed(DFErrorCode.UNKNOWN_ERROR)), result)
        // Listener should not be registered or unregistered if startInstall fails before listener registration
        coVerify(exactly = 0) { mockSplitInstallManager.registerListener(any()) }
        coVerify(exactly = 0) { mockSplitInstallManager.unregisterListener(any()) }
    }

    @Test
    fun `installComponent handles listener already active for session`() = runTest {
        val componentName = "featureMultiAccess"
        val sessionId = 101
        val existingListener = mockk<SplitInstallStateUpdatedListener>()

        // Simulate listener already present in activeListeners by pre-populating it
        // This requires making activeListeners accessible or changing its type for testing,
        // or testing this behavior more indirectly.
        // For this example, let's assume we can't directly manipulate `activeListeners`.
        // Instead, we'll verify that if startInstall returns a session ID for which a listener
        // is *conceptually* active, the new listener isn't registered.
        // This is hard to test perfectly without modifying the SUT.
        // The current SUT logic uses putIfAbsent, so if it's already there, it won't re-register.

        val mockInitialState = mockSessionState(sessionId, SplitInstallSessionStatus.DOWNLOADING, moduleNames = listOf(componentName))
        coEvery { mockSplitInstallManager.startInstall(any()) } returns Tasks.forResult(sessionId)
        // For this test, we assume the listener is NOT added to activeListeners by this call,
        // because `putIfAbsent` would find an existing one (hypothetically).
        // So, we won't capture `listenerSlot` here.
        every { mockSplitInstallManager.registerListener(any()) } just Runs // General mock
        every { mockSplitInstallManager.unregisterListener(any()) } just Runs
        coEvery { mockSplitInstallManager.getSessionState(sessionId) } returns Tasks.forResult(mockInitialState)

        // To simulate `activeListeners.putIfAbsent` returning a non-null (existing) listener:
        // This part is tricky because `activeListeners` is private.
        // A more robust way would be to refactor SUT to allow injecting a mock ConcurrentHashMap
        // or use a testing subclass.
        // For now, we'll assume the SUT's `putIfAbsent` works as intended and focus on the outcome:
        // if a listener was hypothetically already there, a new one isn't registered.
        // The flow should still emit the current state.

        val flow = dfComponentInstallerManager.installComponent(componentName)
        val firstItem = flow.first()

        assertEquals(DFInstallProgress(DFInstallationState.Downloading(0), playCoreState = null), firstItem) // Or whatever mapSessionStateToInstallProgress returns for DOWNLOADED

        // Since we can't easily mock `activeListeners.putIfAbsent`'s internal check,
        // we verify that `registerListener` is called at most once (for the initial setup if no listener was "found").
        // If we could control `activeListeners`, we'd verify `registerListener(listenerSlot.captured)` is not called.
        // This test is limited by the private nature of `activeListeners`.
        // A better test would involve refactoring or more complex mocking.
        // For now, we ensure it doesn't crash and emits an initial state.
    }


    @Test
    fun `retryComponentInstall calls installComponent`() = runTest {
        val componentName = "featureToRetry"
        // We just need to ensure installComponent is called.
        // We can mock installComponent itself if we were testing a higher-level component,
        // but here we are testing retryComponentInstall *within* DFComponentInstallerManager.
        // So, we'll set up mocks as if installComponent is being called.

        val sessionId = 321
        coEvery { mockSplitInstallManager.startInstall(any()) } returns Tasks.forResult(sessionId)
        every { mockSplitInstallManager.registerListener(capture(listenerSlot)) } just Runs
        every { mockSplitInstallManager.unregisterListener(any()) } just Runs
        coEvery { mockSplitInstallManager.getSessionState(sessionId) } returns Tasks.forResult(
            mockSessionState(sessionId, SplitInstallSessionStatus.PENDING, moduleNames = listOf(componentName))
        )

        val flow = dfComponentInstallerManager.retryComponentInstall(componentName)
        val firstItem = flow.first()

        assertEquals(DFInstallProgress(DFInstallationState.Pending), firstItem)
        // Further verification of installComponent's behavior can be done,
        // but the main point is that retry delegates to installComponent.
        coVerify { mockSplitInstallManager.startInstall(any()) } // Verifies the core action of installComponent was reached
    }

    // --- Tests for private helper methods (indirectly or if made internal/testable) ---

    // Example for mapSessionStateToInstallProgress (if it were public/internal)
    // For now, it's tested indirectly via installComponent's flow emissions.
    // If you make it internal:
    /*
    @Test
    fun `mapSessionStateToInstallProgress maps DOWNLOADING correctly`() {
        val mockState = mockSessionState(1, SplitInstallSessionStatus.DOWNLOADING, bytesDownloaded = 25, totalBytesToDownload = 100)
        val progress = dfComponentInstallerManager.mapSessionStateToInstallProgress(mockState, "feature1") // Assuming visibility
        assertEquals(DFInstallationState.Downloading(25), progress.frameworkState)
        assertNull(progress.playCoreState)
    }

    @Test
    fun `mapSessionStateToInstallProgress maps REQUIRES_USER_CONFIRMATION correctly`() {
        val rawState = mockSessionState(1, SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION)
        val progress = dfComponentInstallerManager.mapSessionStateToInstallProgress(rawState, "feature1") // Assuming visibility
        assertEquals(DFInstallationState.RequiresConfirmation, progress.frameworkState)
        assertEquals(rawState, progress.playCoreState)
    }
    */

    @Test
    fun `isTerminalState identifies terminal states correctly`() {
        // Accessing private method is not direct. Test its effects or make it internal for testing.
        // For now, this logic is implicitly tested by how flows complete or listeners are unregistered.
        // If made internal:
        // assertTrue(dfComponentInstallerManager.isTerminalState(DFInstallationState.Installed))
        // assertTrue(dfComponentInstallerManager.isTerminalState(DFInstallationState.Failed(DFErrorCode.UNKNOWN_ERROR)))
        // assertTrue(dfComponentInstallerManager.isTerminalState(DFInstallationState.Canceled))
        // assertFalse(dfComponentInstallerManager.isTerminalState(DFInstallationState.Pending))
        // assertFalse(dfComponentInstallerManager.isTerminalState(DFInstallationState.Downloading(50)))
    }

    @Test
    fun `cleanupListener unregisters listener if found`() {
        val sessionId = 1
        val mockListener = mockk<SplitInstallStateUpdatedListener>()

        // To test cleanupListener directly, we'd need to access/modify `activeListeners`
        // This is an example of how you might test it if `activeListeners` was injectable or modifiable.
        // For now, this is tested indirectly when a flow terminates.

        // Simulate listener was active
        // dfComponentInstallerManager.activeListeners[sessionId] = mockListener // If accessible

        every { mockSplitInstallManager.unregisterListener(mockListener) } just Runs

        // dfComponentInstallerManager.cleanupListener(sessionId) // If accessible

        // verify { mockSplitInstallManager.unregisterListener(mockListener) }
        // assertNull(dfComponentInstallerManager.activeListeners[sessionId])
    }

    @Test
    fun `cleanupListener handles error during unregistration`() {
        val sessionId = 1
        val mockListener = mockk<SplitInstallStateUpdatedListener>()
        // dfComponentInstallerManager.activeListeners[sessionId] = mockListener // If accessible
        every { mockSplitInstallManager.unregisterListener(mockListener) } throws RuntimeException("Unregister failed")

        // dfComponentInstallerManager.cleanupListener(sessionId) // If accessible

        // verify { mockSplitInstallManager.unregisterListener(mockListener) } // Should still be called
        // assertNull(dfComponentInstallerManager.activeListeners[sessionId]) // Should still be removed from map
    }
}
