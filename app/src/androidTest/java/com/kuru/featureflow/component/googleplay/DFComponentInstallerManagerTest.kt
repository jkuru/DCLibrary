package com.kuru.featureflow.component.googleplay

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.kuru.featureflow.di.AppModule
import com.kuru.featureflow.di.FrameworkBindingsModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Captor
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(AppModule::class, FrameworkBindingsModule::class)
@HiltAndroidTest
class DFComponentInstallerManagerTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var splitInstallManager: SplitInstallManager

    @Inject
    lateinit var context: Context

    @Captor
    private lateinit var listenerCaptor: ArgumentCaptor<SplitInstallStateUpdatedListener>

    @Captor
    private lateinit var requestCaptor: ArgumentCaptor<SplitInstallRequest>

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val testModuleName = "testModule"
    private val testSessionId = 123

    private var mocksClosable: AutoCloseable = MockitoAnnotations.openMocks(this)

    @Before
    fun setUp() {
        hiltRule.inject()
        mocksClosable = MockitoAnnotations.openMocks(this)
        val mockTask: Task<Int> = Tasks.forResult(testSessionId)
        `when`(splitInstallManager.startInstall(ArgumentMatchers.any(SplitInstallRequest::class.java)))
            .thenReturn(mockTask)
    }

    @After
    fun tearDown() {
        mocksClosable.close()
    }

}