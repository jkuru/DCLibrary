package com.kuru.featureflow.component.domain

import android.content.Context
import android.util.Log
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.kuru.featureflow.component.state.DFErrorCode
import com.kuru.featureflow.component.state.DFFeatureInstallProgress
import com.kuru.featureflow.component.state.DFInstallationState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for installing dynamic feature modules.
 */
interface DFFeatureInstaller {
    /**
     * Checks if a specific feature module is already installed.
     * @param featureName The name of the dynamic feature module.
     * @return True if the module is installed, false otherwise.
     */
    suspend fun isFeatureInstalled(featureName: String): Boolean

    /**
     * Initiates the installation process for a feature module and returns a Flow
     * emitting installation progress updates.
     * @param featureName The name of the dynamic feature module to install.
     * @return A Flow emitting DFInstallProgress updates.
     */
    suspend fun installFeature(featureName: String): Flow<DFFeatureInstallProgress>

    /**
     * Retries the installation for a feature module by re-initiating the installation flow.
     * @param featureName The name of the dynamic feature module to retry.
     * @return A Flow emitting DFInstallProgress updates for the retry attempt.
     */
    suspend fun retryFeatureInstall(featureName: String): Flow<DFFeatureInstallProgress>
}

/**
 * Use case for installing dynamic feature modules using Google Play's SplitInstall API.
 * Manages installation requests, monitors progress, and handles session states.
 */
