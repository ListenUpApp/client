package com.calypsan.listenup.client

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.repository.DeepLinkManager
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.data.sync.SSEManager
import com.calypsan.listenup.client.deeplink.DeepLinkParser
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.theme.ListenUpTheme
import com.calypsan.listenup.client.domain.model.Instance
import com.calypsan.listenup.client.domain.usecase.GetInstanceUseCase
import com.calypsan.listenup.client.navigation.ListenUpNavigation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main activity for the ListenUp app.
 *
 * Manages SSE connection lifecycle:
 * - Connects SSE when app comes to foreground (if authenticated)
 * - Disconnects SSE when app goes to background (saves battery)
 * - Auto-reconnects on app resume
 *
 * Handles deep links for invite URLs:
 * - https://server.com/join/{code} (App Links)
 * - listenup://join?server=...&code=... (custom scheme)
 *
 * This ensures real-time updates when actively using the app
 * while preserving battery life in the background.
 */
class MainActivity : ComponentActivity() {
    private val sseManager: SSEManager by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val deepLinkManager: DeepLinkManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Handle deep link from initial launch
        handleIntent(intent)

        setContent {
            ListenUpApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep link when app is already running (singleTask)
        handleIntent(intent)
    }

    /**
     * Parses and stores deep link data for navigation layer to consume.
     */
    private fun handleIntent(intent: Intent?) {
        DeepLinkParser.parse(intent)?.let { inviteLink ->
            println("MainActivity: Received invite deep link - server=${inviteLink.serverUrl}, code=${inviteLink.code}")
            deepLinkManager.setInviteLink(inviteLink.serverUrl, inviteLink.code)
        }
    }

    override fun onResume() {
        super.onResume()

        // Connect SSE when app comes to foreground (if authenticated)
        lifecycleScope.launch {
            val isAuthenticated = settingsRepository.getAccessToken() != null
            if (isAuthenticated) {
                println("MainActivity: App resumed and user authenticated, connecting SSE...")
                sseManager.connect()
            } else {
                println("MainActivity: App resumed but user not authenticated, skipping SSE")
            }
        }
    }

    override fun onPause() {
        super.onPause()

        // Disconnect SSE when app goes to background to save battery
        println("MainActivity: App paused, disconnecting SSE to save battery...")
        sseManager.disconnect()
    }
}

/**
 * Root composable for the ListenUp app.
 *
 * Wraps the entire app in Material 3 Expressive theme with:
 * - Dynamic color support (Android 12+)
 * - Display P3 HDR color space
 * - Google Sans Flex typography
 * - Expressive shapes (20-28dp corners)
 *
 * Navigation is auth-driven and automatically adjusts based on
 * authentication state from SettingsRepository.
 */
@Composable
fun ListenUpApp() {
    ListenUpTheme {
        ListenUpNavigation()
    }
}

/**
 * Screen that displays server instance information.
 */
@Composable
fun InstanceScreen(viewModel: InstanceViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading -> {
                ListenUpLoadingIndicator()
            }

            state.error != null -> {
                ErrorContent(
                    error = state.error!!,
                    onRetry = { viewModel.loadInstance() },
                )
            }

            state.instance != null -> {
                InstanceContent(instance = state.instance!!)
            }
        }
    }
}

@Composable
fun InstanceContent(instance: Instance) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "ListenUp Server",
                style = MaterialTheme.typography.headlineMedium,
            )

            HorizontalDivider()

            InfoRow(label = "Instance ID", value = instance.id.value)

            InfoRow(
                label = "Status",
                value = if (instance.isReady) "Ready" else "Needs Setup",
            )

            InfoRow(
                label = "Has Root User",
                value = if (instance.hasRootUser) "Yes" else "No",
            )

            if (instance.needsSetup) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                ) {
                    Text(
                        text = "⚠️ Server needs initial setup",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

/**
 * UI state for the instance screen.
 */
data class InstanceUiState(
    val isLoading: Boolean = true,
    val instance: Instance? = null,
    val error: String? = null,
)

/**
 * ViewModel for managing instance data and state.
 *
 * Follows modern Android architecture with StateFlow for reactive UI updates.
 */
class InstanceViewModel(
    private val getInstanceUseCase: GetInstanceUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(InstanceUiState())
    val state: StateFlow<InstanceUiState> = _state.asStateFlow()

    init {
        loadInstance()
    }

    fun loadInstance() {
        viewModelScope.launch {
            _state.value = InstanceUiState(isLoading = true)

            when (val result = getInstanceUseCase()) {
                is Result.Success -> {
                    _state.value =
                        InstanceUiState(
                            isLoading = false,
                            instance = result.data,
                        )
                }

                is Result.Failure -> {
                    _state.value =
                        InstanceUiState(
                            isLoading = false,
                            error = result.message,
                        )
                }
            }
        }
    }
}
