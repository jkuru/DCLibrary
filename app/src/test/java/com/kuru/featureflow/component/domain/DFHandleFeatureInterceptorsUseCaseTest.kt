package com.kuru.featureflow.component.domain

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.kuru.featureflow.component.register.DFFeatureConfig
import com.kuru.featureflow.component.register.DFFeatureInterceptor
import com.kuru.featureflow.component.register.InterceptorTask // Correct import
import com.kuru.featureflow.component.state.DFFeatureInterceptorState
import com.kuru.featureflow.component.state.DFStateStore
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DFHandleFeatureInterceptorsUseCaseTest {

    @MockK
    lateinit var mockRegistry: DFFeatureRegistryUseCase

    @MockK
    lateinit var mockStateStore: DFStateStore

    private lateinit var useCase: DFHandleFeatureInterceptorsUseCase

    private val testDispatcher = StandardTestDispatcher()

    private val dummyScreen: @Composable (NavController, List<String>) -> Unit = { _, _ -> }
    private val featureName = "testFeature"

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        useCase = DFHandleFeatureInterceptorsUseCase(mockRegistry, mockStateStore)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    // --- runPreInstallInterceptors Tests ---

    @Test
    fun `runPreInstallInterceptors - no config found - returns true and logs warning`() = runTest(testDispatcher) {
        every { mockRegistry.getConfig(featureName) } returns null
        val result = useCase.runPreInstallInterceptors(featureName)
        assertTrue(result)
        verify { Log.w("DFHandleFeatureInterceptorsUseCase", "No config found for $featureName, skipping pre-install interceptors") }
        coVerify(exactly = 0) { mockStateStore.setInterceptorState(any(), any()) }
    }

    @Test
    fun `runPreInstallInterceptors - no pre-install interceptors - returns true`() = runTest(testDispatcher) {
        // Revised mocking style for InterceptorTask:
        val postInstallTask: InterceptorTask = mockk()
        every { postInstallTask() } returns true // Explicitly define behavior on the mock instance

        // This task is for an interceptor that is preInstall=false.
        // So it will not be filtered into preInstallInterceptors and thus not executed by runPreInstallInterceptors.
        // The mock's return value here is not critical for this specific test's logic path,
        // but using a consistent mocking style is good.
        val config = DFFeatureConfig(
            route = featureName,
            listOfDFFeatureInterceptor = listOf(DFFeatureInterceptor(preInstall = false, task = postInstallTask))
        )
        every { mockRegistry.getConfig(featureName) } returns config
        val result = useCase.runPreInstallInterceptors(featureName)
        assertTrue(result)
        coVerify(exactly = 0) { mockStateStore.setInterceptorState(any(), any()) }
        verify { Log.d("DFHandleFeatureInterceptorsUseCase", "Running 0 pre-install interceptors for $featureName") }
    }

    @Test
    fun `runPreInstallInterceptors - all succeed - returns true and sets states`() = runTest(testDispatcher) {
        // Revised mocking style for InterceptorTask:
        val task1Success: InterceptorTask = mockk()
        every { task1Success() } returns true

        val task2Success: InterceptorTask = mockk()
        every { task2Success() } returns true

        val interceptors = listOf(
            DFFeatureInterceptor(true, task1Success),
            DFFeatureInterceptor(true, task2Success)
        )
        val config = DFFeatureConfig(route = featureName, listOfDFFeatureInterceptor = interceptors)
        every { mockRegistry.getConfig(featureName) } returns config

        val result = useCase.runPreInstallInterceptors(featureName)

        assertTrue(result)
        // coVerify is used because invoke() (or task1Success()) is called within withContext by the use case
        coVerify(exactly = 1) { task1Success() } // Verifying call on the mocked lambda
        coVerify(exactly = 1) { task2Success() } // Verifying call on the mocked lambda
        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            mockStateStore.setInterceptorState("$featureName-pre-0", DFFeatureInterceptorState.Active)
            mockStateStore.setInterceptorState("$featureName-pre-0", DFFeatureInterceptorState.Completed)
            mockStateStore.setInterceptorState("$featureName-pre-1", DFFeatureInterceptorState.Active)
            mockStateStore.setInterceptorState("$featureName-pre-1", DFFeatureInterceptorState.Completed)
        }
    }

    @Test
    fun `runPreInstallInterceptors - one fails - returns false and sets states`() = runTest(testDispatcher) {
        // Revised mocking style
        val task1Success: InterceptorTask = mockk()
        every { task1Success() } returns true

        val task2Failure: InterceptorTask = mockk()
        every { task2Failure() } returns false // This one fails

        val interceptors = listOf(
            DFFeatureInterceptor(true, task1Success),
            DFFeatureInterceptor(true, task2Failure)
        )
        val config = DFFeatureConfig(route = featureName, listOfDFFeatureInterceptor = interceptors)
        every { mockRegistry.getConfig(featureName) } returns config

        val result = useCase.runPreInstallInterceptors(featureName)

        assertFalse(result)
        coVerify(exactly = 1) { task1Success() }
        coVerify(exactly = 1) { task2Failure() }
        val stateSlot = slot<DFFeatureInterceptorState.Failed>()
        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            mockStateStore.setInterceptorState("$featureName-pre-0", DFFeatureInterceptorState.Active)
            mockStateStore.setInterceptorState("$featureName-pre-0", DFFeatureInterceptorState.Completed)
            mockStateStore.setInterceptorState("$featureName-pre-1", DFFeatureInterceptorState.Active)
            mockStateStore.setInterceptorState("$featureName-pre-1", capture(stateSlot))
        }
        assertEquals("Pre-install interceptor 1 failed", stateSlot.captured.message)
    }



    // --- runPostInstallInterceptors Tests (apply similar changes for task mocking) ---

    @Test
    fun `runPostInstallInterceptors - no config found - returns false`() = runTest(testDispatcher) {
        every { mockRegistry.getConfig(featureName) } returns null
        val result = useCase.runPostInstallInterceptors(featureName)
        assertFalse(result) // Post-install returns false if no config
        verify { Log.w("DFHandleFeatureInterceptorsUseCase", "No config found for $featureName, cannot run post-install interceptors") }
    }

    @Test
    fun `runPostInstallInterceptors - no post-install interceptors - returns true`() = runTest(testDispatcher) {
        // Revised mocking style
        val preInstallTask: InterceptorTask = mockk()
        every { preInstallTask() } returns true

        val config = DFFeatureConfig(
            route = featureName,
            listOfDFFeatureInterceptor = listOf(DFFeatureInterceptor(true, preInstallTask)) // Only pre-install
        )
        every { mockRegistry.getConfig(featureName) } returns config
        val result = useCase.runPostInstallInterceptors(featureName)
        assertTrue(result) // Should be true if no post-install interceptors
        verify { Log.d("DFHandleFeatureInterceptorsUseCase", "Running 0 post-install interceptors for $featureName") }
    }

    @Test
    fun `runPostInstallInterceptors - all succeed - returns true`() = runTest(testDispatcher) {
        // Revised mocking style
        val task1Success: InterceptorTask = mockk()
        every { task1Success() } returns true
        val task2Success: InterceptorTask = mockk()
        every { task2Success() } returns true

        val interceptors = listOf(
            DFFeatureInterceptor(false, task1Success), // postInstall = false
            DFFeatureInterceptor(false, task2Success)
        )
        val config = DFFeatureConfig(route = featureName, listOfDFFeatureInterceptor = interceptors)
        every { mockRegistry.getConfig(featureName) } returns config

        val result = useCase.runPostInstallInterceptors(featureName)

        assertTrue(result)
        coVerify(exactly = 1) { task1Success() }
        coVerify(exactly = 1) { task2Success() }
        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            mockStateStore.setInterceptorState("$featureName-post-0", DFFeatureInterceptorState.Active)
            mockStateStore.setInterceptorState("$featureName-post-0", DFFeatureInterceptorState.Completed)
            mockStateStore.setInterceptorState("$featureName-post-1", DFFeatureInterceptorState.Active)
            mockStateStore.setInterceptorState("$featureName-post-1", DFFeatureInterceptorState.Completed)
        }
    }

    @Test
    fun `runPreInstallInterceptors - task throws exception - returns false and sets failed state`() = runTest(testDispatcher) {
        val expectedExceptionMessage = "Task blew up!" // Specific message for this test
        val exception = RuntimeException(expectedExceptionMessage)
        // Revised mocking style
        val taskThrows: InterceptorTask = mockk()
        every { taskThrows() } throws exception

        val interceptors = listOf(DFFeatureInterceptor(true, taskThrows))
        val config = DFFeatureConfig(route = featureName, listOfDFFeatureInterceptor = interceptors)
        every { mockRegistry.getConfig(featureName) } returns config

        val result = useCase.runPreInstallInterceptors(featureName)

        assertFalse(result)
        coVerify(exactly = 1) { taskThrows() }
        val stateSlot = slot<DFFeatureInterceptorState.Failed>()
        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            mockStateStore.setInterceptorState("$featureName-pre-0", DFFeatureInterceptorState.Active)
            mockStateStore.setInterceptorState("$featureName-pre-0", capture(stateSlot))
        }
        assertEquals("Pre-install interceptor 0 failed", stateSlot.captured.message) // Assuming .message from previous context

        // --- MODIFICATION FOR Log.e VERIFICATION ---
        val logExceptionSlot = slot<Throwable>()
        verify {
            Log.e(
                eq("DFHandleFeatureInterceptorsUseCase"),
                eq("Pre-install interceptor 0 failed for $featureName"),
                capture(logExceptionSlot) // Capture the Throwable
            )
        }
        assertTrue(logExceptionSlot.captured is RuntimeException)
        assertEquals(expectedExceptionMessage, logExceptionSlot.captured.message)
    }

    @Test
    fun `runPostInstallInterceptors - task throws exception - returns false`() = runTest(testDispatcher) {
        val expectedExceptionMessage = "Task blew up post-install!"
        val exception = RuntimeException(expectedExceptionMessage) // Keep your original exception
        // Revised mocking style
        val taskThrows: InterceptorTask = mockk()
        every { taskThrows() } throws exception

        val interceptors = listOf(DFFeatureInterceptor(false, taskThrows))
        val config = DFFeatureConfig(route = featureName, listOfDFFeatureInterceptor = interceptors)
        every { mockRegistry.getConfig(featureName) } returns config

        val result = useCase.runPostInstallInterceptors(featureName)

        assertFalse(result)
        coVerify(exactly = 1) { taskThrows() }
        val stateSlot = slot<DFFeatureInterceptorState.Failed>()
        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            mockStateStore.setInterceptorState("$featureName-post-0", DFFeatureInterceptorState.Active)
            mockStateStore.setInterceptorState("$featureName-post-0", capture(stateSlot))
        }
        // You were using .reason before, make sure DFFeatureInterceptorState.Failed has a 'message' or 'reason' property
        // Assuming DFFeatureInterceptorState.Failed looks like: data class Failed(val message: String)
        assertEquals("Post-install interceptor 0 failed", stateSlot.captured.message)

        // --- MODIFICATION FOR Log.e VERIFICATION ---
        val logExceptionSlot = slot<Throwable>()
        verify {
            Log.e(
                eq("DFHandleFeatureInterceptorsUseCase"),
                eq("Post-install interceptor 0 failed for $featureName"),
                capture(logExceptionSlot) // Capture the Throwable
            )
        }
        // Now assert properties of the captured exception
        assertTrue(logExceptionSlot.captured is RuntimeException)
        assertEquals(expectedExceptionMessage, logExceptionSlot.captured.message)
        // You can even assert that it's the same instance if the use case is expected to pass it through directly
        // assertEquals(exception, logExceptionSlot.captured) // This might work if the object reference is preserved
    }

    // --- fetchDynamicScreen Tests (remain unchanged as they don't mock InterceptorTask) ---

    @Test
    fun `WorkspaceDynamicScreen - config and screen found - returns screen lambda`() {
        val config = DFFeatureConfig(route = featureName)
        every { mockRegistry.getConfig(featureName) } returns config
        every { mockRegistry.getScreen(config) } returns dummyScreen

        val screen = useCase.fetchDynamicScreen(featureName)

        assertNotNull(screen)
        assertEquals(dummyScreen, screen)
    }

    @Test
    fun `WorkspaceDynamicScreen - config found but screen lambda is null - returns null`() {
        val config = DFFeatureConfig(route = featureName)
        every { mockRegistry.getConfig(featureName) } returns config
        every { mockRegistry.getScreen(config) } returns null

        val screen = useCase.fetchDynamicScreen(featureName)

        assertNull(screen)
    }

    @Test
    fun `WorkspaceDynamicScreen - no config found - returns null`() {
        every { mockRegistry.getConfig(featureName) } returns null
        val screen = useCase.fetchDynamicScreen(featureName)
        assertNull(screen)
    }
}