package com.calypsan.listenup.client.features.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.calypsan.listenup.client.playback.NowPlayingState

/**
 * Height reserved for the mini player in shell content.
 * Includes the bar height (80dp) + bottom padding (8dp) + breathing room (8dp).
 * Content on shell screens should add this as bottom padding to avoid overlap.
 */
val MiniPlayerReservedHeight = 96.dp

/**
 * Floating mini player that appears above bottom navigation.
 *
 * M3 Expressive styling:
 * - Full pill shape (28dp corners)
 * - Diverse button sizes (play larger than skips)
 * - Pill-shaped skip buttons (horizontally elongated)
 * - Tonal elevation for depth
 */
@Composable
fun NowPlayingBar(
    state: NowPlayingState,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.isVisible && !state.isExpanded,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            onClick = onTap,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cover art
                    AsyncImage(
                        model = state.coverUrl,
                        contentDescription = "Book cover",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(Modifier.width(12.dp))

                    // Text info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        state.chapterTitle?.let { chapter ->
                            Text(
                                text = chapter,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Controls
                    NowPlayingBarControls(
                        isPlaying = state.isPlaying,
                        onPlayPause = onPlayPause,
                        onSkipBack = onSkipBack,
                        onSkipForward = onSkipForward
                    )
                }

                // Progress bar
                LinearProgressIndicator(
                    progress = { state.bookProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {}
                )
            }
        }
    }
}

@Composable
private fun NowPlayingBarControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Skip back - rounded square, tonal
        FilledTonalIconButton(
            onClick = onSkipBack,
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Replay10,
                contentDescription = "Skip back 10 seconds",
                modifier = Modifier.size(20.dp)
            )
        }

        // Play/Pause - larger, filled, rounded square (hero button)
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(28.dp)
            )
        }

        // Skip forward - rounded square, tonal
        FilledTonalIconButton(
            onClick = onSkipForward,
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Forward30,
                contentDescription = "Skip forward 30 seconds",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
