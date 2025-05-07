package com.kuru.featureflow.component.domain

import android.content.Context
import android.util.Log
import com.kuru.featureflow.component.register.DFFeatureConfig
import com.kuru.featureflow.component.register.DFRegistryComponentEntry
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coVerify // For suspend functions if any (not directly in DFInitializeFeatureUseCase mocks)
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DFInitializeFeatureUseCaseTest {

    @MockK
    lateinit var mockRegistry: DFFeatureRegistryUseCase

    @MockK
    lateinit var mockServiceLoaderWrapper: ServiceLoaderWrapper

    @MockK
    lateinit var mockContext: Context

    @MockK
    lateinit var mockApplicationContext: Context // For context.applicationContext

    @MockK
    lateinit var mockClassLoader: ClassLoader

    private lateinit var useCase: DFInitializeFeatureUseCase
    private val testDispatcher = StandardTestDispatcher()

    private val featureName = "testFeature"
    private val dummyConfig = DFFeatureConfig(route = featureName)

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(testDispatcher) // Set main dispatcher for tests

        // Mock Android's Log
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0 // Explicitly type any() when ambiguous
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0


        // Mock context behavior
        every { mockContext.classLoader } returns mockClassLoader
        every { mockContext.applicationContext } returns mockApplicationContext // Ensure applicationContext is also mocked if used

        useCase = DFInitializeFeatureUseCase(mockRegistry, mockServiceLoaderWrapper)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // Reset main dispatcher
        unmockkStatic(Log::class)
    }

    @Test
    fun `runServiceLoaderInitialization - success - one entry initializes and registers config`() = runTest(testDispatcher) {
        val mockEntry = mockk<DFRegistryComponentEntry>()
        every { mockEntry.initialize(mockApplicationContext) } just Runs
        every { mockServiceLoaderWrapper.loadDFComponentEntry(DFRegistryComponentEntry::class.java, mockClassLoader) } returns listOf(mockEntry)
        every { mockRegistry.getConfig(featureName) } returns dummyConfig // Config is found after initialization

        val result = useCase.runServiceLoaderInitialization(featureName, mockContext)

        assertTrue(result)
        verify { Log.d("DFInitializeFeatureUseCase", "Running ServiceLoader initialization for feature: $featureName") }
        verify { mockEntry.initialize(mockApplicationContext) }
        verify { Log.i("DFInitializeFeatureUseCase", "Initialized DFRegistryComponentEntry: ${mockEntry.javaClass.name}") }
        verify { mockRegistry.getConfig(featureName) } // Checked after initialize
        verify { Log.i("DFInitializeFeatureUseCase", "ServiceLoader initialization successful for $featureName") }
    }

    @Test
    fun `runServiceLoaderInitialization - success - multiple entries, one registers config`() = runTest(testDispatcher) {
        val mockEntry1 = mockk<DFRegistryComponentEntry>(name = "MockEntry1")
        val mockEntry2 = mockk<DFRegistryComponentEntry>(name = "MockEntry2")

        // Entry 1 initializes but doesn't immediately lead to config (or we check later)
        every { mockEntry1.initialize(mockApplicationContext) } just Runs
        // Entry 2 initializes and leads to config
        every { mockEntry2.initialize(mockApplicationContext) } just Runs

        every { mockServiceLoaderWrapper.loadDFComponentEntry(DFRegistryComponentEntry::class.java, mockClassLoader) } returns listOf(mockEntry1, mockEntry2)

        // Simulate getConfig returning null for entry1's check, then non-null for entry2's check
        every { mockRegistry.getConfig(featureName) } returnsMany listOf(null, dummyConfig)


        val result = useCase.runServiceLoaderInitialization(featureName, mockContext)

        assertTrue(result)
        verify { mockEntry1.initialize(mockApplicationContext) }
        verify { Log.i("DFInitializeFeatureUseCase", "Initialized DFRegistryComponentEntry: ${mockEntry1.javaClass.name}") }
        verify { Log.w("DFInitializeFeatureUseCase", "DFRegistryComponentEntry ${mockEntry1.javaClass.name} initialized but no config found for $featureName") }

        verify { mockEntry2.initialize(mockApplicationContext) }
        verify { Log.i("DFInitializeFeatureUseCase", "Initialized DFRegistryComponentEntry: ${mockEntry2.javaClass.name}") }

        verify(exactly = 2) { mockRegistry.getConfig(featureName) } // Called after each entry
        verify { Log.i("DFInitializeFeatureUseCase", "ServiceLoader initialization successful for $featureName") }
    }


    @Test
    fun `runServiceLoaderInitialization - no DFRegistryComponentEntry found - returns false`() = runTest(testDispatcher) {
        every { mockServiceLoaderWrapper.loadDFComponentEntry(DFRegistryComponentEntry::class.java, mockClassLoader) } returns emptyList()

        val result = useCase.runServiceLoaderInitialization(featureName, mockContext)

        assertFalse(result)
        verify { Log.d("DFInitializeFeatureUseCase", "Running ServiceLoader initialization for feature: $featureName") }
        verify { Log.w("DFInitializeFeatureUseCase", "No DFRegistryComponentEntry implementations found for $featureName") }
    }

    @Test
    fun `runServiceLoaderInitialization - entry initializes but no config registered by any entry - returns false`() = runTest(testDispatcher) {
        val mockEntry = mockk<DFRegistryComponentEntry>()
        every { mockEntry.initialize(mockApplicationContext) } just Runs
        every { mockServiceLoaderWrapper.loadDFComponentEntry(DFRegistryComponentEntry::class.java, mockClassLoader) } returns listOf(mockEntry)
        every { mockRegistry.getConfig(featureName) } returns null // Config is never found

        val result = useCase.runServiceLoaderInitialization(featureName, mockContext)

        assertFalse(result)
        verify { mockEntry.initialize(mockApplicationContext) }
        verify { Log.i("DFInitializeFeatureUseCase", "Initialized DFRegistryComponentEntry: ${mockEntry.javaClass.name}") }
        verify { mockRegistry.getConfig(featureName) }
        verify { Log.w("DFInitializeFeatureUseCase", "DFRegistryComponentEntry ${mockEntry.javaClass.name} initialized but no config found for $featureName") }
        verify { Log.e("DFInitializeFeatureUseCase", "ServiceLoader ran but failed to register config for $featureName") }
    }

    @Test
    fun `runServiceLoaderInitialization - entry initialize throws exception - returns false`() = runTest(testDispatcher) {
        val mockEntry = mockk<DFRegistryComponentEntry>()
        val exception = RuntimeException("Initialization failed!")
        every { mockEntry.initialize(mockApplicationContext) } throws exception
        every { mockServiceLoaderWrapper.loadDFComponentEntry(DFRegistryComponentEntry::class.java, mockClassLoader) } returns listOf(mockEntry)

        val result = useCase.runServiceLoaderInitialization(featureName, mockContext)

        assertFalse(result)
        verify { mockEntry.initialize(mockApplicationContext) }

        val throwableSlot = slot<Throwable>()
        verify { Log.e("DFInitializeFeatureUseCase", "Failed to initialize DFRegistryComponentEntry ${mockEntry.javaClass.name}", capture(throwableSlot)) }
        assertEquals(exception, throwableSlot.captured)
    }

    @Test
    fun `runServiceLoaderInitialization - serviceLoaderWrapper load throws exception - returns false`() = runTest(testDispatcher) {
        val exception = RuntimeException("ServiceLoader failed!")
        every { mockServiceLoaderWrapper.loadDFComponentEntry(DFRegistryComponentEntry::class.java, mockClassLoader) } throws exception

        val result = useCase.runServiceLoaderInitialization(featureName, mockContext)

        assertFalse(result)
        val throwableSlot = slot<Throwable>()
        verify { Log.e("DFInitializeFeatureUseCase", "Failed to run ServiceLoader for feature $featureName", capture(throwableSlot)) }
        assertEquals(exception, throwableSlot.captured)
    }
}