package com.kuru.featureflow.component.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Use lifecycle-aware collection

@Composable
fun DFComponentScreen(viewModel: DFComponentViewModel) {
    // Use collectAsStateWithLifecycle for better lifecycle awareness
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Center content for loading/error/confirmation states
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = uiState) { // Use 'state' variable for smart casting
            is DFComponentState.Loading -> {
                Text("Loading feature...")
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator()
            }
            is DFComponentState.RequiresConfirmation -> {
                Text("Confirmation Required for feature: ${state.feature}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Please confirm the installation when prompted.")
                Spacer(modifier = Modifier.height(8.dp))
                // Usually, the system dialog handles confirmation.
                // You might show a loading indicator here or a disabled button.
                CircularProgressIndicator() // Indicate waiting for user action
                // Button(onClick = { /* Maybe trigger confirmation again? Unlikely needed */ }, enabled = false) {
                //     Text("Waiting for Confirmation")
                // }
            }
            is DFComponentState.Success -> {
                // Display success information - specific UI depends on the feature
                Text("Feature Loaded: ${state.feature}")
                Spacer(modifier = Modifier.height(8.dp))
                // Displaying the raw InstallationState might not be user-friendly
                // Adapt this based on what the user needs to see
                Text("Status: ${state.DFInstallationState::class.simpleName}")
                // TODO: Add navigation or display the actual feature UI here
            }
            is DFComponentState.Error -> {
                Text(
                    "Error Loading Feature: ${state.feature ?: "Unknown"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Message: ${state.message}",
                    color = MaterialTheme.colorScheme.error
                )
                state.dfErrorCode?.let {
                    Text(
                        "Code: ${it.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Allow retry only for specific error types where it makes sense
                if (state.errorType == ErrorType.INSTALLATION || state.errorType == ErrorType.NETWORK) {
                    Button(onClick = { viewModel.processIntent(DFComponentIntent.Retry) }) {
                        Text("Retry")
                    }
                }
            }
            // No 'else' needed because sealed class is exhaustive with all branches covered
        }
    }
}
