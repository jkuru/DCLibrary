package com.kuru.featureflow.component.domain

import android.util.Log
import com.kuru.featureflow.component.state.DFStateStore
import com.kuru.featureflow.component.ui.ErrorType // Import the assumed ErrorType
import io.mockk.* // Import base MockK functions
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After // Using JUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DFLoadFeatureUseCaseTest {

    @MockK
    lateinit var mockInstaller: DFFeatureInstaller

    @MockK
    lateinit var mockStateStore: DFStateStore

    private lateinit var useCase: DFLoadFeatureUseCase

    private val featureName = "testFeature"

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true) // Initialize mocks

        // Mock Android Log static methods
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0 // Specify type for any() if needed
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0

        useCase = DFLoadFeatureUseCase(mockInstaller, mockStateStore)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class) // Unmock static Log calls
    }

    @Test
    fun `invoke - given blank feature name - returns Failure with VALIDATION error`() = runTest {
        // Given
        val blankFeature = "  "

        // When
        val result = useCase(blankFeature)

        // Then
        assertIs<DFLoadFeatureResult.Failure>(result)
        assertEquals(ErrorType.VALIDATION, result.errorType)
        assertEquals("Feature name cannot be empty", result.message)
        verify { Log.w("DFLoadFeatureUseCase", "Feature name cannot be blank.") }
        coVerify(exactly = 0) { mockStateStore.setLastAttemptedFeature(any()) }
        coVerify(exactly = 0) { mockInstaller.isFeatureInstalled(any()) }
    }

    @Test
    fun `invoke - given valid feature is installed - returns ProceedToPostInstall`() = runTest {
        // Given
        coEvery { mockStateStore.setLastAttemptedFeature(featureName) } just Runs // Mock suspend function
        coEvery { mockInstaller.isFeatureInstalled(featureName) } returns true // Mock suspend function

        // When
        val result = useCase(featureName)

        // Then
        assertIs<DFLoadFeatureResult.ProceedToPostInstall>(result)
        coVerify(exactly = 1) { mockStateStore.setLastAttemptedFeature(featureName) }
        coVerify(exactly = 1) { mockInstaller.isFeatureInstalled(featureName) }
        verify { Log.i("DFLoadFeatureUseCase", "Initiating load check for feature: $featureName") }
        verify { Log.d("DFLoadFeatureUseCase", "Stored '$featureName' as last attempted feature.") }
        verify { Log.d("DFLoadFeatureUseCase", "Feature '$featureName' installed status: true") }
    }

    @Test
    fun `invoke - given valid feature is NOT installed - returns ProceedToInstallationMonitoring`() = runTest {
        // Given
        coEvery { mockStateStore.setLastAttemptedFeature(featureName) } just Runs
        coEvery { mockInstaller.isFeatureInstalled(featureName) } returns false // Feature not installed

        // When
        val result = useCase(featureName)

        // Then
        assertIs<DFLoadFeatureResult.ProceedToInstallationMonitoring>(result)
        coVerify(exactly = 1) { mockStateStore.setLastAttemptedFeature(featureName) }
        coVerify(exactly = 1) { mockInstaller.isFeatureInstalled(featureName) }
        verify { Log.i("DFLoadFeatureUseCase", "Initiating load check for feature: $featureName") }
        verify { Log.d("DFLoadFeatureUseCase", "Stored '$featureName' as last attempted feature.") }
        verify { Log.d("DFLoadFeatureUseCase", "Feature '$featureName' installed status: false") }
    }

    @Test
    fun `invoke - when setLastAttemptedFeature fails - continues and returns based on install status`() = runTest {
        // Given
        val storageException = IOException("Disk full")
        coEvery { mockStateStore.setLastAttemptedFeature(featureName) } throws storageException
        // Assume installer check succeeds despite storage error
        coEvery { mockInstaller.isFeatureInstalled(featureName) } returns true

        // When
        val result = useCase(featureName)

        // Then
        // Verify logging of the storage error
        verify { Log.e("DFLoadFeatureUseCase", "Failed to store last attempted feature '$featureName'", storageException) }
        // Verify installer was still called
        coVerify(exactly = 1) { mockInstaller.isFeatureInstalled(featureName) }
        // Verify the result is based on the installer status (ProceedToPostInstall in this case)
        assertIs<DFLoadFeatureResult.ProceedToPostInstall>(result)
    }

    @Test
    fun `invoke - when isFeatureInstalled fails - returns Failure with INSTALLATION error`() = runTest {
        // Given
        val installCheckException = RuntimeException("SplitInstallManager unavailable")
        coEvery { mockStateStore.setLastAttemptedFeature(featureName) } just Runs
        coEvery { mockInstaller.isFeatureInstalled(featureName) } throws installCheckException // Installer check fails

        // When
        val result = useCase(featureName)

        // Then
        assertIs<DFLoadFeatureResult.Failure>(result)
        assertEquals(ErrorType.INSTALLATION, result.errorType)
        assertTrue(result.message.contains("Failed to determine installation status for $featureName"))
        assertTrue(result.message.contains(installCheckException.message ?: ""))
        coVerify(exactly = 1) { mockStateStore.setLastAttemptedFeature(featureName) }
        coVerify(exactly = 1) { mockInstaller.isFeatureInstalled(featureName) }
        verify { Log.e("DFLoadFeatureUseCase", "Failed to check installation status for feature '$featureName'", installCheckException) }
    }
}