package com.kuru.featureflow.component.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.kuru.featureflow.component.state.DFInstallationState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class DFComponentActivityTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var activity: DFComponentActivity
    private lateinit var viewModel: DFComponentViewModel

    @Before
    fun setUp() {
        // Mock ViewModel
        viewModel = mockk(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(DFComponentState.Loading)
        every { viewModel.eventFlow } returns MutableSharedFlow()
        every { viewModel.dynamicScreenContent } returns MutableStateFlow(null)

        // Setup activity with Robolectric
        val controller = Robolectric.buildActivity(DFComponentActivity::class.java)
        activity = controller.get()

        // Inject mocked ViewModel
        val viewModelField = DFComponentActivity::class.java.getDeclaredField("viewModel")
        viewModelField.isAccessible = true
        viewModelField.set(activity, viewModel)

        controller.create()
    }

    @After
    fun tearDown() {
        activity.finish()
    }

    @Test
    @Ignore("To be implemented")
    fun `onCreate processes intent with EXTRA_TARGET`() = runTest {
        // Arrange
        val uri = "feature://test"
        val intent = Intent().apply {
            putExtra(DFComponentActivity.EXTRA_TARGET, uri)
        }
        activity.intent = intent

        // Act
        activity.onCreate(Bundle())

        // Assert
        verify { viewModel.processIntent(DFComponentIntent.ProcessUri(uri)) }
    }

    @Test
    @Ignore("To be implemented")
    fun `onCreate processes ACTION_VIEW intent`() = runTest {
        // Arrange
        val uri = "feature://test"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(uri)
        }
        activity.intent = intent

        // Act
        activity.onCreate(Bundle())

        // Assert
        verify { viewModel.processIntent(DFComponentIntent.ProcessUri(uri)) }
    }

    @Test
    @Ignore("To be implemented")
    fun `onCreate handles null intent gracefully`() = runTest {
        // Arrange
        activity.intent = null

        // Act
        activity.onCreate(Bundle())

        // Assert
        verify(exactly = 0) { viewModel.processIntent(any()) }
    }

    @Test
    @Ignore("To be implemented")
    fun `onNewIntent updates intent and processes uri`() = runTest {
        // Arrange
        val uri = "feature://new_feature"
        val newIntent = Intent().apply {
            putExtra(DFComponentActivity.EXTRA_TARGET, uri)
        }

        // Act
        activity.onNewIntent(newIntent)

        // Assert
        assertEquals(newIntent, activity.intent)
        verify { viewModel.processIntent(DFComponentIntent.ProcessUri(uri)) }
    }

    @Test
    @Ignore("To be implemented")
    fun `confirmation dialog launches for ConfirmationEventData`() = runTest {
        // Arrange
        val feature = "test_feature"
        val sessionId = 123
        val intentSender = mockk<android.content.IntentSender>()
        val sessionState = mockk<SplitInstallSessionState> {
            every { sessionId() } returns sessionId
            every { status() } returns SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION
         //   every { resolutionIntent() } returns intentSender
        }
        val eventFlow = MutableSharedFlow<ConfirmationEventData>()
        every { viewModel.eventFlow } returns eventFlow

        // Set featureAwaitingConfirmationResult
        val field = DFComponentActivity::class.java.getDeclaredField("featureAwaitingConfirmationResult")
        field.isAccessible = true
        field.set(activity, null)

        // Mock confirmationResultLauncher
        val launcher = mockk<androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>>(relaxed = true)
        val launcherField = DFComponentActivity::class.java.getDeclaredField("confirmationResultLauncher")
        launcherField.isAccessible = true
        launcherField.set(activity, launcher)

        // Act
        activity.onCreate(Bundle())
        eventFlow.emit(ConfirmationEventData(feature, sessionState))

        // Assert
        verify { launcher.launch(any<IntentSenderRequest>()) }
        assertEquals(feature, field.get(activity))
    }

    @Test
    @Ignore("To be implemented")
    fun `confirmation result notifies ViewModel and clears state`() = runTest {
        // Arrange
        val feature = "test_feature"
        val field = DFComponentActivity::class.java.getDeclaredField("featureAwaitingConfirmationResult")
        field.isAccessible = true
        field.set(activity, feature)

        // Mock confirmationResultLauncher
        val launcher = mockk<androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>>(relaxed = true)
        val launcherField = DFComponentActivity::class.java.getDeclaredField("confirmationResultLauncher")
        launcherField.isAccessible = true
        launcherField.set(activity, launcher)

        // Simulate user confirming
        val result = ActivityResult(Activity.RESULT_OK, Intent())

        // Act
        activity.onCreate(Bundle())
        launcherField.javaClass.getDeclaredMethod("onActivityResult", Any::class.java)
            .invoke(launcher, result)

        // Assert
        verify { viewModel.processIntent(DFComponentIntent.UserConfirmationResult(feature, true)) }
        assertNull(field.get(activity))
    }

    @Test
    @Ignore("To be implemented")
    fun `terminal Success state clears featureAwaitingConfirmationResult`() = runTest {
        // Arrange
        val feature = "test_feature"
        val uiStateFlow = MutableStateFlow<DFComponentState>(DFComponentState.Loading)
        every { viewModel.uiState } returns uiStateFlow

        // Set featureAwaitingConfirmationResult
        val field = DFComponentActivity::class.java.getDeclaredField("featureAwaitingConfirmationResult")
        field.isAccessible = true
        field.set(activity, feature)

        // Act
        activity.onCreate(Bundle())
        uiStateFlow.value = DFComponentState.Success(
            feature = feature,
            featureInstallationState = DFInstallationState.Installed,
            params = emptyList()
        )

        // Assert
        assertNull(field.get(activity))
    }

    @Test
    @Ignore("To be implemented")
    fun `terminal Error state clears featureAwaitingConfirmationResult`() = runTest {
        // Arrange
        val feature = "test_feature"
        val uiStateFlow = MutableStateFlow<DFComponentState>(DFComponentState.Loading)
        every { viewModel.uiState } returns uiStateFlow

        // Set featureAwaitingConfirmationResult
        val field = DFComponentActivity::class.java.getDeclaredField("featureAwaitingConfirmationResult")
        field.isAccessible = true
        field.set(activity, feature)

        // Act
        activity.onCreate(Bundle())
        uiStateFlow.value = DFComponentState.Error(
            message = "Installation failed",
            errorType = ErrorType.INSTALLATION,
            feature = feature
        )

        // Assert
        assertNull(field.get(activity))
    }

    @Test
    @Ignore("To be implemented")
    fun `duplicate URI intent is ignored during active processing`() = runTest {
        // Arrange
        val uri = "feature://test"
        val intent = Intent().apply {
            putExtra(DFComponentActivity.EXTRA_TARGET, uri)
        }
        activity.intent = intent
        val uiStateFlow = MutableStateFlow<DFComponentState>(DFComponentState.Loading)
        every { viewModel.uiState } returns uiStateFlow

        // Act
        activity.onCreate(Bundle())
        val newIntent = Intent().apply {
            putExtra(DFComponentActivity.EXTRA_TARGET, uri)
        }
        activity.onNewIntent(newIntent)

        // Assert
        verify(exactly = 1) { viewModel.processIntent(DFComponentIntent.ProcessUri(uri)) }
    }

    @Test
    @Ignore("To be implemented")
    fun `dynamic content is rendered when Success state and lambda available`() = runTest {
        // Arrange
        val feature = "test_feature"
        val uiStateFlow = MutableStateFlow<DFComponentState>(DFComponentState.Loading)
        // Use a dummy Composable lambda instead of mockk
//        val dynamicContentFlow = MutableStateFlow<(@Composable (NavController, List<String>) -> Unit)?>(
//            @Composable { _: NavController, _: List<String> -> } // Empty Composable
//        )
        every { viewModel.uiState } returns uiStateFlow
     //   every { viewModel.dynamicScreenContent } returns dynamicContentFlow

        // Act
        activity.onCreate(Bundle())
        uiStateFlow.value = DFComponentState.Success(
            feature = feature,
            featureInstallationState = DFInstallationState.Installed,
            params = emptyList()
        )

        // Assert
        // Verify that ViewModel's clearDynamicContent is called on disposal
        val clearDynamicContentCalled = mutableListOf<Boolean>()
        every { viewModel.clearDynamicContent() } answers { clearDynamicContentCalled.add(true) }
        activity.finish()

        assertEquals(listOf(true), clearDynamicContentCalled)
    }
}