package com.calypsan.listenup.desktop.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.ListenUpAsyncImage
import com.calypsan.listenup.client.playback.NowPlayingState

/**
 * Mini player bar shown at the bottom of the desktop window during playback.
 *
 * Shows cover art, title/chapter info, playback controls, and a progress indicator.
 * Clicking the bar (outside of controls) expands to the full now-playing screen.
 */
@Composable
fun DesktopNowPlayingBar(
    state: NowPlayingState,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Column {
            // Progress indicator at top of bar
            LinearProgressIndicator(
                progress = { state.bookProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover art
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    ListenUpAsyncImage(
                        path = state.coverUrl,
                        contentDescription = state.title,
                        blurHash = state.coverBlurHash,
                        modifier = Modifier.size(48.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and chapter/error info
                Column(modifier = Modifier.weight(1f)) {
                    val errorMessage = state.errorMessage
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.chapterTitle ?: state.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Playback controls
                IconButton(onClick = onSkipBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Skip back",
                        modifier = Modifier.size(20.dp),
                    )
                }

                IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp),
                    )
                }

                IconButton(onClick = onSkipForward, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Skip forward",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
