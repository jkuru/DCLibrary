package com.kuru.featureflow.component.interceptor

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.kuru.featureflow.component.register.DFComponentConfig
import com.kuru.featureflow.component.register.DFComponentInterceptor
import com.kuru.featureflow.component.register.DFComponentRegistry
import com.kuru.featureflow.component.state.DFComponentStateStore
import com.kuru.featureflow.component.state.DFInterceptorState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class DFInterceptorManagerTest {

    private lateinit var registry: DFComponentRegistry
    private lateinit var stateStore: DFComponentStateStore
    private lateinit var interceptorManager: DFInterceptorManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        registry = mockk(relaxed = true)
        stateStore = mockk(relaxed = true)
        interceptorManager = DFInterceptorManager(registry, stateStore)

        // Mock static Log calls
        mockkStatic(Log::class)
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // reset the main dispatcher to the original one
    }

    // --- runPreInstallInterceptors Tests ---

    @Test
    fun `runPreInstallInterceptors returns true when config not found`() = runTest {
        val featureName = "testFeature"
        every { registry.getConfig(featureName) } returns null

        val result = interceptorManager.runPreInstallInterceptors(featureName)

        assertTrue(result)
        coVerify(exactly = 0) { stateStore.setInterceptorState(any(), any()) }
    }

    @Test
    fun `runPreInstallInterceptors returns true when no pre-install interceptors`() = runTest {
        val featureName = "testFeature"
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns emptyList()
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPreInstallInterceptors(featureName)

        assertTrue(result)
        coVerify(exactly = 0) { stateStore.setInterceptorState(any(), any()) }
    }

    @Test
    fun `runPreInstallInterceptors success for single pre-install interceptor`() = runTest {
        val featureName = "testFeature"
        val interceptor = mockk<DFComponentInterceptor> {
            every { preInstall } returns true
            coEvery { task() } returns true
        }
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns listOf(interceptor)
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPreInstallInterceptors(featureName)

        assertTrue(result)
        coVerify { stateStore.setInterceptorState("$featureName-pre-0", DFInterceptorState.Active) }
        coVerify { stateStore.setInterceptorState("$featureName-pre-0", DFInterceptorState.Completed) }
    }

    @Test
    fun `runPreInstallInterceptors failure for single pre-install interceptor`() = runTest {
        val featureName = "testFeature"
        val interceptor = mockk<DFComponentInterceptor> {
            every { preInstall } returns true
            coEvery { task() } returns false
        }
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns listOf(interceptor)
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPreInstallInterceptors(featureName)

        assertFalse(result)
        coVerify { stateStore.setInterceptorState("$featureName-pre-0", DFInterceptorState.Active) }
        coVerify {
            stateStore.setInterceptorState(
                "$featureName-pre-0",
                DFInterceptorState.Failed("Pre-install interceptor 0 failed")
            )
        }
    }

    @Test
    fun `runPreInstallInterceptors handles exception in pre-install interceptor`() = runTest {
        val featureName = "testFeature"
        val interceptor = mockk<DFComponentInterceptor> {
            every { preInstall } returns true
            coEvery { task() } throws RuntimeException("Test Exception")
        }
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns listOf(interceptor)
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPreInstallInterceptors(featureName)

        assertFalse(result)
        coVerify { stateStore.setInterceptorState("$featureName-pre-0", DFInterceptorState.Active) }
        coVerify {
            stateStore.setInterceptorState(
                "$featureName-pre-0",
                DFInterceptorState.Failed("Pre-install interceptor 0 failed")
            )
        }
    }

    @Test
    fun `runPreInstallInterceptors success for multiple pre-install interceptors`() = runTest {
        val featureName = "testFeature"
        val interceptor1 = mockk<DFComponentInterceptor> {
            every { preInstall } returns true
            coEvery { task() } returns true
        }
        val interceptor2 = mockk<DFComponentInterceptor> {
            every { preInstall } returns true
            coEvery { task() } returns true
        }
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns listOf(interceptor1, interceptor2)
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPreInstallInterceptors(featureName)

        assertTrue(result)
        coVerify { stateStore.setInterceptorState("$featureName-pre-0", DFInterceptorState.Active) }
        coVerify { stateStore.setInterceptorState("$featureName-pre-0", DFInterceptorState.Completed) }
        coVerify { stateStore.setInterceptorState("$featureName-pre-1", DFInterceptorState.Active) }
        coVerify { stateStore.setInterceptorState("$featureName-pre-1", DFInterceptorState.Completed) }
    }

    @Test
    fun `runPreInstallInterceptors failure stops at first failing pre-install interceptor`() = runTest {
        val featureName = "testFeature"
        val interceptor1 = mockk<DFComponentInterceptor> {
            every { preInstall } returns true
            coEvery { task() } returns true
        }
        val interceptor2 = mockk<DFComponentInterceptor> {
            every { preInstall } returns true
            coEvery { task() } returns false // This one fails
        }
        val interceptor3 = mockk<DFComponentInterceptor> {
            every { preInstall } returns true
            coEvery { task() } returns true
        }
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns listOf(interceptor1, interceptor2, interceptor3)
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPreInstallInterceptors(featureName)

        assertFalse(result)
        coVerify { stateStore.setInterceptorState("$featureName-pre-0", DFInterceptorState.Active) }
        coVerify { stateStore.setInterceptorState("$featureName-pre-0", DFInterceptorState.Completed) }
        coVerify { stateStore.setInterceptorState("$featureName-pre-1", DFInterceptorState.Active) }
        coVerify {
            stateStore.setInterceptorState(
                "$featureName-pre-1",
                DFInterceptorState.Failed("Pre-install interceptor 1 failed")
            )
        }
        coVerify(exactly = 0) { stateStore.setInterceptorState("$featureName-pre-2", any()) } // Interceptor 3 should not run
    }


    // --- runPostInstallInterceptors Tests ---

    @Test
    fun `runPostInstallInterceptors returns false when config not found`() = runTest {
        val featureName = "testFeature"
        every { registry.getConfig(featureName) } returns null

        val result = interceptorManager.runPostInstallInterceptors(featureName)

        assertFalse(result) // Should be false as per implementation
        coVerify(exactly = 0) { stateStore.setInterceptorState(any(), any()) }
    }

    @Test
    fun `runPostInstallInterceptors returns true when no post-install interceptors`() = runTest {
        val featureName = "testFeature"
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns emptyList() // No interceptors at all
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPostInstallInterceptors(featureName)

        assertTrue(result)
        coVerify(exactly = 0) { stateStore.setInterceptorState(any(), any()) }
    }

    @Test
    fun `runPostInstallInterceptors returns true when only pre-install interceptors exist`() = runTest {
        val featureName = "testFeature"
        val preInterceptor = mockk<DFComponentInterceptor> {
            every { preInstall } returns true // This is a pre-install interceptor
            coEvery { task() } returns true
        }
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns listOf(preInterceptor)
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPostInstallInterceptors(featureName)

        assertTrue(result)
        coVerify(exactly = 0) { stateStore.setInterceptorState(any(), any()) } // No post-install state changes
    }


    @Test
    fun `runPostInstallInterceptors success for single post-install interceptor`() = runTest {
        val featureName = "testFeature"
        val interceptor = mockk<DFComponentInterceptor> {
            every { preInstall } returns false // Post-install
            coEvery { task() } returns true
        }
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns listOf(interceptor)
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPostInstallInterceptors(featureName)

        assertTrue(result)
        coVerify { stateStore.setInterceptorState("$featureName-post-0", DFInterceptorState.Active) }
        coVerify { stateStore.setInterceptorState("$featureName-post-0", DFInterceptorState.Completed) }
    }

    @Test
    fun `runPostInstallInterceptors failure for single post-install interceptor`() = runTest {
        val featureName = "testFeature"
        val interceptor = mockk<DFComponentInterceptor> {
            every { preInstall } returns false // Post-install
            coEvery { task() } returns false
        }
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns listOf(interceptor)
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPostInstallInterceptors(featureName)

        assertFalse(result)
        coVerify { stateStore.setInterceptorState("$featureName-post-0", DFInterceptorState.Active) }
        coVerify {
            stateStore.setInterceptorState(
                "$featureName-post-0",
                DFInterceptorState.Failed("Post-install interceptor 0 failed")
            )
        }
    }

    @Test
    fun `runPostInstallInterceptors handles exception in post-install interceptor`() = runTest {
        val featureName = "testFeature"
        val interceptor = mockk<DFComponentInterceptor> {
            every { preInstall } returns false // Post-install
            coEvery { task() } throws RuntimeException("Test Exception")
        }
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns listOf(interceptor)
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPostInstallInterceptors(featureName)

        assertFalse(result)
        coVerify { stateStore.setInterceptorState("$featureName-post-0", DFInterceptorState.Active) }
        coVerify {
            stateStore.setInterceptorState(
                "$featureName-post-0",
                DFInterceptorState.Failed("Post-install interceptor 0 failed")
            )
        }
    }

    @Test
    fun `runPostInstallInterceptors success for multiple post-install interceptors`() = runTest {
        val featureName = "testFeature"
        val interceptor1 = mockk<DFComponentInterceptor> {
            every { preInstall } returns false // Post-install
            coEvery { task() } returns true
        }
        val interceptor2 = mockk<DFComponentInterceptor> {
            every { preInstall } returns false // Post-install
            coEvery { task() } returns true
        }
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns listOf(interceptor1, interceptor2)
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPostInstallInterceptors(featureName)

        assertTrue(result)
        coVerify { stateStore.setInterceptorState("$featureName-post-0", DFInterceptorState.Active) }
        coVerify { stateStore.setInterceptorState("$featureName-post-0", DFInterceptorState.Completed) }
        coVerify { stateStore.setInterceptorState("$featureName-post-1", DFInterceptorState.Active) }
        coVerify { stateStore.setInterceptorState("$featureName-post-1", DFInterceptorState.Completed) }
    }

    @Test
    fun `runPostInstallInterceptors failure stops at first failing post-install interceptor`() = runTest {
        val featureName = "testFeature"
        val interceptor1 = mockk<DFComponentInterceptor> {
            every { preInstall } returns false // Post-install
            coEvery { task() } returns true
        }
        val interceptor2 = mockk<DFComponentInterceptor> {
            every { preInstall } returns false // Post-install
            coEvery { task() } returns false // This one fails
        }
        val interceptor3 = mockk<DFComponentInterceptor> {
            every { preInstall } returns false // Post-install
            coEvery { task() } returns true
        }
        val mockConfig = mockk<DFComponentConfig> {
            every { listOfDFComponentInterceptor } returns listOf(interceptor1, interceptor2, interceptor3)
        }
        every { registry.getConfig(featureName) } returns mockConfig

        val result = interceptorManager.runPostInstallInterceptors(featureName)

        assertFalse(result)
        coVerify { stateStore.setInterceptorState("$featureName-post-0", DFInterceptorState.Active) }
        coVerify { stateStore.setInterceptorState("$featureName-post-0", DFInterceptorState.Completed) }
        coVerify { stateStore.setInterceptorState("$featureName-post-1", DFInterceptorState.Active) }
        coVerify {
            stateStore.setInterceptorState(
                "$featureName-post-1",
                DFInterceptorState.Failed("Post-install interceptor 1 failed")
            )
        }
        coVerify(exactly = 0) { stateStore.setInterceptorState("$featureName-post-2", any()) } // Interceptor 3 should not run
    }

    // --- fetchAndSetDynamicScreen Tests ---

    @Test
    fun `WorkspaceAndSetDynamicScreen returns null when config not found`() {
        val featureName = "testFeature"
        every { registry.getConfig(featureName) } returns null

        val screen = interceptorManager.fetchAndSetDynamicScreen(featureName)

        assertNull(screen)
        verify { registry.getConfig(featureName) }
        verify(exactly = 0) { registry.getScreen(any()) }
    }

    @Test
    fun `WorkspaceAndSetDynamicScreen returns null when screen lambda is null in registry`() {
        val featureName = "testFeature"
        val mockConfig = mockk<DFComponentConfig>()
        every { registry.getConfig(featureName) } returns mockConfig
        every { registry.getScreen(mockConfig) } returns null

        val screen = interceptorManager.fetchAndSetDynamicScreen(featureName)

        assertNull(screen)
        verify { registry.getConfig(featureName) }
        verify { registry.getScreen(mockConfig) }
    }

    @Test
    fun `WorkspaceAndSetDynamicScreen returns screen lambda when found`() {
        val featureName = "testFeature"
        val mockConfig = mockk<DFComponentConfig>()
        val mockScreenLambda: @Composable (NavController, List<String>) -> Unit = { _, _ -> }
        every { registry.getConfig(featureName) } returns mockConfig
        every { registry.getScreen(mockConfig) } returns mockScreenLambda

        val screen = interceptorManager.fetchAndSetDynamicScreen(featureName)

        assertNotNull(screen)
        assertEquals(mockScreenLambda, screen)
        verify { registry.getConfig(featureName) }
        verify { registry.getScreen(mockConfig) }
    }
}