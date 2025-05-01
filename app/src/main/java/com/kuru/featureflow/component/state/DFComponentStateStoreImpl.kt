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

@Singleton
class DFComponentStateStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val externalScope: CoroutineScope
) : DFComponentStateStore {

    init {
        Log.e(TAG, "DFComponentStateStoreImpl Init")
    }

    companion object {
        private const val TAG = "DFComponentStateStoreImpl"
    }

    // --- Preference Keys ---
    private object PreferencesKeys {
        /**
         * Aspect	LAST_ATTEMPTED_FEATURE_URI	|| LAST_PROCESSED_URI
         * Scope	Tracks the feature being installed or loaded.	|| Tracks the URI last handled by the activity.
         * Purpose	Enables retry or resumption of feature installation.	|| Prevents redundant processing of the same URI.
         * Usage Context	Feature installation logic (e.g., in a view model).	|| Intent/URI processing (e.g., in an activity).
         * Functionality	Supports resuming interrupted feature loads.	|| Ensures idempotency in handling deep links/intents.
         */
        val LAST_ATTEMPTED_FEATURE_URI = stringPreferencesKey("last_attempted_feature_uri")
        val LAST_PROCESSED_URI = stringPreferencesKey("last_processed_uri")
    }

    // --- In-Memory State Flows ---
    // Holds the current installation state for each feature observed or set
    private val _DF_installationStates =
        MutableStateFlow<Map<String, DFInstallationState>>(emptyMap())

    // Holds interceptor states (kept in-memory)
    private val _DF_interceptorStates =
        MutableStateFlow<Map<String, DFInterceptorState>>(emptyMap())


    // --- Persistence Methods (Using DataStore) ---
    // Retrieves the last attempted feature URI from DataStore
    // Purpose: Persists the feature URI across app restarts to track unfinished installations
    override suspend fun getLastAttemptedFeature(): String? {
        return readPreference(PreferencesKeys.LAST_ATTEMPTED_FEATURE_URI)
    }

    // Stores the last attempted feature URI in DataStore
    // Why Persisted: Ensures the app remembers which feature was being installed if the user closes the app mid-download,
    // allowing resumption or retry on restart
    override suspend fun setLastAttemptedFeature(uri: String) {
        updatePreference(PreferencesKeys.LAST_ATTEMPTED_FEATURE_URI, uri)
    }

    // --- In-Memory State Methods ---

    // Returns the current installation state of a feature from the in-memory map
    // Note: This state is transient and lost on app closure; SplitInstallManager handles persistence of installation progress
    override fun getInstallationState(feature: String): DFInstallationState {
        return _DF_installationStates.value[feature] ?: DFInstallationState.NotInstalled
    }

    // Updates the in-memory installation state and notifies observers via StateFlow
    // Purpose: Tracks real-time installation progress during the app's runtime
    override fun setInstallationState(feature: String, state: DFInstallationState) {
        externalScope.launch {
            _DF_installationStates.value = _DF_installationStates.value.toMutableMap().apply {
                this[feature] = state
            }
        }
    }

    // Provides a StateFlow for observing a feature's installation state changes
    // Usage: UI components can subscribe to this flow to update in real-time
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


    // Returns the current interceptor state from the in-memory map
    // Note: Interceptor states are transient and tied to the current session, lost on app closure
    override fun getInterceptorState(interceptorId: String): DFInterceptorState {
        return _DF_interceptorStates.value[interceptorId] ?: DFInterceptorState.Inactive
    }

    // Updates the in-memory interceptor state and notifies observers
    // Purpose: Manages interceptor logic during the current installation process
    override fun setInterceptorState(interceptorId: String, state: DFInterceptorState) {
        externalScope.launch {
            _DF_interceptorStates.value = _DF_interceptorStates.value.toMutableMap().apply {
                this[interceptorId] = state
            }
        }
    }


    // --- DataStore Helper Functions ---
    // Reads a preference value from DataStore with error handling
    // Persistence Benefit: Ensures robust access to the last attempted feature URI even after app restarts
    private suspend fun <T> readPreference(key: Preferences.Key<T>): T? {
        return try {
            val preferences = context.dataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        emit(emptyPreferences()) // Handle error reading preferences
                    } else {
                        Log.e(TAG, "readPreference failed $exception")
                    }
                }.first() // Get the first/current value
            preferences[key]
        } catch (e: Exception) {
            Log.e(TAG, "readPreference failed $e")
            null
        }
    }

    // Updates a preference value in DataStore with error handling
    // Why Used: Saves the last attempted feature URI persistently to survive app closures
    private suspend fun <T> updatePreference(key: Preferences.Key<T>, value: T) {
        try {
            context.dataStore.edit { preferences ->
                preferences[key] = value
            }
        } catch (e: Exception) {
            Log.e(TAG, "updatePreference failed $e")
        }
    }

    // --- Scenario: User Closes App Mid-Download ---
    // - Persistent State (DataStore): The last_attempted_feature_uri remains stored, allowing the app to identify
    //   which feature was being installed upon restart.
    // - Recovery: On restart, the app can read this URI, query SplitInstallManager for the installation status,
    //   and decide to resume, retry, or notify the user.
    // - In-Memory State (StateFlow): Installation and interceptor states are lost on closure but can be
    //   recreated from SplitInstallManager's persisted installation progress, ensuring continuity.
}