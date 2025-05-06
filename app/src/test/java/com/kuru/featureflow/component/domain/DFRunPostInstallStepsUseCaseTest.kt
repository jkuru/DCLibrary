package com.kuru.featureflow.component.domain

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.kuru.featureflow.component.interceptor.DFInterceptor
import com.kuru.featureflow.component.serviceloader.DFServiceLoader
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class DFRunPostInstallStepsUseCaseTest {

    @MockK
    private lateinit var mockServiceLoader: DFServiceLoader

    @MockK
    private lateinit var mockInterceptor: DFInterceptor

    @MockK
    private lateinit var mockContext: Context

    @MockK
    private lateinit var mockApplicationContext: Context // For ApplicationContext specifically

    private lateinit var runPostInstallStepsUseCase: DFRunPostInstallStepsUseCase

    private val featureName = "testFeature"

    // Dummy screen composable for success cases
    private val dummyScreen: @Composable (NavController, List<String>) -> Unit = { _, _ -> }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        // Mock context behavior
        every { mockContext.applicationContext } returns mockApplicationContext

        runPostInstallStepsUseCase = DFRunPostInstallStepsUseCase(
            mockServiceLoader,
            mockInterceptor,
            mockContext // Pass the context that returns the application context
        )

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
        unmockkStatic(Log::class)
    }

    @Test
    fun `invoke all steps successful returns Success with screen lambda`() = runTest {
        // Arrange
        coEvery { mockServiceLoader.runServiceLoaderInitialization(featureName, mockApplicationContext) } returns true
        coEvery { mockInterceptor.runPostInstallInterceptors(featureName) } returns true
        coEvery { mockInterceptor.fetchAndSetDynamicScreen(featureName) } returns dummyScreen

        // Act
        val result = runPostInstallStepsUseCase(featureName)

        // Assert
        assertTrue(result is DFRunPostInstallResult.Success)
        assertEquals(dummyScreen, (result as DFRunPostInstallResult.Success).screen)
    }

    @Test
    fun `invoke serviceLoader fails returns Failure at SERVICE_LOADER_INITIALIZATION`() = runTest {
        // Arrange
        coEvery { mockServiceLoader.runServiceLoaderInitialization(featureName, mockApplicationContext) } returns false
        // Other mocks are not needed as it should fail early

        // Act
        val result = runPostInstallStepsUseCase(featureName)

        // Assert
        assertTrue(result is DFRunPostInstallResult.Failure)
        val failure = result as DFRunPostInstallResult.Failure
        assertEquals(Step.SERVICE_LOADER_INITIALIZATION, failure.step)
        assertEquals("ServiceLoader initialization failed for $featureName (returned false).", failure.message)
    }

    @Test
    fun `invoke serviceLoader throws exception returns Failure at SERVICE_LOADER_INITIALIZATION`() = runTest {
        // Arrange
        val exception = RuntimeException("ServiceLoader exploded")
        coEvery { mockServiceLoader.runServiceLoaderInitialization(featureName, mockApplicationContext) } throws exception

        // Act
        val result = runPostInstallStepsUseCase(featureName)

        // Assert
        assertTrue(result is DFRunPostInstallResult.Failure)
        val failure = result as DFRunPostInstallResult.Failure
        assertEquals(Step.SERVICE_LOADER_INITIALIZATION, failure.step)
        assertEquals("ServiceLoader initialization threw an exception for $featureName.", failure.message)
        assertEquals(exception, failure.cause)
    }

    @Test
    fun `invoke postInstallInterceptors fail returns Failure at POST_INSTALL_INTERCEPTORS`() = runTest {
        // Arrange
        coEvery { mockServiceLoader.runServiceLoaderInitialization(featureName, mockApplicationContext) } returns true
        coEvery { mockInterceptor.runPostInstallInterceptors(featureName) } returns false
        // fetchAndSetDynamicScreen mock not needed

        // Act
        val result = runPostInstallStepsUseCase(featureName)

        // Assert
        assertTrue(result is DFRunPostInstallResult.Failure)
        val failure = result as DFRunPostInstallResult.Failure
        assertEquals(Step.POST_INSTALL_INTERCEPTORS, failure.step)
        assertEquals("One or more post-install interceptors failed for $featureName.", failure.message)
    }

    @Test
    fun `invoke postInstallInterceptors throws exception returns Failure at POST_INSTALL_INTERCEPTORS`() = runTest {
        // Arrange
        val exception = RuntimeException("Interceptor exploded")
        coEvery { mockServiceLoader.runServiceLoaderInitialization(featureName, mockApplicationContext) } returns true
        coEvery { mockInterceptor.runPostInstallInterceptors(featureName) } throws exception

        // Act
        val result = runPostInstallStepsUseCase(featureName)

        // Assert
        assertTrue(result is DFRunPostInstallResult.Failure)
        val failure = result as DFRunPostInstallResult.Failure
        assertEquals(Step.POST_INSTALL_INTERCEPTORS, failure.step)
        assertEquals("Post-install interceptors threw an exception for $featureName.", failure.message)
        assertEquals(exception, failure.cause)
    }

    @Test
    fun `invoke fetchAndSetDynamicScreen returns null returns Failure at FETCH_DYNAMIC_SCREEN`() = runTest {
        // Arrange
        coEvery { mockServiceLoader.runServiceLoaderInitialization(featureName, mockApplicationContext) } returns true
        coEvery { mockInterceptor.runPostInstallInterceptors(featureName) } returns true
        coEvery { mockInterceptor.fetchAndSetDynamicScreen(featureName) } returns null

        // Act
        val result = runPostInstallStepsUseCase(featureName)

        // Assert
        assertTrue(result is DFRunPostInstallResult.Failure)
        val failure = result as DFRunPostInstallResult.Failure
        assertEquals(Step.FETCH_DYNAMIC_SCREEN, failure.step)
        assertEquals("Could not retrieve the screen content for $featureName after installation and interceptors.", failure.message)
    }

    @Test
    fun `invoke fetchAndSetDynamicScreen throws exception returns Failure at FETCH_DYNAMIC_SCREEN`() = runTest {
        // Arrange
        val exception = RuntimeException("Screen fetching exploded")
        coEvery { mockServiceLoader.runServiceLoaderInitialization(featureName, mockApplicationContext) } returns true
        coEvery { mockInterceptor.runPostInstallInterceptors(featureName) } returns true
        coEvery { mockInterceptor.fetchAndSetDynamicScreen(featureName) } throws exception

        // Act
        val result = runPostInstallStepsUseCase(featureName)

        // Assert
        assertTrue(result is DFRunPostInstallResult.Failure)
        val failure = result as DFRunPostInstallResult.Failure
        assertEquals(Step.FETCH_DYNAMIC_SCREEN, failure.step)
        assertEquals("Fetching dynamic screen lambda threw an exception for $featureName.", failure.message)
        assertEquals(exception, failure.cause)
    }
}
