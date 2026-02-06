package com.calypsan.listenup.client.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 loading indicator.
 *
 * Uses CircularProgressIndicator for cross-platform compatibility.
 * Android platform can override with M3 Expressive LoadingIndicator if available.
 *
 * @param modifier Optional modifier for the indicator
 * @param size Size of the indicator (default 48.dp)
 * @param color Color of the indicator (default primary)
 */
@Composable
fun ListenUpLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = 4.dp,
    )
}

/**
 * Small Material 3 loading indicator.
 *
 * Sized for inline use in buttons, list items, and compact spaces.
 *
 * @param modifier Optional modifier for the indicator
 * @param color Color of the indicator (default primary)
 */
@Composable
fun ListenUpLoadingIndicatorSmall(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    ListenUpLoadingIndicator(
        modifier = modifier,
        size = 24.dp,
        color = color,
    )
}

/**
 * Full-screen centered loading indicator.
 *
 * Convenience wrapper that centers the loading indicator within a Box
 * that fills all available space. Use this for loading states in screens.
 *
 * @param modifier Optional modifier for the outer Box
 * @param message Optional message to display below the indicator
 */
@Composable
fun FullScreenLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (message != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ListenUpLoadingIndicator()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        } else {
            ListenUpLoadingIndicator()
        }
    }
}
