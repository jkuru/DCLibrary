package com.kuru.featureflow.component.state

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Define DataStore instance via extension property
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "featureflow_settings")

@Singleton // Make the state store a singleton via Hilt
class DFComponentStateStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    // Inject a CoroutineScope for managing internal state flows if needed outside ViewModelScope
    // For simplicity, using a default scope here. Consider providing one via Hilt.
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) // Use Main.immediate for synchronous-like updates
) : DFComponentStateStore {

    init {
        Log.e(TAG, "DFComponentStateStoreImpl Init")
    }
    companion object {
        private const val TAG = "DFComponentStateStoreImpl"
    }
    // --- Preference Keys ---
    private object PreferencesKeys {
        val LAST_ATTEMPTED_FEATURE_URI = stringPreferencesKey("last_attempted_feature_uri")
        // Add keys here if you decide to persist installation/interceptor states
        // Example: fun installationStateKey(feature: String) = stringPreferencesKey("install_state_$feature")
    }

    // --- In-Memory State Flows ---
    // Holds the current installation state for each feature observed or set
    private val _DF_installationStates = MutableStateFlow<Map<String, DFInstallationState>>(emptyMap())
    // Holds interceptor states (kept in-memory)
    private val _DF_interceptorStates = MutableStateFlow<Map<String, DFInterceptorState>>(emptyMap())


    // --- Persistence Methods (Using DataStore) ---

    override suspend fun getLastAttemptedFeature(): String? {
        return readPreference(PreferencesKeys.LAST_ATTEMPTED_FEATURE_URI)
    }

    override suspend fun setLastAttemptedFeature(uri: String) {
        updatePreference(PreferencesKeys.LAST_ATTEMPTED_FEATURE_URI, uri)
    }

    // --- In-Memory State Methods ---

    // Provides the current snapshot of the installation state
    override fun getInstallationState(feature: String): DFInstallationState {
        return _DF_installationStates.value[feature] ?: DFInstallationState.NotInstalled
    }

    // Updates the in-memory state map and notifies observers
    override fun setInstallationState(feature: String, state: DFInstallationState) {
        // Update synchronously on the main thread if called from there, or use scope.launch
        // Using externalScope ensures updates happen even if ViewModel scope is cancelled
        externalScope.launch {
            _DF_installationStates.value = _DF_installationStates.value.toMutableMap().apply {
                this[feature] = state
            }
        }
    }

    // Provides a flow that emits updates for a specific feature's installation state
    override fun getInstallationStateFlow(feature: String): StateFlow<DFInstallationState> {
        return _DF_installationStates
            .map { stateMap -> stateMap[feature] ?: DFInstallationState.NotInstalled }
            .distinctUntilChanged() // Only emit when the state for this feature actually changes
            .stateIn(
                scope = externalScope, // Use the injected scope
                started = SharingStarted.WhileSubscribed(5000), // Keep active for 5s after last subscriber
                initialValue = getInstallationState(feature) // Start with the current known state
            )
    }


    // Interceptor state methods (remain in-memory)
    override fun getInterceptorState(interceptorId: String): DFInterceptorState {
        return _DF_interceptorStates.value[interceptorId] ?: DFInterceptorState.Inactive
    }

    override fun setInterceptorState(interceptorId: String, state: DFInterceptorState) {
        externalScope.launch {
            _DF_interceptorStates.value = _DF_interceptorStates.value.toMutableMap().apply {
                this[interceptorId] = state
            }
        }
    }


    // --- DataStore Helper Functions ---
    private suspend fun <T> readPreference(key: Preferences.Key<T>): T? {
        return try {
            val preferences = context.dataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        emit(emptyPreferences()) // Handle error reading preferences
                    } else {
                        throw exception
                    }
                }.first() // Get the first/current value
            preferences[key]
        } catch (e: Exception) {
            // Handle exceptions during read
            null
        }
    }

    private suspend fun <T> updatePreference(key: Preferences.Key<T>, value: T) {
        try {
            context.dataStore.edit { preferences ->
                preferences[key] = value
            }
        } catch (e: Exception) {
            // Handle exceptions during write
        }
    }
}