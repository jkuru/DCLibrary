package com.kuru.featureflow.component.googleplay

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.kuru.featureflow.component.state.DFErrorCode
import com.kuru.featureflow.component.state.DFFeatureInstallProgress
import com.kuru.featureflow.component.state.DFInstallationState
import com.kuru.featureflow.di.AppModule
import com.kuru.featureflow.di.FrameworkBindingsModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Captor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(AppModule::class, FrameworkBindingsModule::class)
@HiltAndroidTest
class DFComponentInstallerManagerTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var installerManager: DFComponentInstallerManager

    @Inject
    lateinit var splitInstallManager: SplitInstallManager

    @Inject
    lateinit var context: Context

    @Captor
    private lateinit var listenerCaptor: ArgumentCaptor<SplitInstallStateUpdatedListener>

    @Captor
    private lateinit var requestCaptor: ArgumentCaptor<SplitInstallRequest>

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val testModuleName = "testModule"
    private val testSessionId = 123

    private var mocksClosable: AutoCloseable = MockitoAnnotations.openMocks(this)

    @Before
    fun setUp() {
        hiltRule.inject()
        mocksClosable = MockitoAnnotations.openMocks(this)
        val mockTask: Task<Int> = Tasks.forResult(testSessionId)
        `when`(splitInstallManager.startInstall(ArgumentMatchers.any(SplitInstallRequest::class.java)))
            .thenReturn(mockTask)
    }

    @After
    fun tearDown() {
        mocksClosable.close()
    }

    @Test
    fun isComponentInstalled_returnsTrue_whenModuleIsInstalled() = testScope.runTest {
        `when`(splitInstallManager.installedModules).thenReturn(setOf(testModuleName))
        val result = installerManager.isComponentInstalled(testModuleName)
        assertTrue(result)
        Mockito.verify(splitInstallManager).installedModules
    }

    @Test
    fun isComponentInstalled_returnsFalse_whenModuleIsNotInstalled() = testScope.runTest {
        `when`(splitInstallManager.installedModules).thenReturn(emptySet())
        val result = installerManager.isComponentInstalled(testModuleName)
        assertFalse(result)
        Mockito.verify(splitInstallManager).installedModules
    }

    @Test
    fun installComponent_emitsInstalled_ifModuleAlreadyInstalled() = testScope.runTest {
        `when`(splitInstallManager.installedModules).thenReturn(setOf(testModuleName))
        val collectedStates = mutableListOf<DFFeatureInstallProgress>()
        installerManager.installComponent(testModuleName)
            .onEach { collectedStates.add(it) }
            .launchIn(this)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, collectedStates.size)
        assertEquals(DFInstallationState.Installed, collectedStates.first().frameworkState)

        Mockito.verify(splitInstallManager).installedModules
        Mockito.verify(splitInstallManager, Mockito.never())
            .startInstall(ArgumentMatchers.any(SplitInstallRequest::class.java))
        Mockito.verify(splitInstallManager, Mockito.never())
            .registerListener(ArgumentMatchers.any(SplitInstallStateUpdatedListener::class.java))
    }

    @Test
    fun installComponent_emitsCorrectStates_forSuccessfulInstallation() = testScope.runTest {
        `when`(splitInstallManager.installedModules).thenReturn(emptySet())

        val sessionStatePending = createMockSessionState(testSessionId, SplitInstallSessionStatus.PENDING)
        val sessionStateDownloading = createMockSessionState(testSessionId, SplitInstallSessionStatus.DOWNLOADING, bytesDownloaded = 500, totalBytes = 1000)
        val sessionStateInstalling = createMockSessionState(testSessionId, SplitInstallSessionStatus.INSTALLING, bytesDownloaded = 1000, totalBytes = 1000)
        val sessionStateInstalled = createMockSessionState(testSessionId, SplitInstallSessionStatus.INSTALLED, bytesDownloaded = 1000, totalBytes = 1000)

        `when`(splitInstallManager.getSessionState(testSessionId)).thenReturn(Tasks.forResult(sessionStatePending))

        val collectedStates = mutableListOf<DFFeatureInstallProgress>()

        val job = installerManager.installComponent(testModuleName)
            .onEach {
                collectedStates.add(it)
                println("Collected state: ${it.frameworkState}")
            }
            .onCompletion {
                if (::listenerCaptor.isInitialized && listenerCaptor.value != null) {
                    Mockito.verify(splitInstallManager).unregisterListener(listenerCaptor.value)
                }
                println("Flow completed or cancelled (Success test)")
            }
            .launchIn(this)

        testDispatcher.scheduler.advanceUntilIdle()

        Mockito.verify(splitInstallManager).startInstall(requestCaptor.capture())
        Mockito.verify(splitInstallManager).registerListener(listenerCaptor.capture())
        assertEquals(testModuleName, requestCaptor.value.moduleNames.first())

        val listener = listenerCaptor.value
        listener.onStateUpdate(sessionStatePending)
        testDispatcher.scheduler.advanceUntilIdle()

        listener.onStateUpdate(sessionStateDownloading)
        testDispatcher.scheduler.advanceUntilIdle()

        listener.onStateUpdate(sessionStateInstalling)
        testDispatcher.scheduler.advanceUntilIdle()

        listener.onStateUpdate(sessionStateInstalled)
        testDispatcher.scheduler.advanceUntilIdle()

        // Expect 5 states due to initial PENDING emission
        assertEquals(5, collectedStates.size)
        assertEquals(DFInstallationState.Pending, collectedStates[0].frameworkState) // Initial PENDING
        assertEquals(DFInstallationState.Pending, collectedStates[1].frameworkState) // Listener PENDING
        assertEquals(DFInstallationState.Downloading(50), collectedStates[2].frameworkState)
        assertEquals(DFInstallationState.Installing(100), collectedStates[3].frameworkState)
        assertEquals(DFInstallationState.Installed, collectedStates[4].frameworkState)

        job.cancel()
    }

    @Test
    fun installComponent_emitsFailedState_onInstallationError() = testScope.runTest {
        `when`(splitInstallManager.installedModules).thenReturn(emptySet())

        val sessionStatePending = createMockSessionState(testSessionId, SplitInstallSessionStatus.PENDING)
        val sessionStateFailed = createMockSessionState(testSessionId, SplitInstallSessionStatus.FAILED, errorCode = com.google.android.play.core.splitinstall.model.SplitInstallErrorCode.NETWORK_ERROR)

        `when`(splitInstallManager.getSessionState(testSessionId)).thenReturn(Tasks.forResult(sessionStatePending))

        val collectedStates = mutableListOf<DFFeatureInstallProgress>()

        val job = installerManager.installComponent(testModuleName)
            .onEach {
                collectedStates.add(it)
                println("Collected state: ${it.frameworkState}")
            }
            .onCompletion {
                if (::listenerCaptor.isInitialized && listenerCaptor.value != null) {
                    Mockito.verify(splitInstallManager).unregisterListener(listenerCaptor.value)
                }
                println("Flow completed or cancelled (Failure test)")
            }
            .launchIn(this)

        testDispatcher.scheduler.advanceUntilIdle()

        Mockito.verify(splitInstallManager).startInstall(requestCaptor.capture())
        Mockito.verify(splitInstallManager).registerListener(listenerCaptor.capture())
        assertEquals(testModuleName, requestCaptor.value.moduleNames.first())

        val listener = listenerCaptor.value
        listener.onStateUpdate(sessionStatePending)
        testDispatcher.scheduler.advanceUntilIdle()

        listener.onStateUpdate(sessionStateFailed)
        testDispatcher.scheduler.advanceUntilIdle()

        // Expect 3 states due to initial PENDING emission
        assertEquals(3, collectedStates.size)
        assertEquals(DFInstallationState.Pending, collectedStates[0].frameworkState) // Initial PENDING
        assertEquals(DFInstallationState.Pending, collectedStates[1].frameworkState) // Listener PENDING
        assertTrue(collectedStates[2].frameworkState is DFInstallationState.Failed)
        assertEquals(DFErrorCode.NETWORK_ERROR, (collectedStates[2].frameworkState as DFInstallationState.Failed).errorCode)

        job.cancel()
    }

    @Test
    fun retryComponentInstall_delegatesAndStartsInstall_whenModuleNotInstalled() = testScope.runTest {
        `when`(splitInstallManager.installedModules).thenReturn(emptySet())

        val sessionStatePending = createMockSessionState(testSessionId, SplitInstallSessionStatus.PENDING)

        `when`(splitInstallManager.getSessionState(testSessionId)).thenReturn(Tasks.forResult(sessionStatePending))

        val collectedStates = mutableListOf<DFFeatureInstallProgress>()

        val job = installerManager.retryComponentInstall(testModuleName)
            .onEach {
                collectedStates.add(it)
                println("Collected state: ${it.frameworkState}")
            }
            .onCompletion {
                if (::listenerCaptor.isInitialized && listenerCaptor.value != null) {
                    Mockito.verify(splitInstallManager).unregisterListener(listenerCaptor.value)
                }
                println("Flow completed or cancelled (Retry test)")
            }
            .launchIn(this)

        testDispatcher.scheduler.advanceUntilIdle()

        Mockito.verify(splitInstallManager).startInstall(requestCaptor.capture())
        Mockito.verify(splitInstallManager).registerListener(listenerCaptor.capture())
        assertEquals(testModuleName, requestCaptor.value.moduleNames.first())

        listenerCaptor.value.onStateUpdate(sessionStatePending)
        testDispatcher.scheduler.advanceUntilIdle()

        // Expect 2 states due to initial PENDING emission
        assertTrue(collectedStates.isNotEmpty())
        assertEquals(2, collectedStates.size)
        assertEquals(DFInstallationState.Pending, collectedStates[0].frameworkState) // Initial PENDING
        assertEquals(DFInstallationState.Pending, collectedStates[1].frameworkState) // Listener PENDING

        job.cancel()
    }

    private fun createMockSessionState(
        sessionId: Int,
        status: Int,
        errorCode: Int = 0,
        bytesDownloaded: Long = 0L,
        totalBytes: Long = 0L,
        moduleNames: List<String> = listOf(testModuleName)
    ): SplitInstallSessionState {
        val mockState = Mockito.mock(SplitInstallSessionState::class.java)
        `when`(mockState.sessionId()).thenReturn(sessionId)
        `when`(mockState.status()).thenReturn(status)
        `when`(mockState.errorCode()).thenReturn(errorCode)
        `when`(mockState.bytesDownloaded()).thenReturn(bytesDownloaded)
        `when`(mockState.totalBytesToDownload()).thenReturn(totalBytes)
        `when`(mockState.moduleNames()).thenReturn(moduleNames)
        return mockState
    }
}