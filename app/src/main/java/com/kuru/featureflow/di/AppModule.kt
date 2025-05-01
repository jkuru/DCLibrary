package com.kuru.featureflow.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.kuru.featureflow.component.googleplay.DFComponentInstaller
import com.kuru.featureflow.component.googleplay.DFComponentInstallerManager
import com.kuru.featureflow.component.register.ComponentRegistryData
import com.kuru.featureflow.component.register.DFComponentRegistry
import com.kuru.featureflow.component.register.DFComponentRegistryManager
import com.kuru.featureflow.component.route.DFComponentRoute
import com.kuru.featureflow.component.route.DFComponentUriRouteParser
import com.kuru.featureflow.component.state.DFComponentStateStore
import com.kuru.featureflow.component.state.DFComponentStateStoreImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

// Define DataStore instance via extension property
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "feature_flow_settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideComponentRegistryData(): ComponentRegistryData {
        return ComponentRegistryData()
    }

    @Provides
    @Singleton
    fun provideSplitInstallManager(@ApplicationContext context: Context): SplitInstallManager {
        return SplitInstallManagerFactory.create(context)
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideServiceMap(): MutableMap<Class<*>, Any> {
        return mutableMapOf()
    }

}

@Module
@InstallIn(SingletonComponent::class)
abstract class FrameworkBindingsModule {

    @Binds
    @Singleton
    abstract fun bindDFComponentInstaller(
        impl: DFComponentInstallerManager
    ): DFComponentInstaller

    @Binds
    @Singleton
    abstract fun bindDFComponentStateStore(
        impl: DFComponentStateStoreImpl
    ): DFComponentStateStore

    @Binds
    @Singleton
    abstract fun bindDFComponentRegistry(
        impl: DFComponentRegistryManager
    ): DFComponentRegistry

}
