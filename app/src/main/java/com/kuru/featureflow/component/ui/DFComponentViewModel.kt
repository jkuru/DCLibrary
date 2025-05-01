package com.kuru.featureflow.component.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.kuru.featureflow.component.googleplay.DFComponentInstaller
import com.kuru.featureflow.component.register.DFComponentEntry
import com.kuru.featureflow.component.register.DFComponentRegistry
import com.kuru.featureflow.component.state.DFComponentStateStore
import com.kuru.featureflow.component.state.DFErrorCode
import com.kuru.featureflow.component.state.DFInstallationState
import com.kuru.featureflow.component.state.DFInterceptorState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Import for Dispatchers.IO
import java.util.ServiceLoader
import javax.inject.Inject

// --- DFComponentState Definition (Remains the same) ---
sealed class DFComponentState {
    data object Loading : DFComponentState()
    data class Error(
        val message: String,
        val errorType: ErrorType,
        val feature: String? = null,
        val dfErrorCode: DFErrorCode? = null
    ) : DFComponentState()
    data class RequiresConfirmation(val feature: String) : DFComponentState() // No change here
    data class Success(
        val feature: String,
        val featureInstallationState: DFInstallationState // Usually Installed
    ) : DFComponentState()
}


// --- ErrorType Enum (Remains the same) ---
enum class ErrorType {
    NETWORK, STORAGE, API_UNAVAILABLE, INSTALLATION, VALIDATION,
    SERVICE_LOADER, UNKNOWN, PRE_INSTALL_INTERCEPTOR, POST_INSTALL_INTERCEPTOR
}

// --- DFComponentIntent Definition (Remains the same) ---
sealed class DFComponentIntent {
    data class LoadFeature(val feature: String) : DFComponentIntent()
    object Retry : DFComponentIntent()
    data class ConfirmInstallation(val feature: String): DFComponentIntent()
}


