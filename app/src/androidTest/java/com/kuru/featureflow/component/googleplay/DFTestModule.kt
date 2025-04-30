package com.kuru.featureflow.component.googleplay

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.kuru.featureflow.component.register.DFComponentRegistry
import com.kuru.featureflow.component.state.DFComponentStateStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.mockito.Mockito
import javax.inject.Singleton

@Module
//@TestInstallIn(
//    components = [SingletonComponent::class],
//    replaces = [AppModule::class, FrameworkBindingsModule::class]
//)
@InstallIn(SingletonComponent::class)
object TestModule {
    @Provides
    @Singleton
    fun provideSplitInstallManager(): SplitInstallManager {
        return Mockito.mock(SplitInstallManager::class.java)
    }

    @Provides
    @Singleton
    fun provideContext(): Context {
        return Mockito.mock(Context::class.java)
    }

    @Provides
    @Singleton
    fun provideDFComponentRegistry(): DFComponentRegistry {
        return Mockito.mock(DFComponentRegistry::class.java)
    }

    @Provides
    @Singleton
    fun provideDFComponentStateStore(): DFComponentStateStore {
        return Mockito.mock(DFComponentStateStore::class.java)
    }

    @Provides
    @Singleton
    fun provideDFComponentInstaller(): DFComponentInstaller {
        return Mockito.mock(DFComponentInstaller::class.java)
    }
}