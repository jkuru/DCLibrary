package com.kuru.featureflow.component.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.kuru.featureflow.component.domain.* // Import your domain use cases
import com.kuru.featureflow.component.state.* // Import your state classes
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DFComponentViewModelTest {

    // Coroutine Test Rule or manual setup/teardown
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Mock Dependencies
    @MockK lateinit var mockStateStore: DFStateStore
    @MockK lateinit var mockLoadFeatureUseCase: DFLoadFeatureUseCase
    @MockK lateinit var mockProcessUriUseCase: DFResolveFeatureRouteUseCase
    @MockK lateinit var mockRunPostInstallStepsUseCase: DFCompleteFeatureSetupUseCase
    @MockK lateinit var mockMonitorInstallationUseCase: DFTrackFeatureInstallUseCase

    // Class under test
    private lateinit var viewModel: DFComponentViewModel

    // Dummy data (same as before)
    private val featureName = "testFeature"
    private val featureUri = "app://host/chase/df/route/$featureName"
    private val featureParams = listOf("p1=v1")
    private val dummyScreen: @Composable (NavController, List<String>) -> Unit = { _, _ -> }
    private val dummyError = DFComponentState.Error("Test error", ErrorType.UNKNOWN, featureName)
    private val dummySessionState: SplitInstallSessionState = mockk()

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        // --- Mock setup for monitorInstallationUseCase.handleError ---
        coEvery { mockMonitorInstallationUseCase.handleError(any(), any(), any(), any(), any()) } answers {
            DFFeatureError(
                uiErrorState = DFComponentState.Error(
                    message = arg(3), errorType = arg(2), feature = arg(0) ?: arg(1) ?: "unknown", dfErrorCode = arg(4)
                ),
                installationStateToStore = if (arg<String?>(0) != null) {
                    DFInstallationState.Failed(arg<DFErrorCode?>(4) ?: DFErrorCode.UNKNOWN_ERROR)
                } else null
            )
        }
        // --- End Mock setup for handleError ---

        viewModel = DFComponentViewModel(
            stateStore = mockStateStore,
            loadFeatureUseCase = mockLoadFeatureUseCase,
            processUriUseCase = mockProcessUriUseCase,
            runPostInstallStepsUseCase = mockRunPostInstallStepsUseCase,
            monitorInstallationUseCase = mockMonitorInstallationUseCase
        )
        // Ensure initial state is collected if necessary before tests run
        testScope.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    // --- Test processIntent(ProcessUri(...)) ---

    @Test
    fun `processIntent ProcessUri - given valid feature URI - calls LoadFeature`() = testScope.runTest {
        // Arrange
        val processResult = DFProcessUriState.FeatureRoute(featureName, featureParams)
        coEvery { mockProcessUriUseCase(featureUri) } returns processResult
        coEvery { mockLoadFeatureUseCase(featureName) } returns DFLoadFeatureResult.ProceedToInstallationMonitoring // Prevent further exec

        // Act
        viewModel.processIntent(DFComponentIntent.ProcessUri(featureUri))
        advanceUntilIdle() // Allow launched coroutines to run

        // Assert
        coVerify(exactly = 1) { mockProcessUriUseCase(featureUri) }
        coVerify(exactly = 1) { mockLoadFeatureUseCase(featureName) } // Verify LoadFeature was triggered
    }

    @Test
    fun `processIntent ProcessUri - given valid navigation URI - calls LoadFeature`() = testScope.runTest {
        // Arrange
        val navKey = "navKey1"
        val navUri = "app://host/chase/df/navigation/key/$navKey"
        val processResult = DFProcessUriState.NavigationRoute(navKey, featureParams)
        coEvery { mockProcessUriUseCase(navUri) } returns processResult
        coEvery { mockLoadFeatureUseCase(navKey) } returns DFLoadFeatureResult.ProceedToInstallationMonitoring

        // Act
        viewModel.processIntent(DFComponentIntent.ProcessUri(navUri))
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { mockProcessUriUseCase(navUri) }
        coVerify(exactly = 1) { mockLoadFeatureUseCase(navKey) } // Triggered with navKey
    }

    @Test
    fun `processIntent ProcessUri - given invalid URI - sets Error state`() = testScope.runTest {
        // Arrange
        val invalidUri = "invalid-uri"
        val reason = "Bad format"
        val processResult = DFProcessUriState.InvalidUri(reason)
        coEvery { mockProcessUriUseCase(invalidUri) } returns processResult

        // Use simple value check after action
        assertEquals(DFComponentState.Loading, viewModel.uiState.value) // Check initial before action

        // Act
        viewModel.processIntent(DFComponentIntent.ProcessUri(invalidUri))
        advanceUntilIdle() // Let the intent processing and state update happen

        // Assert
        val errorState = viewModel.uiState.value
        assertIs<DFComponentState.Error>(errorState)
        assertEquals(ErrorType.URI_INVALID, errorState.errorType)
        assertTrue(errorState.message.contains(reason))

        coVerify(exactly = 1) { mockProcessUriUseCase(invalidUri) }
        coVerify(exactly = 0) { mockLoadFeatureUseCase(any()) }
    }

    @Test
    @Ignore
    fun `processIntent ProcessUri - given null URI - sets Error state`() = testScope.runTest {
        // Arrange
        assertEquals(DFComponentState.Loading, viewModel.uiState.value) // Initial

        // Act
        viewModel.processIntent(DFComponentIntent.ProcessUri(null as String))
        advanceUntilIdle()

        // Assert
        val errorState = viewModel.uiState.value
        assertIs<DFComponentState.Error>(errorState)
        assertEquals(ErrorType.URI_INVALID, errorState.errorType)
        assertEquals("Received null URI.", errorState.message)

        coVerify(exactly = 0) { mockProcessUriUseCase(any()) }
        coVerify(exactly = 0) { mockLoadFeatureUseCase(any()) }
    }

    @Test
    fun `processIntent ProcessUri - given duplicate active URI - ignores intent`() = testScope.runTest {
        // Arrange: Start processing the first URI and keep it active
        val processResult = DFProcessUriState.FeatureRoute(featureName, featureParams)
        coEvery { mockProcessUriUseCase(featureUri) } returns processResult
        coEvery { mockLoadFeatureUseCase(featureName) } returns DFLoadFeatureResult.ProceedToInstallationMonitoring
        coEvery { mockMonitorInstallationUseCase(featureName, featureParams) } returns emptyFlow()

        // Act 1: Process first intent
        viewModel.processIntent(DFComponentIntent.ProcessUri(featureUri))
        advanceUntilIdle() // Let the first processIntent start the load/install job

        // Verify initial processing
        coVerify(exactly = 1) { mockProcessUriUseCase(featureUri) }
        coVerify(exactly = 1) { mockLoadFeatureUseCase(featureName) }

        // Act 2: Process duplicate intent
        viewModel.processIntent(DFComponentIntent.ProcessUri(featureUri))
        advanceUntilIdle()

        // Assert: Verify use cases were NOT called a second time
        coVerify(exactly = 1) { mockProcessUriUseCase(featureUri) }
        coVerify(exactly = 1) { mockLoadFeatureUseCase(featureName) }
        verify { Log.w(any(), eq("Ignoring duplicate ProcessUri intent for already processing URI: $featureUri"))}
    }


    // --- Test processIntent(LoadFeature(...)) ---

    @Test
    fun `processIntent LoadFeature - when load returns ProceedToPostInstall - executes post install steps and updates state`() = testScope.runTest {
        // Arrange
        coEvery { mockLoadFeatureUseCase(featureName) } returns DFLoadFeatureResult.ProceedToPostInstall
        coEvery { mockRunPostInstallStepsUseCase(featureName) } returns DFFeatureSetupResult.Success(dummyScreen)
        every { mockStateStore.getInstallationState(featureName) } returns DFInstallationState.Installed

        // Collect state changes
        val uiStates = mutableListOf<DFComponentState>()
        val contentStates = mutableListOf<(@Composable (NavController, List<String>) -> Unit)? >()
        val uiJob = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(uiStates) }
        val contentJob = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.dynamicScreenContent.toList(contentStates) }
        advanceUntilIdle() // Collect initial states

        // Act
        viewModel.processIntent(DFComponentIntent.LoadFeature(featureName, featureParams))
        advanceUntilIdle()

        // Assert StateFlows
        // Initial states might be collected depending on timing, check last relevant states
        assertIs<DFComponentState.Success>(uiStates.last())
        val successState = uiStates.last() as DFComponentState.Success
        assertEquals(featureName, successState.feature)
        assertEquals(featureParams, successState.params)
        assertIs<DFInstallationState.Installed>(successState.featureInstallationState)

        assertEquals(dummyScreen, contentStates.last())

        // Assert Interactions
        coVerify(exactly = 1) { mockLoadFeatureUseCase(featureName) }
        coVerify(exactly = 1) { mockRunPostInstallStepsUseCase(featureName) }
        coVerify(exactly = 0) { mockMonitorInstallationUseCase(any(), any()) }

        uiJob.cancel()
        contentJob.cancel()
    }

    @Test
    fun `processIntent LoadFeature - when load returns ProceedToInstallationMonitoring - initiates installation`() = testScope.runTest {
        // Arrange
        coEvery { mockLoadFeatureUseCase(featureName) } returns DFLoadFeatureResult.ProceedToInstallationMonitoring
        coEvery { mockMonitorInstallationUseCase(featureName, featureParams) } returns emptyFlow()

        val uiStates = mutableListOf<DFComponentState>()
        val uiJob = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(uiStates) }
        advanceUntilIdle() // Collect initial Loading

        // Act
        viewModel.processIntent(DFComponentIntent.LoadFeature(featureName, featureParams))
        advanceUntilIdle()

        // Assert
        // Should remain Loading or potentially emit Loading again inside initiateInstallation
        assertTrue(uiStates.all { it is DFComponentState.Loading })

        coVerify(exactly = 1) { mockLoadFeatureUseCase(featureName) }
        coVerify(exactly = 1) { mockMonitorInstallationUseCase(featureName, featureParams) }
        coVerify(exactly = 0) { mockRunPostInstallStepsUseCase(any()) }

        uiJob.cancel()
    }

    @Test
    fun `processIntent LoadFeature - when load returns Failure - sets Error state`() = testScope.runTest {
        // Arrange
        val loadFailure = DFLoadFeatureResult.Failure(ErrorType.VALIDATION, "Load failed")
        coEvery { mockLoadFeatureUseCase(featureName) } returns loadFailure

        val uiStates = mutableListOf<DFComponentState>()
        val uiJob = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(uiStates) }
        advanceUntilIdle() // Initial Loading

        // Act
        viewModel.processIntent(DFComponentIntent.LoadFeature(featureName, featureParams))
        advanceUntilIdle()

        // Assert
        assertIs<DFComponentState.Error>(uiStates.last())
        val errorState = uiStates.last() as DFComponentState.Error
        assertEquals(loadFailure.errorType, errorState.errorType)
        assertEquals(loadFailure.message, errorState.message)
        assertEquals(featureName, errorState.feature)

        coVerify(exactly = 1) { mockLoadFeatureUseCase(featureName) }
        coVerify(exactly = 0) { mockMonitorInstallationUseCase(any(), any()) }
        coVerify(exactly = 0) { mockRunPostInstallStepsUseCase(any()) }

        uiJob.cancel()
    }

    // Test for job cancellation remains the same conceptually,
    // verification via reflection helper is independent of Turbine.
    @Test
    fun `processIntent LoadFeature - when new feature requested while another installing - cancels previous job`() = testScope.runTest {
        // Arrange: Start first feature installation
        val feature1 = "feature1"
        val feature2 = "feature2"
        val monitorFlow1 = MutableSharedFlow<DFInstallationMonitoringState>()
        coEvery { mockLoadFeatureUseCase(feature1) } returns DFLoadFeatureResult.ProceedToInstallationMonitoring
        coEvery { mockMonitorInstallationUseCase(feature1, any()) } returns monitorFlow1

        viewModel.processIntent(DFComponentIntent.LoadFeature(feature1, emptyList()))
        advanceUntilIdle()

        coVerify { mockMonitorInstallationUseCase(feature1, any()) }
        val firstJob = viewModel.getCurrentInstallJob_TestOnly()
        assertTrue(firstJob?.isActive == true)

        // Arrange: Setup for second feature
        coEvery { mockLoadFeatureUseCase(feature2) } returns DFLoadFeatureResult.ProceedToInstallationMonitoring
        coEvery { mockMonitorInstallationUseCase(feature2, any()) } returns emptyFlow()

        // Act: Load second feature
        viewModel.processIntent(DFComponentIntent.LoadFeature(feature2, emptyList()))
        advanceUntilIdle()

        // Assert
        assertFalse(firstJob?.isActive == true, "First job should be cancelled")
        coVerify { mockLoadFeatureUseCase(feature2) }
        coVerify { mockMonitorInstallationUseCase(feature2, any()) }
    }

    // Helper for test above
    private fun DFComponentViewModel.getCurrentInstallJob_TestOnly(): Job? { /* same as before */
        return try {
            val field = DFComponentViewModel::class.java.getDeclaredField("currentInstallJob")
            field.isAccessible = true
            field.get(this) as Job?
        } catch (e: Exception) {
            null
        }
    }

    // --- Test initiateInstallation (via LoadFeature) ---

    @Test
    @Ignore
    fun `initiateInstallation - receives StorePendingConfirmation - emits ConfirmationEventData`() = testScope.runTest {
        // Arrange
        val confirmationState = DFInstallationMonitoringState.StorePendingConfirmation(dummySessionState)
        val monitorFlow = flowOf(confirmationState)
        coEvery { mockLoadFeatureUseCase(featureName) } returns DFLoadFeatureResult.ProceedToInstallationMonitoring
        coEvery { mockMonitorInstallationUseCase(featureName, featureParams) } returns monitorFlow

        val events = mutableListOf<ConfirmationEventData>()
        val eventJob = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.eventFlow.toList(events) }
        advanceUntilIdle() // Start collector

        // Act
        viewModel.processIntent(DFComponentIntent.LoadFeature(featureName, featureParams))
        advanceUntilIdle() // Process intent and flow emission

        // Assert
        assertEquals(1, events.size)
        assertEquals(featureName, events[0].feature)
        assertEquals(dummySessionState, events[0].sessionState)

        // Verify UI state changed
        assertIs<DFComponentState.RequiresConfirmation>(viewModel.uiState.value)

        eventJob.cancel()
    }

    @Test
    fun `initiateInstallation - receives TriggerPostInstallSteps - executes post install steps`() = testScope.runTest {
        // Arrange
        val triggerState = DFInstallationMonitoringState.TriggerPostInstallSteps
        val monitorFlow = flowOf(triggerState)
        coEvery { mockLoadFeatureUseCase(featureName) } returns DFLoadFeatureResult.ProceedToInstallationMonitoring
        coEvery { mockMonitorInstallationUseCase(featureName, featureParams) } returns monitorFlow
        coEvery { mockRunPostInstallStepsUseCase(featureName) } returns DFFeatureSetupResult.Success(dummyScreen)
        every { mockStateStore.getInstallationState(featureName) } returns DFInstallationState.Installed

        // Act
        viewModel.processIntent(DFComponentIntent.LoadFeature(featureName, featureParams))
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { mockMonitorInstallationUseCase(featureName, featureParams) }
        coVerify(exactly = 1) { mockRunPostInstallStepsUseCase(featureName) }
        assertIs<DFComponentState.Success>(viewModel.uiState.value)
        assertEquals(dummyScreen, viewModel.dynamicScreenContent.value)
    }

    // --- Test processIntent(Retry) ---
    @Test
    fun `processIntent Retry - when last feature exists - calls loadFeature`() = testScope.runTest {
        // Arrange
        coEvery { mockStateStore.getLastAttemptedFeature() } returns featureName
        coEvery { mockLoadFeatureUseCase(featureName) } returns DFLoadFeatureResult.ProceedToInstallationMonitoring

        // Act
        viewModel.processIntent(DFComponentIntent.Retry)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { mockStateStore.getLastAttemptedFeature() }
        coVerify(exactly = 1) { mockLoadFeatureUseCase(featureName) }
    }

    @Test
    fun `processIntent Retry - when no last feature - sets Error state`() = testScope.runTest {
        // Arrange
        coEvery { mockStateStore.getLastAttemptedFeature() } returns null
        assertEquals(DFComponentState.Loading, viewModel.uiState.value) // Initial

        // Act
        viewModel.processIntent(DFComponentIntent.Retry)
        advanceUntilIdle()

        // Assert
        val errorState = viewModel.uiState.value
        assertIs<DFComponentState.Error>(errorState)
        assertEquals(ErrorType.VALIDATION, errorState.errorType)
        assertEquals("No feature to retry", errorState.message)

        coVerify(exactly = 1) { mockStateStore.getLastAttemptedFeature() }
        coVerify(exactly = 0) { mockLoadFeatureUseCase(any()) }
    }

    // --- Test processIntent(UserConfirmationResult) ---
    @Test
    fun `processIntent UserConfirmationResult - confirmed true - sets Loading state`() = testScope.runTest {
        // Arrange - Set state to RequiresConfirmation first
        viewModel.forceSetUiState_TestOnly(DFComponentState.RequiresConfirmation(featureName))
        assertEquals(DFComponentState.RequiresConfirmation(featureName), viewModel.uiState.value) // Verify pre-state

        // Act
        viewModel.processIntent(DFComponentIntent.UserConfirmationResult(featureName, true))
        advanceUntilIdle()

        // Assert
        assertEquals(DFComponentState.Loading, viewModel.uiState.value) // State after intent
        coVerify(exactly = 0) { mockMonitorInstallationUseCase.handleError(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `processIntent UserConfirmationResult - confirmed false - sets Error state`() = testScope.runTest {
        // Arrange
        val expectedErrorType = ErrorType.INSTALLATION
        val expectedMessage = "User cancelled installation for $featureName."
        val expectedErrorCode = DFErrorCode.NO_ERROR
        val expectedFailedState = DFInstallationState.Failed(expectedErrorCode)
        val expectedUiError = DFComponentState.Error(expectedMessage, expectedErrorType, featureName, expectedErrorCode)

        coEvery {
            mockMonitorInstallationUseCase.handleError(
                feature = featureName,
                currentFeature = null, // Assume null initially
                errorType = expectedErrorType,
                message = expectedMessage,
                dfErrorCode = expectedErrorCode
            )
        } returns DFFeatureError(expectedUiError, expectedFailedState)

        assertEquals(DFComponentState.Loading, viewModel.uiState.value) // Initial state

        // Act
        viewModel.processIntent(DFComponentIntent.UserConfirmationResult(featureName, false))
        advanceUntilIdle()

        // Assert
        assertEquals(expectedUiError, viewModel.uiState.value)
        // Verify state store was updated via handleError mock's result
        coVerify { mockStateStore.setInstallationState(featureName, expectedFailedState) }
    }

    // Helper to set initial state if needed for tests
    private fun DFComponentViewModel.forceSetUiState_TestOnly(state: DFComponentState) { /* same as before */
        try {
            val field = DFComponentViewModel::class.java.getDeclaredField("_uiState")
            field.isAccessible = true
            (field.get(this) as MutableStateFlow<DFComponentState>).value = state
        } catch (e: Exception) {
            fail("Failed to set UI state via reflection: $e")
        }
    }

    // --- Test clearDynamicContent ---
    @Test
    fun `clearDynamicContent - sets dynamicScreenContent to null`() = testScope.runTest {
        // Arrange: Set some initial content
        viewModel.forceSetDynamicContent_TestOnly(dummyScreen)
        assertNotNull(viewModel.dynamicScreenContent.value)

        // Act
        viewModel.clearDynamicContent()
        advanceUntilIdle() // Ensure state flow updates if needed

        // Assert
        assertNull(viewModel.dynamicScreenContent.value)
    }

    // Helper to set initial content
    private fun DFComponentViewModel.forceSetDynamicContent_TestOnly(content: (@Composable (NavController, List<String>) -> Unit)?) { /* same as before */
        try {
            val field = DFComponentViewModel::class.java.getDeclaredField("_dynamicScreenContent")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(this) as MutableStateFlow<(@Composable (NavController, List<String>) -> Unit)?>).value = content
        } catch (e: Exception) {
            fail("Failed to set dynamic content via reflection: $e")
        }
    }
}