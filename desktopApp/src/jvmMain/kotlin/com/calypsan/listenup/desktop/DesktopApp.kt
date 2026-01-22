package com.calypsan.listenup.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.navigation.AuthNavigation

/**
 * Root composable for the desktop application.
 *
 * Handles:
 * - Authentication flow via shared AuthNavigation
 * - Navigation to main app after authentication
 *
 * Currently shows:
 * - Auth screens (LoginScreen, SetupScreen, etc.) shared with Android
 * - Placeholder main app after authentication
 *
 * TODO: Add shared library screen after auth is working
 */
@Composable
fun DesktopApp() {
    var isAuthenticated by remember { mutableStateOf(false) }

    if (!isAuthenticated) {
        AuthNavigation(
            onAuthenticated = {
                isAuthenticated = true
            },
        )
    } else {
        MainAppPlaceholder()
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
                text = "You're authenticated. Library screen coming soon.",
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
