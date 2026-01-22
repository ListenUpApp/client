@file:Suppress("StringLiteralDuplication")

package com.calypsan.listenup.client.features.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.BrandLogo
import com.calypsan.listenup.client.design.components.ListenUpButton
import com.calypsan.listenup.client.design.components.ListenUpTextField
import com.calypsan.listenup.client.presentation.connect.ServerConnectUiEvent
import com.calypsan.listenup.client.presentation.connect.ServerConnectUiState
import com.calypsan.listenup.client.presentation.connect.ServerConnectViewModel
import org.koin.compose.koinInject

/**
 * Server setup screen with clean, non-overlapping layout.
 *
 * Features:
 * - Scaffold for proper Material 3 structure
 * - Single scrollable Column - no overlapping elements
 * - ElevatedCard for form container with expressive corners
 * - Respects system theme and dynamic colors
 * - Edge-to-edge with proper insets
 *
 * @param onServerVerified Callback when server is successfully verified
 * @param modifier Modifier for the root composable
 */
@Composable
fun ServerSetupScreen(
    onServerVerified: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ServerConnectViewModel = koinInject(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isVerified) {
        if (state.isVerified) {
            onServerVerified()
        }
    }

    ServerSetupContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless content for ServerSetupScreen.
 * Separated for preview support.
 */
@Composable
private fun ServerSetupContent(
    state: ServerConnectUiState,
    onEvent: (ServerConnectUiEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top spacing
            Spacer(modifier = Modifier.height(48.dp))

            // Logo
            BrandLogo()

            // Gap between logo and card
            Spacer(modifier = Modifier.height(48.dp))

            // Form card - constrained width for tablets
            ElevatedCard(
                modifier =
                    Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                    CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            ) {
                FormContent(
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier.padding(24.dp),
                )
            }

            // Back button (only shown when there's somewhere to go back to)
            if (onBack != null) {
                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onBack) {
                    Text("Back to Server Selection")
                }
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Form content inside the card.
 * Contains title, text field, and connect button.
 */
@Composable
private fun FormContent(
    state: ServerConnectUiState,
    onEvent: (ServerConnectUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Title
        Text(
            text = "Connect to Server",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // URL input field
        ListenUpTextField(
            value = state.serverUrl,
            onValueChange = { onEvent(ServerConnectUiEvent.UrlChanged(it)) },
            label = "Server URL",
            placeholder = "example.com or 192.168.1.100:8080",
            isError = state.error != null,
            supportingText = state.error?.message,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = { onEvent(ServerConnectUiEvent.ConnectClicked) },
                ),
        )

        // Connect button
        ListenUpButton(
            text = "Connect",
            onClick = { onEvent(ServerConnectUiEvent.ConnectClicked) },
            isLoading = state.isLoading,
            enabled = state.isConnectEnabled,
        )
    }
}
