package com.kuru.featureflow.component.domain

import android.util.Log
import com.kuru.featureflow.component.googleplay.DFComponentInstaller
import com.kuru.featureflow.component.state.DFComponentStateStore
import com.kuru.featureflow.component.ui.ErrorType
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class DFLoadFeatureUseCaseTest {

    @MockK
    private lateinit var mockInstaller: DFComponentInstaller

    @MockK
    private lateinit var mockStateStore: DFComponentStateStore

    private lateinit var loadFeatureUseCase: DFLoadFeatureUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        loadFeatureUseCase = DFLoadFeatureUseCase(mockInstaller, mockStateStore)

        // Mock static Log calls
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0 // For Log.w(tag, msg)
        every { Log.w(any(), any<Throwable>()) } returns 0 // For Log.w(tag, Throwable)
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0 // For Log.w(tag, msg, Throwable)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0 // For Log.e(tag, msg, throwable)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `invoke with blank feature name returns Failure`() = runTest {
        // Arrange
        val blankFeatureName = " "
        // No need to mock store or installer as it should fail before that

        // Act
        val result = loadFeatureUseCase(blankFeatureName)

        // Assert
        assertTrue(result is DFLoadFeatureResult.Failure)
        val failureResult = result as DFLoadFeatureResult.Failure
        assertEquals(ErrorType.VALIDATION, failureResult.errorType)
        assertEquals("Feature name cannot be empty", failureResult.message)
        coVerify(exactly = 0) { mockStateStore.setLastAttemptedFeature(any()) }
        coVerify(exactly = 0) { mockInstaller.isComponentInstalled(any()) }
    }

    @Test
    fun `invoke when feature is installed returns ProceedToPostInstall`() = runTest {
        // Arrange
        val featureName = "testFeature"
        coEvery { mockStateStore.setLastAttemptedFeature(featureName) } just Runs
        coEvery { mockInstaller.isComponentInstalled(featureName) } returns true

        // Act
        val result = loadFeatureUseCase(featureName)

        // Assert
        assertTrue(result is DFLoadFeatureResult.ProceedToPostInstall)
        coVerify(exactly = 1) { mockStateStore.setLastAttemptedFeature(featureName) }
        coVerify(exactly = 1) { mockInstaller.isComponentInstalled(featureName) }
    }

    @Test
    fun `invoke when feature is not installed returns ProceedToInstallationMonitoring`() = runTest {
        // Arrange
        val featureName = "testFeature"
        coEvery { mockStateStore.setLastAttemptedFeature(featureName) } just Runs
        coEvery { mockInstaller.isComponentInstalled(featureName) } returns false

        // Act
        val result = loadFeatureUseCase(featureName)

        // Assert
        assertTrue(result is DFLoadFeatureResult.ProceedToInstallationMonitoring)
        coVerify(exactly = 1) { mockStateStore.setLastAttemptedFeature(featureName) }
        coVerify(exactly = 1) { mockInstaller.isComponentInstalled(featureName) }
    }

    @Test
    fun `invoke when setLastAttemptedFeature throws exception still proceeds and returns correct result`() = runTest {
        // Arrange
        val featureName = "testFeature"
        val exception = RuntimeException("Failed to write to store")
        coEvery { mockStateStore.setLastAttemptedFeature(featureName) } throws exception
        coEvery { mockInstaller.isComponentInstalled(featureName) } returns true // Assume installed for this path

        // Act
        val result = loadFeatureUseCase(featureName)

        // Assert
        assertTrue(result is DFLoadFeatureResult.ProceedToPostInstall) // Should still proceed
        coVerify(exactly = 1) { mockStateStore.setLastAttemptedFeature(featureName) }
        coVerify(exactly = 1) { mockInstaller.isComponentInstalled(featureName) }
        // Verify Log.e was called (optional, depends on strictness)
        coVerify { Log.e("DFLoadFeatureUseCase", "Failed to store last attempted feature '$featureName'", exception) }
    }

    @Test
    fun `invoke when isComponentInstalled throws exception returns Failure`() = runTest {
        // Arrange
        val featureName = "testFeature"
        val exceptionMessage = "Play Core error"
        val exception = RuntimeException(exceptionMessage)
        coEvery { mockStateStore.setLastAttemptedFeature(featureName) } just Runs
        coEvery { mockInstaller.isComponentInstalled(featureName) } throws exception

        // Act
        val result = loadFeatureUseCase(featureName)

        // Assert
        assertTrue(result is DFLoadFeatureResult.Failure)
        val failureResult = result as DFLoadFeatureResult.Failure
        assertEquals(ErrorType.INSTALLATION, failureResult.errorType) // Or ErrorType.UNKNOWN based on SUT
        assertEquals("Failed to determine installation status for $featureName: $exceptionMessage", failureResult.message)
        coVerify(exactly = 1) { mockStateStore.setLastAttemptedFeature(featureName) }
        coVerify(exactly = 1) { mockInstaller.isComponentInstalled(featureName) }
    }
}
