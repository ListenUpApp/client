package com.calypsan.listenup.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.ServerConfig
import org.koin.compose.koinInject

/**
 * Root composable for the desktop application.
 *
 * Handles:
 * - Server connection state
 * - Authentication state
 * - Navigation between auth flow and main app
 *
 * Currently shows placeholder screens. Real screens will be wired
 * from composeApp once the navigation is implemented.
 */
@Composable
fun DesktopApp() {
    val serverConfig: ServerConfig = koinInject()
    val authSession: AuthSession = koinInject()

    // Collect auth state using LaunchedEffect
    var hasServer by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasServer = serverConfig.hasServerConfigured()
        isAuthenticated = authSession.isAuthenticated()
    }

    when {
        !hasServer -> ServerConnectPlaceholder()
        !isAuthenticated -> LoginPlaceholder()
        else -> MainAppPlaceholder()
    }
}

@Composable
private fun ServerConnectPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "ListenUp",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Connect to your server to get started",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // TODO: Replace with actual ServerConnectScreen from composeApp
            OutlinedButton(onClick = { /* TODO: Navigate to server setup */ }) {
                Text("Connect to Server")
            }
        }
    }
}

@Composable
private fun LoginPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Sign In",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Enter your credentials to continue",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // TODO: Replace with actual LoginScreen from composeApp
            OutlinedButton(onClick = { /* TODO: Navigate to login */ }) {
                Text("Sign In")
            }
        }
    }
}

@Composable
private fun MainAppPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Welcome to ListenUp!",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Desktop app is working. Full navigation coming soon.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Your audiobook library will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