@Singleton
class DFInstallFeatureUseCase @Inject constructor(
    private val splitInstallManager: SplitInstallManager,
    @ApplicationContext private val context: Context
) : DFFeatureInstaller {

    init {
        SplitCompat.install(context) //TODO do i need this ?
    }

    companion object {
        private const val TAG = "DFInstallFeatureUseCase"
    }

    private val activeListeners = ConcurrentHashMap<Int, SplitInstallStateUpdatedListener>()

    override suspend fun isFeatureInstalled(featureName: String): Boolean {
        if (featureName.isBlank()) {
            Log.e(TAG, "Invalid feature name: $featureName")
            return false
        }
        val installedModules = splitInstallManager.installedModules
        val isInInstalledModules = installedModules.contains(featureName)

        if (isInInstalledModules) {
            // Cross-check with session states to confirm
            runCatching {
                val sessions = splitInstallManager.sessionStates.await()
                val isFullyInstalled = sessions.any { session ->
                    session.moduleNames().contains(featureName) &&
                            session.status() == SplitInstallSessionStatus.INSTALLED
                }
                Log.d(TAG, "Session state check for $featureName: $isFullyInstalled")
                Log.d(
                    TAG,
                    "isFeatureInstalled($featureName): $isFullyInstalled (installedModules: $installedModules)"
                )
                return isFullyInstalled
            }.onFailure { e ->
                Log.e(TAG, "Failed to check session states for $featureName", e)
            }
            Log.d(
                TAG,
                "Falling back to installedModules for $featureName as session check failed"
            )
            return true // Fallback to installedModules if session check fails
        }
        return false
    }

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun installFeature(featureName: String): Flow<DFFeatureInstallProgress> {
        Log.d(TAG, "Building SplitInstallRequest for feature: $featureName")
        val request = SplitInstallRequest.newBuilder()
            .addModule(featureName)
            .build()
        try {
            return callbackFlow {
                var currentSessionId = 0
                Log.d(
                    TAG,
                    "callbackFlow started for feature: $featureName, sessionId: $currentSessionId"
                )
                val listener = SplitInstallStateUpdatedListener { state ->
                    Log.d(
                        TAG,
                        "Listener received state for $featureName: status=${state.status()}, sessionId=${state.sessionId()}"
                    )
                    if (state.sessionId() == currentSessionId) {
                        val installProgress = mapSessionStateToInstallProgress(state, featureName)
                        val newState = installProgress.frameworkState

                        Log.d(
                            TAG,
                            "Emitting state for $featureName (session $currentSessionId): $newState"
                        )
                        val success = trySend(installProgress).isSuccess

                        if (isTerminalState(newState) || !success) {
                            Log.d(
                                TAG,
                                "Terminal state ($newState) or channel closed ($success) for $featureName (session $currentSessionId)"
                            )
                            cleanupListener(currentSessionId)
                            if (!isClosedForSend) {
                                close()
                            }
                        }
                    } else {
                        Log.w(
                            TAG,
                            "Ignoring state update for unrelated session ${state.sessionId()}"
                        )
                    }
                }

                try {
                    Log.d(TAG, "Starting install for feature: $featureName")
                    currentSessionId = splitInstallManager.startInstall(request).await()
                    Log.d(TAG, "Install initiated for $featureName, sessionId: $currentSessionId")
                    val existingListener = activeListeners.putIfAbsent(currentSessionId, listener)

                    if (existingListener == null) {
                        Log.d(TAG, "Registering listener for session $currentSessionId")
                        splitInstallManager.registerListener(listener)
                        Log.d(TAG, "Listener registered for $featureName (session $currentSessionId)")

                        // Emit initial state
                        val currentSessionState =
                            splitInstallManager.getSessionState(currentSessionId).await()
                        val initialProgress = if (currentSessionState != null) {
                            mapSessionStateToInstallProgress(currentSessionState, featureName)
                        } else {
                            DFFeatureInstallProgress(DFInstallationState.Pending)
                        }
                        Log.d(
                            TAG,
                            "Emitting initial state for $featureName (session $currentSessionId): ${initialProgress.frameworkState}"
                        )
                        trySend(initialProgress)
                    } else {
                        Log.w(
                            TAG,
                            "Listener already active for session $currentSessionId"
                        )
                        val currentSessionState =
                            splitInstallManager.getSessionState(currentSessionId).await()
                        if (currentSessionState != null) {
                            trySend(mapSessionStateToInstallProgress(currentSessionState, featureName))
                        } else {
                            trySend(DFFeatureInstallProgress(DFInstallationState.Unknown))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start install for $featureName", e)
                    trySend(DFFeatureInstallProgress(DFInstallationState.Failed(DFErrorCode.UNKNOWN_ERROR)))
                    close(e)
                }

                awaitClose {
                    Log.d(TAG, "Flow closing for $featureName (session $currentSessionId)")
                    cleanupListener(currentSessionId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create callbackFlow for $featureName", e)
            return flowOf(DFFeatureInstallProgress(DFInstallationState.Failed(DFErrorCode.UNKNOWN_ERROR)))
        }
    }

    override suspend fun retryFeatureInstall(featureName: String): Flow<DFFeatureInstallProgress> {
        Log.d(TAG, "Retrying installation for feature: $featureName")
        return installFeature(featureName)
    }

    private fun cleanupListener(sessionId: Int) {
        val listenerToRemove = activeListeners.remove(sessionId)
        if (listenerToRemove != null) {
            try {
                splitInstallManager.unregisterListener(listenerToRemove)
                Log.d(TAG, "Listener unregistered for session $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering listener for session $sessionId", e)
            }
        } else {
            Log.w(TAG, "No listener found for session $sessionId")
        }
    }

    private fun mapSessionStateToInstallProgress(
        state: SplitInstallSessionState,
        featureName: String
    ): DFFeatureInstallProgress {
        if (!state.moduleNames().contains(featureName)) {
            Log.e(
                TAG,
                "State update for session ${state.sessionId()} does not include feature $featureName"
            )
            return DFFeatureInstallProgress(DFInstallationState.Unknown)
        }

        val frameworkState: DFInstallationState = when (state.status()) {
            SplitInstallSessionStatus.PENDING -> DFInstallationState.Pending
            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> DFInstallationState.RequiresConfirmation
            SplitInstallSessionStatus.DOWNLOADING -> {
                val totalBytes = state.totalBytesToDownload()
                val progress = if (totalBytes > 0) {
                    ((state.bytesDownloaded() * 100) / totalBytes).toInt()
                } else {
                    0
                }
                DFInstallationState.Downloading(progress.coerceIn(0, 100))
            }
            SplitInstallSessionStatus.DOWNLOADED -> DFInstallationState.Installing(0)
            SplitInstallSessionStatus.INSTALLING -> DFInstallationState.Installing(100)
            SplitInstallSessionStatus.INSTALLED -> DFInstallationState.Installed
            SplitInstallSessionStatus.FAILED -> DFInstallationState.Failed(
                DFErrorCode.fromSplitInstallErrorCode(state.errorCode())
            )
            SplitInstallSessionStatus.CANCELING -> DFInstallationState.Canceling
            SplitInstallSessionStatus.CANCELED -> DFInstallationState.Canceled
            SplitInstallSessionStatus.UNKNOWN -> DFInstallationState.Unknown
            else -> DFInstallationState.Unknown
        }

        return DFFeatureInstallProgress(
            frameworkState = frameworkState,
            playCoreState = if (frameworkState is DFInstallationState.RequiresConfirmation) state else null
        )
    }

    private fun isTerminalState(state: DFInstallationState): Boolean {
        return when (state) {
            is DFInstallationState.Installed,
            is DFInstallationState.Failed,
            is DFInstallationState.Canceled -> true
            else -> false
        }
    }
}