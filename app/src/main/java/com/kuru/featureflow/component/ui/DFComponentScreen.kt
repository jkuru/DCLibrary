package com.kuru.featureflow.component.ui

import androidx.compose.foundation.layout.* // Import necessary layout components
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuru.featureflow.component.state.DFErrorCode

@Composable
fun DFComponentScreen(viewModel: DFComponentViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Use Box for simpler centering when possible, apply edge-to-edge padding
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Apply padding that respects system bars (status, navigation)
            // This allows the Box background/content to go edge-to-edge
            // while keeping the main content within safe areas.
            .windowInsetsPadding(WindowInsets.safeDrawing), // Use safeDrawing or systemBars
        contentAlignment = Alignment.Center // Center content within the Box
    ) {
        // Delegate rendering based on state to helper Composables
        when (val state = uiState) {
            is DFComponentState.Loading -> LoadingIndicator()
            is DFComponentState.RequiresConfirmation -> ConfirmationPrompt(state.feature)
            // Option B: Treat Success state here as final loading step before lambda swap
            is DFComponentState.Success -> LoadingIndicator("Loading feature UI...")
            is DFComponentState.Error -> ErrorMessage(
                errorState = state,
                onRetry = { viewModel.processIntent(DFComponentIntent.Retry) }
            )
            // Removed explicit Success case (Option A) OR simplified it (Option B shown above)
        }
    }
}

// --- Helper Composables for each state ---

@Composable
private fun LoadingIndicator(text: String = "Loading feature...") {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator()
    }
}

@Composable
private fun ConfirmationPrompt(feature: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Confirmation Required",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Installation for '$feature' requires your approval. Please check for a system dialog.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp) // Add horizontal padding for text readability
        )
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator() // Show activity while waiting for dialog interaction
    }
}

@Composable
private fun ErrorMessage(errorState: DFComponentState.Error, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp) // Add horizontal padding for text
    ) {
        Text(
            "Error Loading Feature",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        // User-friendly message based on ErrorType (example)
        val userMessage = when(errorState.errorType) {
            ErrorType.NETWORK -> "Please check your network connection and try again."
            ErrorType.STORAGE -> "Not enough storage space to install the feature."
            ErrorType.URI_INVALID -> "The requested link seems to be invalid."
            ErrorType.INSTALLATION -> "Installation failed. (${errorState.dfErrorCode?.name ?: "Unknown reason"})" // Show code conditionally
            else -> errorState.message // Fallback to original message
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            userMessage,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        // Optionally show technical details if needed for debugging
        // errorState.dfErrorCode?.let { code ->
        //     Text(
        //         "Details: ${code.name} (${code.code})",
        //         style = MaterialTheme.typography.bodySmall,
        //         color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        //     )
        // }
        Spacer(modifier = Modifier.height(24.dp))

        // Determine if retry makes sense
        val allowRetry = when (errorState.errorType) {
            ErrorType.NETWORK, ErrorType.UNKNOWN -> true // Always allow retry for network/unknown?
            ErrorType.INSTALLATION -> when (errorState.dfErrorCode) { // Retry specific installation errors
                DFErrorCode.NETWORK_ERROR,
                DFErrorCode.API_NOT_AVAILABLE,
                DFErrorCode.SESSION_NOT_FOUND,
                DFErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED,
                DFErrorCode.INTERNAL_ERROR -> true
                else -> false
            }
            else -> false // Don't allow retry for validation, storage, URI errors by default
        }

        if (allowRetry) {
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}