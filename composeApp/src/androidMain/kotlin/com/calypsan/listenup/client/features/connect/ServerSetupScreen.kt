package com.calypsan.listenup.client.features.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import androidx.window.core.layout.WindowWidthSizeClass
import com.calypsan.listenup.client.R
import com.calypsan.listenup.client.design.components.ListenUpButton
import com.calypsan.listenup.client.design.components.ListenUpTextField
import com.calypsan.listenup.client.design.theme.ListenUpTheme
import com.calypsan.listenup.client.presentation.connect.ServerConnectUiEvent
import com.calypsan.listenup.client.presentation.connect.ServerConnectUiState
import com.calypsan.listenup.client.presentation.connect.ServerConnectViewModel
import org.koin.compose.koinInject

/**
 * Server setup screen with ViewModel integration and adaptive layouts.
 *
 * Reference-grade implementation featuring:
 * - AndroidX Lifecycle ViewModel (KMP, in shared module)
 * - Material 3 Adaptive layouts for all form factors
 * - Edge-to-edge display with safe drawing insets
 * - Event-driven architecture (UiEvent/UiState pattern)
 * - collectAsStateWithLifecycle for proper lifecycle handling
 *
 * Adaptive behavior:
 * - Compact (phones portrait): Vertical layout with stacked brand/form
 * - Medium (tablets, phones landscape): Horizontal 40/60 split
 * - Expanded (large tablets, desktops): Centered content with max width
 *
 * @param onServerVerified Callback when server is successfully verified
 * @param modifier Modifier for the root composable
 */
@Composable
fun ServerSetupScreen(
    onServerVerified: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerConnectViewModel = koinInject()
) {
    // Observe state with lifecycle awareness (cancels when not active)
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Determine window size for adaptive layout
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    // Navigate when server is successfully verified
    LaunchedEffect(state.isVerified) {
        if (state.isVerified) {
            onServerVerified()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding() // Edge-to-edge support with safe areas
    ) {
        when (windowSizeClass.windowWidthSizeClass) {
            WindowWidthSizeClass.COMPACT -> {
                CompactLayout(
                    state = state,
                    onEvent = viewModel::onEvent
                )
            }
            WindowWidthSizeClass.MEDIUM -> {
                MediumLayout(
                    state = state,
                    onEvent = viewModel::onEvent
                )
            }
            else -> { // EXPANDED
                ExpandedLayout(
                    state = state,
                    onEvent = viewModel::onEvent
                )
            }
        }
    }
}

/**
 * Compact layout for phones in portrait mode.
 *
 * Vertical stack:
 * - Top 1/3: Brand logo
 * - Bottom 2/3: Form card with rounded top corners
 */
@Composable
private fun CompactLayout(
    state: ServerConnectUiState,
    onEvent: (ServerConnectUiEvent) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Brand section (1/3 of screen)
        BrandSection(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.33f)
        )

        // Form section (2/3 of screen)
        FormCard(
            state = state,
            onEvent = onEvent,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.67f)
        )
    }
}

/**
 * Medium layout for tablets and phones in landscape.
 *
 * Horizontal split:
 * - Left 40%: Brand logo
 * - Right 60%: Form card
 */
@Composable
private fun MediumLayout(
    state: ServerConnectUiState,
    onEvent: (ServerConnectUiEvent) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Brand section (40%)
        BrandSection(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.4f)
        )

        // Form section (60%)
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.6f),
            tonalElevation = 1.dp
        ) {
            FormContent(
                state = state,
                onEvent = onEvent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            )
        }
    }
}

/**
 * Expanded layout for large tablets and desktops.
 *
 * Centered content with constrained width:
 * - Maximum width 800dp for optimal reading
 * - Vertical layout similar to compact
 * - More generous spacing
 */
@Composable
private fun ExpandedLayout(
    state: ServerConnectUiState,
    onEvent: (ServerConnectUiEvent) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 800.dp)
                .fillMaxHeight()
        ) {
            // Brand section (1/3)
            BrandSection(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.33f)
            )

            // Form section (2/3)
            FormCard(
                state = state,
                onEvent = onEvent,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.67f)
            )
        }
    }
}

/**
 * Brand section with logo.
 * Consistent across all layouts.
 * Uses dark/light logo variant based on system theme.
 */
@Composable
private fun BrandSection(modifier: Modifier = Modifier) {
    val isDarkTheme = isSystemInDarkTheme()
    val logoRes = if (isDarkTheme) {
        R.drawable.listenup_logo_white
    } else {
        R.drawable.listenup_logo_black
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(logoRes),
            contentDescription = "ListenUp Logo",
            modifier = Modifier.size(200.dp)
        )
    }
}

/**
 * Form card with rounded top corners.
 * Used in compact and expanded layouts.
 */
@Composable
private fun FormCard(
    state: ServerConnectUiState,
    onEvent: (ServerConnectUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        ),
        tonalElevation = 1.dp
    ) {
        FormContent(
            state = state,
            onEvent = onEvent,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        )
    }
}

/**
 * Form content (title, input, button, error).
 * Reusable across all layout variants.
 *
 * Button is pinned to bottom and pushed up by keyboard via imePadding.
 */
@Composable
private fun FormContent(
    state: ServerConnectUiState,
    onEvent: (ServerConnectUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.imePadding() // Push up when keyboard opens
    ) {
        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Title
            Text(
                text = "Connect to Server",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // URL input field
            ListenUpTextField(
                value = state.serverUrl,
                onValueChange = { onEvent(ServerConnectUiEvent.UrlChanged(it)) },
                label = "Server URL",
                placeholder = "example.com or 10.0.2.2:8080",
                isError = state.error != null,
                supportingText = state.error?.message,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onEvent(ServerConnectUiEvent.ConnectClicked) }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Connect button pinned to bottom
        ListenUpButton(
            text = "Connect",
            onClick = { onEvent(ServerConnectUiEvent.ConnectClicked) },
            isLoading = state.isLoading,
            enabled = state.isConnectEnabled
        )
    }
}

// ========================================
// Previews
// ========================================

@Preview(name = "Compact - Empty", showSystemUi = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun PreviewCompactEmpty() {
    ListenUpTheme {
        CompactLayout(
            state = ServerConnectUiState(),
            onEvent = {}
        )
    }
}

@Preview(name = "Compact - With URL", showSystemUi = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun PreviewCompactWithUrl() {
    ListenUpTheme {
        CompactLayout(
            state = ServerConnectUiState(
                serverUrl = "https://listenup.example.com"
            ),
            onEvent = {}
        )
    }
}

@Preview(name = "Compact - Loading", showSystemUi = true, device = "spec:width=411dp,height=891dp")
@Composable
private fun PreviewCompactLoading() {
    ListenUpTheme {
        CompactLayout(
            state = ServerConnectUiState(
                serverUrl = "https://listenup.example.com",
                isLoading = true
            ),
            onEvent = {}
        )
    }
}

@Preview(name = "Medium - Tablet", showSystemUi = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun PreviewMediumTablet() {
    ListenUpTheme {
        MediumLayout(
            state = ServerConnectUiState(
                serverUrl = "https://listenup.example.com"
            ),
            onEvent = {}
        )
    }
}

@Preview(name = "Expanded - Desktop", showSystemUi = true, device = "spec:width=1920dp,height=1080dp,dpi=160")
@Composable
private fun PreviewExpandedDesktop() {
    ListenUpTheme {
        ExpandedLayout(
            state = ServerConnectUiState(
                serverUrl = "https://listenup.example.com"
            ),
            onEvent = {}
        )
    }
}
