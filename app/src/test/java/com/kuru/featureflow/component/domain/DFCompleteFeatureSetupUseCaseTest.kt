package com.kuru.featureflow.component.domain

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.kuru.featureflow.component.state.DFFeatureSetupResult
import com.kuru.featureflow.component.state.FeatureSetupStep
import io.mockk.MockKAnnotations // For manual initialization if needed
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest // Kotlin Test annotation
import kotlin.test.BeforeTest // Kotlin Test annotation
import kotlin.test.Test // Kotlin Test annotation
import kotlin.test.assertEquals
import kotlin.test.assertIs // Kotlin Test assertion
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DFCompleteFeatureSetupUseCaseTest {

    @MockK
    lateinit var mockInitializeFeatureUseCase: DFInitializeFeatureUseCase

    @MockK
    lateinit var mockHandleInterceptorsUseCase: DFHandleFeatureInterceptorsUseCase

    @RelaxedMockK // Relaxed because we don't care about all context calls, just applicationContext
    lateinit var mockContext: Context

    @RelaxedMockK
    lateinit var mockApplicationContext: Context // For context.applicationContext

    private lateinit var useCase: DFCompleteFeatureSetupUseCase

    // Dummy composable for success cases
    private val dummyScreen: @Composable (NavController, List<String>) -> Unit = { _, _ -> }
    private val featureName = "testFeature" // Common feature name for tests

    @BeforeTest // Using kotlin.test annotation
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true) // Initialize mocks

        // Mock Android's Log methods as they are used in the UseCase
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0 // For warnings when boolean checks fail
        every { Log.e(any(), any(), any()) } returns 0 // For exceptions
        every { Log.e(any(), any<String>()) } returns 0 // For simple error messages

        every { mockContext.applicationContext } returns mockApplicationContext

        useCase = DFCompleteFeatureSetupUseCase(
            serviceLoader = mockInitializeFeatureUseCase,
            interceptors = mockHandleInterceptorsUseCase,
            context = mockContext
        )
    }

    @AfterTest // Using kotlin.test annotation
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `invoke - given all steps succeed - then returns Success with screen lambda`() = runTest {
        // Given
        coEvery {
            mockInitializeFeatureUseCase.runServiceLoaderInitialization(featureName, mockApplicationContext)
        } returns true
        coEvery { mockHandleInterceptorsUseCase.runPostInstallInterceptors(featureName) } returns true
        coEvery { mockHandleInterceptorsUseCase.fetchDynamicScreen(featureName) } returns dummyScreen

        // When
        val result = useCase(featureName)

        // Then
        assertIs<DFFeatureSetupResult.Success>(result)
        assertEquals(dummyScreen, result.screen)
    }

    // --- Service Loader Failures ---
    @Test
    fun `invoke - given service loader initialization returns false - then returns Failure`() = runTest {
        // Given
        coEvery {
            mockInitializeFeatureUseCase.runServiceLoaderInitialization(featureName, mockApplicationContext)
        } returns false

        // When
        val result = useCase(featureName)

        // Then
        assertIs<DFFeatureSetupResult.Failure>(result)
        assertEquals(FeatureSetupStep.SERVICE_LOADER_INITIALIZATION, result.featureSetupStep)
        assertEquals("ServiceLoader initialization failed for $featureName (returned false).", result.message)
        assertNull(result.cause)
    }

    @Test
    fun `invoke - given service loader initialization throws exception - then returns Failure`() = runTest {
        // Given
        val exception = RuntimeException("ServiceLoader init error")
        coEvery {
            mockInitializeFeatureUseCase.runServiceLoaderInitialization(featureName, mockApplicationContext)
        } throws exception

        // When
        val result = useCase(featureName)

        // Then
        assertIs<DFFeatureSetupResult.Failure>(result)
        assertEquals(FeatureSetupStep.SERVICE_LOADER_INITIALIZATION, result.featureSetupStep)
        assertEquals("ServiceLoader initialization threw an exception for $featureName.", result.message)
        assertEquals(exception, result.cause)
    }

    // --- Post-Install Interceptors Failures ---
    @Test
    fun `invoke - given post-install interceptors return false - then returns Failure`() = runTest {
        // Given
        coEvery {
            mockInitializeFeatureUseCase.runServiceLoaderInitialization(featureName, mockApplicationContext)
        } returns true // Previous step succeeds
        coEvery { mockHandleInterceptorsUseCase.runPostInstallInterceptors(featureName) } returns false

        // When
        val result = useCase(featureName)

        // Then
        assertIs<DFFeatureSetupResult.Failure>(result)
        assertEquals(FeatureSetupStep.POST_INSTALL_INTERCEPTORS, result.featureSetupStep)
        assertEquals("One or more post-install interceptors failed for $featureName.", result.message)
        assertNull(result.cause)
    }

    @Test
    fun `invoke - given post-install interceptors throw exception - then returns Failure`() = runTest {
        // Given
        coEvery {
            mockInitializeFeatureUseCase.runServiceLoaderInitialization(featureName, mockApplicationContext)
        } returns true // Previous step succeeds
        val exception = IllegalStateException("Interceptor blew up")
        coEvery { mockHandleInterceptorsUseCase.runPostInstallInterceptors(featureName) } throws exception

        // When
        val result = useCase(featureName)

        // Then
        assertIs<DFFeatureSetupResult.Failure>(result)
        assertEquals(FeatureSetupStep.POST_INSTALL_INTERCEPTORS, result.featureSetupStep)
        assertEquals("Post-install interceptors threw an exception for $featureName.", result.message)
        assertEquals(exception, result.cause)
    }

    // --- Fetch Dynamic Screen Failures ---
    @Test
    fun `invoke - given fetch dynamic screen returns null - then returns Failure`() = runTest {
        // Given
        coEvery {
            mockInitializeFeatureUseCase.runServiceLoaderInitialization(featureName, mockApplicationContext)
        } returns true
        coEvery { mockHandleInterceptorsUseCase.runPostInstallInterceptors(featureName) } returns true // Previous steps succeed
        coEvery { mockHandleInterceptorsUseCase.fetchDynamicScreen(featureName) } returns null

        // When
        val result = useCase(featureName)

        // Then
        assertIs<DFFeatureSetupResult.Failure>(result)
        assertEquals(FeatureSetupStep.FETCH_DYNAMIC_SCREEN, result.featureSetupStep)
        assertEquals("Could not retrieve the screen content for $featureName after installation and interceptors.", result.message)
        assertNull(result.cause)
    }

    @Test
    fun `invoke - given fetch dynamic screen throws exception - then returns Failure`() = runTest {
        // Given
        coEvery {
            mockInitializeFeatureUseCase.runServiceLoaderInitialization(featureName, mockApplicationContext)
        } returns true
        coEvery { mockHandleInterceptorsUseCase.runPostInstallInterceptors(featureName) } returns true // Previous steps succeed
        val exception = NullPointerException("Screen lambda not found")
        coEvery { mockHandleInterceptorsUseCase.fetchDynamicScreen(featureName) } throws exception

        // When
        val result = useCase(featureName)

        // Then
        assertIs<DFFeatureSetupResult.Failure>(result)
        assertEquals(FeatureSetupStep.FETCH_DYNAMIC_SCREEN, result.featureSetupStep)
        assertEquals("Fetching dynamic screen lambda threw an exception for $featureName.", result.message)
        assertEquals(exception, result.cause)
    }
}