@HiltViewModel
class DFComponentViewModel @Inject constructor(
    private val stateStore: DFComponentStateStore,
    private val registry: DFComponentRegistry,
    private val installer: DFComponentInstaller,
    @ApplicationContext private val context: Context // Keep ApplicationContext
) : ViewModel() {

    companion object {
        private const val TAG = "DFComponentViewModel"
    }

    // --- State Flows ---
    private val _currentFeature = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow<DFComponentState>(DFComponentState.Loading)
    val uiState: StateFlow<DFComponentState> = _uiState.asStateFlow()

    // --- ADD StateFlow for the Dynamic Screen Composable ---
    private val _dynamicScreenContent = MutableStateFlow<(@Composable (NavController) -> Unit)?>(null)
    val dynamicScreenContent: StateFlow<(@Composable (NavController) -> Unit)?> = _dynamicScreenContent.asStateFlow()
    private val processedFeatures = mutableSetOf<String>()

    private var currentInstallJob: Job? = null
    // --- ADDED: Store for the raw Play Core state when confirmation is needed ---
    private var pendingConfirmationState: SplitInstallSessionState? = null
    // ---

    // --- ADDED: Getter for the Activity to retrieve the state ---
    fun getPendingConfirmationSessionState(): SplitInstallSessionState? {
        return pendingConfirmationState
    }
    // ---

    // --- ADD Function to clear dynamic content when navigating away or reloading ---
    fun clearDynamicContent() {
        Log.e(TAG, "Clearing dynamic screen content.")
        _dynamicScreenContent.value = null
        // Also reset UI state if appropriate, e.g., back to loading for next feature?
        // _uiState.value = DFComponentState.Loading // Optional: Reset UI state
    }
    // ---

    fun processIntent(intent: DFComponentIntent) {
        Log.e(TAG, "Processing intent: $intent")
        // Clear previous dynamic content when loading a new feature
        if (intent is DFComponentIntent.LoadFeature) {
            clearDynamicContent()
        }
        when (intent) {
            is DFComponentIntent.LoadFeature -> loadFeature(intent.feature)
            is DFComponentIntent.Retry -> {
                clearDynamicContent() // Clear content on retry as well
                retryLastFeature()
            }
            is DFComponentIntent.ConfirmInstallation -> handleInstallationConfirmed(intent.feature)
        }
    }


    private fun loadFeature(feature: String) {
        // Skip if feature is already processed successfully
        if (processedFeatures.contains(feature) && uiState.value is DFComponentState.Success) {
            Log.d(TAG, "Skipping loadFeature for already processed feature: $feature")
            return
        }
        if (_currentFeature.value != feature) {
            currentInstallJob?.cancel(CancellationException("New feature load requested: $feature"))
            currentInstallJob = null
            Log.e(TAG,"Cancelled previous job for feature: ${_currentFeature.value}")
            // Clear pending confirmation state if feature changes
            pendingConfirmationState = null
        }
        _currentFeature.value = feature

        currentInstallJob = viewModelScope.launch {
            try {
                stateStore.setLastAttemptedFeature(feature)

                if (feature.isBlank()) {
                    handleErrorState(feature, ErrorType.VALIDATION, "Feature name cannot be empty")
                    return@launch
                }

                if (installer.isComponentInstalled(feature)) {
                    Log.e(TAG, "Feature $feature already installed. Running post-install steps.")
                    // Ensure registration happened (idempotent) - Pass context
                    try {
                        runServiceLoaderInitialization(feature, context) // Pass context
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to run ServiceLoader for feature $feature", e)
                        throw e
                    }
                    // Run post-install interceptors (idempotent)
                    runPostInstallInterceptors(feature) // Needs to run within a coroutine if using withContext
                    // --- Fetch and set screen content for already installed feature ---
                    fetchAndSetDynamicScreen(feature)
                    processedFeatures.add(feature)
                } else {
                    Log.e(TAG, "Feature $feature not installed. Starting installation process.")
                    initiateInstallation(feature)
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "LoadFeature job cancelled for $feature: ${e.message}")
                // Don't treat cancellation as an error unless necessary
                // _uiState.value = DFComponentState.Loading // Or map to a specific cancelled state if needed
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during loadFeature for $feature", e)
                handleErrorState(feature, ErrorType.UNKNOWN, "Failed to load feature: ${e.message}")
            }
        }
    }

    private fun initiateInstallation(feature: String) {
        // Cancel potentially running job for the *same* feature if needed (e.g., rapid retry)
        if (currentInstallJob?.isActive == true && _currentFeature.value == feature) {
            Log.w(TAG, "Installation already in progress for $feature? Restarting flow.")
            // currentInstallJob?.cancel("Restarting installation explicitly") // Optional: Decide if explicit restart needed
        }

        // Clear any lingering confirmation state from previous attempts for this feature
        pendingConfirmationState = null

        currentInstallJob = viewModelScope.launch { // Launch specific job for collection
            try {
                Log.e(TAG, "Initiating installation flow collection for: $feature")
                _uiState.value = DFComponentState.Loading // Show loading

                // Run generic pre-install checks (consider moving after config load if needed)
                if (!runGenericPreInstallChecks(feature)) {
                    return@launch
                }

                // --- Collect InstallProgress ---
                installer.installComponent(feature) // Returns Flow<InstallProgress>
                    .distinctUntilChanged()
                    .collect { installProgress -> // Now collecting InstallProgress

                        val frameworkState = installProgress.frameworkState
                        val playCoreState = installProgress.playCoreState // May be null

                        Log.e(TAG, "Received installation state for $feature: $frameworkState")

                        // --- Store Play Core state if confirmation is required ---
                        if (frameworkState is DFInstallationState.RequiresConfirmation) {
                            pendingConfirmationState = playCoreState
                            Log.e(TAG, "Stored pendingConfirmationState for feature $feature, Session ID: ${playCoreState?.sessionId()}")
                        } else if (pendingConfirmationState != null && frameworkState !is DFInstallationState.Pending) {
                            // Clear the state if no longer requiring confirmation or pending
                            // Avoid clearing immediately on Pending, wait for next state.
                            Log.e(TAG, "Clearing pendingConfirmationState for feature $feature as state is $frameworkState")
                            pendingConfirmationState = null
                        }
                        // ---

                        // Update central store with framework state
                        stateStore.setInstallationState(feature, frameworkState)
                        // Update UI state based on framework state
                        updateUiStateFromInstallationState(feature, frameworkState)

                        // Handle terminal states
                        if (frameworkState is DFInstallationState.Installed) {
                            Log.e(TAG, "Installation successful for $feature. Running post-install steps.")
                            runServiceLoaderInitialization(feature, context) // Pass context
                            runPostInstallInterceptors(feature) // Needs coroutine context
                            // --- Fetch and set screen content after installation ---
                            fetchAndSetDynamicScreen(feature)
                            processedFeatures.add(feature) // Mark as processed
                        } else if (frameworkState is DFInstallationState.Failed) {
                            Log.e(TAG, "Installation failed for $feature with code: ${frameworkState.errorCode}")
                        } else if (frameworkState is DFInstallationState.Canceled) {
                            Log.w(TAG, "Installation canceled for $feature.")
                        }
                    }
            } catch (e: CancellationException) {
                Log.i(TAG, "Installation flow collection cancelled for $feature: ${e.message}")
                if (_uiState.value !is DFComponentState.Success) {
                    _uiState.value = DFComponentState.Loading
                }
                pendingConfirmationState = null // Clear on cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Error during installation flow collection for $feature", e)
                handleErrorState(feature, ErrorType.UNKNOWN, "An unexpected error occurred during installation: ${e.message}")
                pendingConfirmationState = null // Clear on error
            }
        }
    }

    // --- ADDED: Function to fetch screen lambda from registry ---
    private fun fetchAndSetDynamicScreen(feature: String) {
        Log.e(TAG, "Attempting to fetch screen lambda for feature: $feature")
        val config = registry.getConfig(feature)
        if (config != null) {
            val screenLambda = registry.getScreen(config)
            if (screenLambda != null) {
                _dynamicScreenContent.value = screenLambda // Update the state flow
                _uiState.value = DFComponentState.Success(feature, DFInstallationState.Installed) // Set success state
                Log.i(TAG, "Successfully fetched and set dynamic screen content for $feature.")
            } else {
                Log.e(TAG,"Feature $feature config found, but screen lambda is null in registry.")
                handleErrorState(feature, ErrorType.INSTALLATION, "Failed to load screen content for $feature.")
            }
        } else {
            // This might happen if ServiceLoader initialization failed or was slow
            Log.e(TAG,"Feature $feature config not found in registry after installation/check. Cannot get screen.")
            handleErrorState(feature, ErrorType.SERVICE_LOADER, "Feature configuration missing for $feature.")
        }
    }
    // ---

    private fun runGenericPreInstallChecks(feature: String): Boolean {
        Log.e(TAG, "Running generic pre-install checks for $feature")
        // Example: Check network, storage etc.
        return true
    }

    private fun retryLastFeature() {
        viewModelScope.launch {
            val lastFeature = stateStore.getLastAttemptedFeature()
            if (lastFeature != null) {
                Log.e(TAG, "Retrying feature: $lastFeature")
                loadFeature(lastFeature) // This will cancel existing job and restart
            } else {
                Log.w(TAG, "No last attempted feature found to retry.")
                _uiState.value = DFComponentState.Error("No feature to retry", ErrorType.VALIDATION, null)
            }
        }
    }

    // Renamed from confirmInstallation for clarity - this is called *after* user confirms in Play dialog
    private fun handleInstallationConfirmed(feature: String) {
        Log.e(TAG, "User confirmation handled for feature: $feature. Installation should resume automatically via listener.")
        // Clear the locally stored state as Play Core takes over now
        pendingConfirmationState = null
        // Optionally update UI state if needed, e.g., back to Loading
        if (_uiState.value is DFComponentState.RequiresConfirmation) {
            _uiState.value = DFComponentState.Loading
        }
    }

    // Pass context for ServiceLoader initialization
    private fun runServiceLoaderInitialization(feature: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.e(TAG, "Running ServiceLoader initialization for feature: $feature on ${Thread.currentThread().name}")
                // Ensure the correct classloader is used, especially after module install
                val classLoader = context.classLoader
                val serviceLoader = ServiceLoader.load(DFComponentEntry::class.java, classLoader)
                var foundAndInitialized = false
                serviceLoader.forEach { entry ->
                    try {
                        // Pass context if DFComponentEntry interface requires it
                        entry.initialize(context.applicationContext) // Pass ApplicationContext
                        Log.i(TAG, "Initialized DFComponentEntry: ${entry.javaClass.name}")
                        if (registry.getConfig(feature) != null) {
                            foundAndInitialized = true
                            // Break loop if only one entry per feature is expected?
                        } else {
                            Log.w(TAG, "DFComponentEntry ${entry.javaClass.name} initialized but config for $feature not found in registry.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize DFComponentEntry ${entry.javaClass.name}", e)
                    }
                }
                if (!foundAndInitialized) {
                    Log.e(TAG, "ServiceLoader ran, but failed to find/initialize/register config for $feature")
                    handleErrorState(feature, ErrorType.SERVICE_LOADER, "Feature components failed to initialize.", DFErrorCode.INTERNAL_ERROR)
                } else {
                    Log.i(TAG, "ServiceLoader initialization appears successful for $feature")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run ServiceLoader for feature $feature", e)
                handleErrorState(feature, ErrorType.SERVICE_LOADER, "Failed to load feature components.")
            }
        }
    }


    // Needs to run within a coroutine scope to use withContext
    private suspend fun runPreInstallInterceptors(feature: String): Boolean {
        val config = registry.getConfig(feature) ?: run {
            Log.w(TAG, "Cannot run pre-install interceptors: Config not found for $feature")
            return true
        }
        val preInstallInterceptors = config.listOfDFComponentInterceptor.filter { it.preInstall }
        Log.e(TAG, "Running ${preInstallInterceptors.size} pre-install interceptors for $feature")
        for ((index, interceptor) in preInstallInterceptors.withIndex()) {
            val interceptorId = "$feature-pre-$index"
            stateStore.setInterceptorState(interceptorId, DFInterceptorState.Active)
            val result = try {
                // Use withContext for potentially blocking tasks
                withContext(Dispatchers.IO) { interceptor.task() }
            } catch (e: Exception) {
                Log.e(TAG, "Pre-install interceptor $index threw exception for $feature", e)
                false
            }
            val finalState = if (result) DFInterceptorState.Completed else DFInterceptorState.Failed("Pre-install interceptor $index failed")
            stateStore.setInterceptorState(interceptorId, finalState)

            if (!result) {
                val error = "Pre-install check failed for $feature"
                Log.e(TAG, error)
                // Switch back to main thread to update UI state safely
                withContext(Dispatchers.Main) {
                    handleErrorState(feature, ErrorType.PRE_INSTALL_INTERCEPTOR, error)
                }
                return false
            }
        }
        return true
    }

    // Needs to run within a coroutine scope to use withContext
    private suspend fun runPostInstallInterceptors(feature: String) {
        val config = registry.getConfig(feature) ?: run {
            Log.w(TAG, "Cannot run post-install interceptors: Config not found for $feature")
            return
        }
        val postInstallInterceptors = config.listOfDFComponentInterceptor.filter { !it.preInstall }
        Log.e(TAG, "Running ${postInstallInterceptors.size} post-install interceptors for $feature")
        for ((index, interceptor) in postInstallInterceptors.withIndex()) {
            val interceptorId = "$feature-post-$index"
            stateStore.setInterceptorState(interceptorId, DFInterceptorState.Active)
            val result = try {
                // Use withContext for potentially blocking tasks
                withContext(Dispatchers.IO) { interceptor.task() }
            } catch (e: Exception) {
                Log.e(TAG, "Post-install interceptor $index threw exception for $feature", e)
                false
            }
            val finalState = if (result) DFInterceptorState.Completed else DFInterceptorState.Failed("Post-install interceptor $index failed")
            stateStore.setInterceptorState(interceptorId, finalState)

            if (!result) {
                val error = "Post-install check $index failed for $feature"
                Log.w(TAG, error)
                // Optionally update UI state to show a non-fatal warning, ensure on main thread
                // withContext(Dispatchers.Main) {
                //    _uiState.value = DFComponentState.Warning(...)
                // }
            }
        }
    }

    // This function now only deals with InstallationState
    private fun updateUiStateFromInstallationState(feature: String, installationState: DFInstallationState) {
        val newState = when (installationState) {
            DFInstallationState.NotInstalled,
            DFInstallationState.Pending,
            is DFInstallationState.Downloading,
            is DFInstallationState.Installing,
            DFInstallationState.Canceling -> DFComponentState.Loading
            DFInstallationState.Installed -> DFComponentState.Success(feature, installationState)
            is DFInstallationState.Failed -> DFComponentState.Error(
                message = "Installation failed (Code: ${installationState.errorCode.name})",
                errorType = mapDfErrorCodeToErrorType(installationState.errorCode),
                feature = feature,
                dfErrorCode = installationState.errorCode
            )
            // Map framework state to UI state
            DFInstallationState.RequiresConfirmation -> DFComponentState.RequiresConfirmation(feature) // Simple mapping
            DFInstallationState.Canceled -> DFComponentState.Error(
                message = "Installation canceled by user or system.",
                errorType = ErrorType.INSTALLATION,
                feature = feature,
                dfErrorCode = DFErrorCode.NO_ERROR // Or a specific 'Canceled' code
            )
            DFInstallationState.Unknown -> DFComponentState.Error(
                message = "Unknown installation state encountered.",
                errorType = ErrorType.UNKNOWN,
                feature = feature,
                dfErrorCode = DFErrorCode.UNKNOWN_ERROR
            )
        }
        if (_uiState.value != newState) {
            _uiState.value = newState
        }
    }

    private fun handleErrorState(feature: String?, errorType: ErrorType, message: String, dfErrorCode: DFErrorCode? = null) {
        val effectiveFeature = feature ?: _currentFeature.value ?: "unknown"
        val finalErrorCode = dfErrorCode ?: DFErrorCode.UNKNOWN_ERROR

        // Only update central store if feature name is known
        if (feature != null) {
            stateStore.setInstallationState(effectiveFeature, DFInstallationState.Failed(finalErrorCode))
        } else {
            Log.w(TAG,"handleErrorState called with null feature for message: $message")
        }

        // Clear pending confirmation state on any error
        pendingConfirmationState = null

        // Update UI state
        _uiState.value = DFComponentState.Error(message, errorType, effectiveFeature, finalErrorCode)
    }


    private fun mapDfErrorCodeToErrorType(errorCode: DFErrorCode): ErrorType {
        // (Implementation remains the same as before)
        return when(errorCode) {
            DFErrorCode.NETWORK_ERROR -> ErrorType.NETWORK
            DFErrorCode.INSUFFICIENT_STORAGE -> ErrorType.STORAGE
            DFErrorCode.API_NOT_AVAILABLE,
            DFErrorCode.PLAY_STORE_NOT_FOUND,
            DFErrorCode.MODULE_UNAVAILABLE,
            DFErrorCode.INVALID_REQUEST,
            DFErrorCode.SESSION_NOT_FOUND,
            DFErrorCode.ACCESS_DENIED,
            DFErrorCode.APP_NOT_OWNED,
            DFErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED,
            DFErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION -> ErrorType.INSTALLATION
            DFErrorCode.SPLITCOMPAT_VERIFICATION_ERROR,
            DFErrorCode.SPLITCOMPAT_EMULATION_ERROR -> ErrorType.INSTALLATION
            DFErrorCode.INTERNAL_ERROR -> ErrorType.UNKNOWN
            DFErrorCode.NO_ERROR -> ErrorType.UNKNOWN
            DFErrorCode.DOWNLOAD_SIZE_EXCEEDED -> ErrorType.INSTALLATION
            DFErrorCode.UNKNOWN_ERROR -> ErrorType.UNKNOWN
        }
    }
}