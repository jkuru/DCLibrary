package com.kuru.featureflow.component.googleplay

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.kuru.featureflow.component.state.DFStateStore
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
    fun provideDFComponentStateStore(): DFStateStore {
        return Mockito.mock(DFStateStore::class.java)
    }

}