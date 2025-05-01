package com.kuru.featureflow.component.googleplay

import com.kuru.featureflow.component.state.DFInstallProgress
import kotlinx.coroutines.flow.Flow

/**
 * Interface for installing dynamic feature components (modules).
 */
interface DFComponentInstaller {
    /**
     * Checks if a specific component (module) is already installed.
     * @param componentName The name of the dynamic feature module.
     * @return True if the module is installed, false otherwise.
     */
    suspend fun isComponentInstalled(componentName: String): Boolean

    /**
     * Initiates the installation process for a component and returns a Flow
     * that emits installation progress updates, including raw state when needed.
     * @param componentName The name of the dynamic feature module to install.
     * @return A Flow emitting InstallProgress updates.
     */
    suspend fun installComponent(componentName: String): Flow<DFInstallProgress> // Changed return type

    /**
     * Retries the installation for a component. This typically involves
     * re-initiating the installation flow.
     * @param componentName The name of the dynamic feature module to retry.
     * @return A Flow emitting InstallProgress updates for the retry attempt.
     */
    suspend fun retryComponentInstall(componentName: String): Flow<DFInstallProgress> // Changed return type
}