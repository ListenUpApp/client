package com.calypsan.listenup.client.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Styled loading indicator with Material 3 Expressive-inspired design.
 *
 * Uses a thicker stroke width and primary color for a more vibrant,
 * modern appearance. When M3 Expressive LoadingIndicator becomes available
 * in Compose Multiplatform, this can be upgraded to use the shape-morphing variant.
 *
 * @param modifier Optional modifier for the indicator
 */
@Composable
fun ListenUpLoadingIndicator(
    modifier: Modifier = Modifier
) {
    CircularProgressIndicator(
        modifier = modifier.size(48.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeWidth = 4.dp
    )
}

/**
 * Full-screen centered loading indicator.
 *
 * Convenience wrapper that centers the loading indicator within a Box
 * that fills all available space. Use this for loading states in screens.
 *
 * @param modifier Optional modifier for the outer Box
 */
@Composable
fun FullScreenLoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ListenUpLoadingIndicator()
    }
}
