package com.kuru.featureflow.component.state

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.coVerify
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
class DFComponentStateStoreImplTest {

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var externalScope: CoroutineScope
    private lateinit var stateStore: DFComponentStateStoreImpl
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Before
    fun setUp() {
        // Mock dependencies
        context = mockk()
        dataStore = mockk()
        externalScope = TestScope(testDispatcher + Job())

        // Initialize state store
        stateStore = DFComponentStateStoreImpl(context, externalScope)

        // Mock logging to avoid issues with Log calls
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

    @Test
    fun `getLastAttemptedFeature returns uri when present in DataStore`() = runTest {
        // Arrange
        val key = stringPreferencesKey("last_attempted_feature_uri")
        val uri = "feature://test"
        val preferences = mockk<Preferences>()
        every { preferences[key] } returns uri
        coEvery { dataStore.data } returns flowOf(preferences)

        // Act
        val result = stateStore.getLastAttemptedFeature()

        // Assert
        assertEquals(uri, result)
    }

    @Test
    fun `getLastAttemptedFeature returns null when uri not present in DataStore`() = runTest {
        // Arrange
        val key = stringPreferencesKey("last_attempted_feature_uri")
        val preferences = mockk<Preferences>()
        every { preferences[key] } returns null
        coEvery { dataStore.data } returns flowOf(preferences)

        // Act
        val result = stateStore.getLastAttemptedFeature()

        // Assert
        assertNull(result)
    }

    @Test
    fun `getLastAttemptedFeature returns null on IOException`() = runTest {
        // Arrange
        coEvery { dataStore.data } returns flow {
            throw IOException("DataStore read error")
        }

        // Act
        val result = stateStore.getLastAttemptedFeature()

        // Assert
        assertNull(result)
    }

    @Test
    fun `setLastAttemptedFeature stores uri in DataStore`() = runTest {
        // Arrange
        val key = stringPreferencesKey("last_attempted_feature_uri")
        val uri = "feature://test"
        val transformSlot = slot<suspend (MutablePreferences) -> Unit>()
        val mutablePreferences = mockk<MutablePreferences>(relaxed = true)
        coEvery { dataStore.edit(capture(transformSlot)) } coAnswers {
            transformSlot.captured(mutablePreferences)
            mutablePreferences
        }

        // Act
        stateStore.setLastAttemptedFeature(uri)
        advanceUntilIdle() // Ensure coroutine completes

        // Assert
        coVerify { mutablePreferences[key] = uri }
        coVerify { dataStore.edit(any()) }
    }

    @Test
    fun `setLastAttemptedFeature handles DataStore exception gracefully`() = runTest {
        // Arrange
        val transformSlot = slot<suspend (MutablePreferences) -> Unit>()
        coEvery { dataStore.edit(capture(transformSlot)) } coAnswers {
            throw IOException("DataStore write error")
        }

        // Act
        stateStore.setLastAttemptedFeature("feature://test")
        advanceUntilIdle() // Ensure coroutine completes

        // Assert
        coVerify { dataStore.edit(any()) }
        // No assertion on exception, as it should be logged internally
    }

    @Test
    fun `getInstallationState returns NotInstalled when feature not set`() = runTest {
        // Arrange
        val feature = "test_feature"

        // Act
        val result = stateStore.getInstallationState(feature)

        // Assert
        assertEquals(DFInstallationState.NotInstalled, result)
    }

    @Test
    fun `setInstallationState updates state and getInstallationState returns it`() = runTest {
        // Arrange
        val feature = "test_feature"
        val state = DFInstallationState.Installed

        // Act
        stateStore.setInstallationState(feature, state)
        advanceUntilIdle() // Ensure coroutine completes
        val result = stateStore.getInstallationState(feature)

        // Assert
        assertEquals(state, result)
    }

    @Test
    fun `getInstallationStateFlow emits NotInstalled initially`() = runTest {
        // Arrange
        val feature = "test_feature"

        // Act
        val flow = stateStore.getInstallationStateFlow(feature)
        val result = flow.first()

        // Assert
        assertEquals(DFInstallationState.NotInstalled, result)
    }

    @Test
    fun `getInstallationStateFlow emits updated state after setInstallationState`() = runTest {
        // Arrange
        val feature = "test_feature"
        val state = DFInstallationState.Installed

        // Act
        stateStore.setInstallationState(feature, state)
        advanceUntilIdle() // Ensure coroutine completes
        val flow = stateStore.getInstallationStateFlow(feature)
        val result = flow.first()

        // Assert
        assertEquals(state, result)
    }

    @Test
    fun `getInterceptorState returns Inactive when interceptor not set`() = runTest {
        // Arrange
        val interceptorId = "test_interceptor"

        // Act
        val result = stateStore.getInterceptorState(interceptorId)

        // Assert
        assertEquals(DFInterceptorState.Inactive, result)
    }

    @Test
    fun `setInterceptorState updates state and getInterceptorState returns it`() = runTest {
        // Arrange
        val interceptorId = "test_interceptor"
        val state = DFInterceptorState.Completed

        // Act
        stateStore.setInterceptorState(interceptorId, state)
        advanceUntilIdle() // Ensure coroutine completes
        val result = stateStore.getInterceptorState(interceptorId)

        // Assert
        assertEquals(state, result)
    }

    @Test
    fun `setInterceptorState with Failed state stores message`() = runTest {
        // Arrange
        val interceptorId = "test_interceptor"
        val errorMessage = "Interceptor failed"
        val state = DFInterceptorState.Failed(errorMessage)

        // Act
        stateStore.setInterceptorState(interceptorId, state)
        advanceUntilIdle() // Ensure coroutine completes
        val result = stateStore.getInterceptorState(interceptorId)

        // Assert
        assertIs<DFInterceptorState.Failed>(result)
        assertEquals(errorMessage, result.message)
    }
}