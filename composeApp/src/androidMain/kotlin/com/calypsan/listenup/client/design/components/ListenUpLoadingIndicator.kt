package com.calypsan.listenup.client.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive wavy loading indicator.
 *
 * Uses the new M3 Expressive LoadingIndicator which animates through
 * seven unique shape morphs, creating an organic, engaging experience
 * that feels less static during wait times.
 *
 * @param modifier Optional modifier for the indicator
 * @param size Size of the indicator (default 48.dp)
 * @param color Color of the indicator (default primary)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListenUpLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LoadingIndicator(
        modifier = modifier.size(size),
        color = color,
        polygons = LoadingIndicatorDefaults.DeterminateIndicatorPolygons,
    )
}

/**
 * Small Material 3 Expressive wavy loading indicator.
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
 */
@Composable
fun FullScreenLoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        ListenUpLoadingIndicator()
    }
}
