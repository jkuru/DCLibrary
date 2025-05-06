package com.kuru.featureflow.component.serviceloader

import android.content.Context
import android.util.Log
import com.kuru.featureflow.component.register.DFComponentConfig
import com.kuru.featureflow.component.register.DFComponentEntry
import com.kuru.featureflow.component.register.DFComponentRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class DFServiceLoaderManagerTest {

    private lateinit var registry: DFComponentRegistry
    private lateinit var serviceLoaderWrapper: ServiceLoaderWrapper
    private lateinit var context: Context
    private lateinit var serviceLoaderManager: DFServiceLoaderManager
    private lateinit var mockEntry: DFComponentEntry

    @Before
    fun setUp() {
        // Set up coroutine dispatcher for testing
        Dispatchers.setMain(Dispatchers.Unconfined)

        // Mock dependencies
        registry = mockk()
        serviceLoaderWrapper = mockk()
        context = mockk()
        mockEntry = mockk()

        // Initialize DFServiceLoaderManager with mocked dependencies
        serviceLoaderManager = DFServiceLoaderManager(registry, serviceLoaderWrapper)

        // Mock context.classLoader and applicationContext
        every { context.classLoader } returns ClassLoader.getSystemClassLoader()
        every { context.applicationContext } returns context
        mockkStatic(Log::class)
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
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
        // Reset coroutine dispatcher
        Dispatchers.resetMain()
    }

    @Test
    fun `runServiceLoaderInitialization returns true when entry initializes and config is found`() = runTest {
        // Arrange
        val feature = "feature_test"
        val config = DFComponentConfig(route = feature)

        // Mock ServiceLoaderWrapper to return a list with our mock entry
        every { serviceLoaderWrapper.loadDFComponentEntry(DFComponentEntry::class.java, any()) } returns listOf(mockEntry)

        // Mock entry initialization
        every { mockEntry.initialize(context) } returns Unit

        // Mock registry to return a config
        coEvery { registry.getConfig(feature) } returns config

        // Act
        val result = serviceLoaderManager.runServiceLoaderInitialization(feature, context)

        // Assert
        assertTrue(result)
        verify { mockEntry.initialize(context) }
        verify { registry.getConfig(feature) }
        verify { serviceLoaderWrapper.loadDFComponentEntry(DFComponentEntry::class.java, any()) }
    }

    @Test
    fun `runServiceLoaderInitialization returns false when no entries are found`() = runTest {
        // Arrange
        val feature = "feature_test"

        // Mock ServiceLoaderWrapper to return an empty list
        every { serviceLoaderWrapper.loadDFComponentEntry(DFComponentEntry::class.java, any()) } returns emptyList()

        // Act
        val result = serviceLoaderManager.runServiceLoaderInitialization(feature, context)

        // Assert
        assertFalse(result)
        verify(exactly = 0) { registry.getConfig(any()) }
        verify { serviceLoaderWrapper.loadDFComponentEntry(DFComponentEntry::class.java, any()) }
    }

    @Test
    fun `runServiceLoaderInitialization returns false when entry initializes but config is not found`() = runTest {
        // Arrange
        val feature = "feature_test"

        // Mock ServiceLoaderWrapper to return a list with our mock entry
        every { serviceLoaderWrapper.loadDFComponentEntry(DFComponentEntry::class.java, any()) } returns listOf(mockEntry)

        // Mock entry initialization
        every { mockEntry.initialize(context) } returns Unit

        // Mock registry to return null
        coEvery { registry.getConfig(feature) } returns null

        // Act
        val result = serviceLoaderManager.runServiceLoaderInitialization(feature, context)

        // Assert
        assertFalse(result)
        verify { mockEntry.initialize(context) }
        verify { registry.getConfig(feature) }
        verify { serviceLoaderWrapper.loadDFComponentEntry(DFComponentEntry::class.java, any()) }
    }

    @Test
    fun `runServiceLoaderInitialization returns false when entry initialization throws exception`() = runTest {
        // Arrange
        val feature = "feature_test"

        // Mock ServiceLoaderWrapper to return a list with our mock entry
        every { serviceLoaderWrapper.loadDFComponentEntry(DFComponentEntry::class.java, any()) } returns listOf(mockEntry)

        // Mock entry initialization to throw an exception
        every { mockEntry.initialize(context) } throws RuntimeException("Initialization failed")

        // Act
        val result = serviceLoaderManager.runServiceLoaderInitialization(feature, context)

        // Assert
        assertFalse(result)
        verify { mockEntry.initialize(context) }
        verify(exactly = 0) { registry.getConfig(any()) }
        verify { serviceLoaderWrapper.loadDFComponentEntry(DFComponentEntry::class.java, any()) }
    }

    @Test
    fun `runServiceLoaderInitialization returns false when ServiceLoaderWrapper throws exception`() = runTest {
        // Arrange
        val feature = "feature_test"

        // Mock ServiceLoaderWrapper to throw an exception
        every { serviceLoaderWrapper.loadDFComponentEntry(DFComponentEntry::class.java, any()) } throws RuntimeException("ServiceLoader failed")

        // Act
        val result = serviceLoaderManager.runServiceLoaderInitialization(feature, context)

        // Assert
        assertFalse(result)
        verify(exactly = 0) { registry.getConfig(any()) }
        verify { serviceLoaderWrapper.loadDFComponentEntry(DFComponentEntry::class.java, any()) }
    }
}