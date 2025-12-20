@file:Suppress("StringLiteralDuplication")

package com.calypsan.listenup.client.features.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.composeapp.R
import com.calypsan.listenup.client.design.components.ListenUpButton
import com.calypsan.listenup.client.design.components.ListenUpTextField
import com.calypsan.listenup.client.design.theme.ListenUpTheme
import com.calypsan.listenup.client.design.theme.LocalDarkTheme
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
    val state by viewModel.state.collectAsStateWithLifecycle()

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
 * Brand logo section.
 * Uses [LocalDarkTheme] to respect the app's actual theme state,
 * not just the system setting.
 */
@Composable
private fun BrandLogo(modifier: Modifier = Modifier) {
    val isDarkTheme = LocalDarkTheme.current
    val logoRes =
        if (isDarkTheme) {
            R.drawable.listenup_logo_white
        } else {
            R.drawable.listenup_logo_black
        }

    Image(
        painter = painterResource(logoRes),
        contentDescription = "ListenUp Logo",
        modifier = modifier.size(160.dp),
    )
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
            placeholder = "example.com or 10.0.2.2:8080",
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

// ========================================
// Previews
// ========================================

@Preview(name = "Empty", showSystemUi = true)
@Composable
private fun PreviewEmpty() {
    ListenUpTheme {
        ServerSetupContent(
            state = ServerConnectUiState(),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "With URL", showSystemUi = true)
@Composable
private fun PreviewWithUrl() {
    ListenUpTheme {
        ServerSetupContent(
            state =
                ServerConnectUiState(
                    serverUrl = "https://listenup.example.com",
                ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Loading", showSystemUi = true)
@Composable
private fun PreviewLoading() {
    ListenUpTheme {
        ServerSetupContent(
            state =
                ServerConnectUiState(
                    serverUrl = "https://listenup.example.com",
                    isLoading = true,
                ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Dark Theme", showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewDark() {
    ListenUpTheme {
        ServerSetupContent(
            state =
                ServerConnectUiState(
                    serverUrl = "https://listenup.example.com",
                ),
            onEvent = {},
            onBack = {},
        )
    }
}
