@file:Suppress("MagicNumber")

package com.calypsan.listenup.desktop.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.calypsan.listenup.client.design.components.ListenUpAsyncImage
import com.calypsan.listenup.client.playback.NowPlayingState
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Full-screen now-playing view for desktop.
 *
 * Centered layout with cover art, chapter seek bar, transport controls,
 * and speed selection. Designed for desktop window dimensions.
 */
@Composable
fun DesktopNowPlayingScreen(
    state: NowPlayingState,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSeekWithinChapter: (Float) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onClose: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Top bar with back and close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close player")
                }
            }

            // Centered content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Cover art
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    ListenUpAsyncImage(
                        path = state.coverUrl,
                        contentDescription = state.title,
                        blurHash = state.coverBlurHash,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Title and author
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 400.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.author,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Chapter label
                if (state.chapterLabel.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.chapterTitle ?: state.chapterLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 400.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Seek bar and time labels
                ChapterSeekSection(
                    chapterProgress = state.chapterProgress,
                    chapterPositionMs = state.chapterPositionMs,
                    chapterDurationMs = state.chapterDurationMs,
                    onSeek = onSeekWithinChapter,
                    modifier = Modifier.widthIn(max = 480.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Transport controls
                TransportControls(
                    isPlaying = state.isPlaying,
                    onPreviousChapter = onPreviousChapter,
                    onSkipBack = onSkipBack,
                    onPlayPause = onPlayPause,
                    onSkipForward = onSkipForward,
                    onNextChapter = onNextChapter,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Speed control
                SpeedControl(
                    currentSpeed = state.playbackSpeed,
                    onSetSpeed = onSetSpeed,
                )
            }
        }
    }
}

@Composable
private fun ChapterSeekSection(
    chapterProgress: Float,
    chapterPositionMs: Long,
    chapterDurationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = if (isDragging) dragProgress else chapterProgress.coerceIn(0f, 1f),
            onValueChange = { value ->
                isDragging = true
                dragProgress = value
            },
            onValueChangeFinished = {
                onSeek(dragProgress)
                isDragging = false
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatPlaybackTime(chapterPositionMs.milliseconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "-${formatPlaybackTime((chapterDurationMs - chapterPositionMs).coerceAtLeast(0).milliseconds)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    onPreviousChapter: () -> Unit,
    onSkipBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onNextChapter: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPreviousChapter, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous chapter", modifier = Modifier.size(24.dp))
        }

        IconButton(onClick = onSkipBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Replay10, contentDescription = "Skip back 10 seconds", modifier = Modifier.size(28.dp))
        }

        // Large play/pause button
        Surface(
            onClick = onPlayPause,
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        IconButton(onClick = onSkipForward, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Forward30, contentDescription = "Skip forward 30 seconds", modifier = Modifier.size(28.dp))
        }

        IconButton(onClick = onNextChapter, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next chapter", modifier = Modifier.size(24.dp))
        }
    }
}

private const val MIN_SPEED = 0.5f
private const val MAX_SPEED = 3.0f
private const val SPEED_STEP = 0.05f
private const val DEFAULT_SPEED = 1.0f
private val SPEED_PRESETS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) {
        "${speed.toInt()}.0x"
    } else {
        "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x"
    }

private fun snapSpeed(speed: Float): Float = (speed / SPEED_STEP).roundToInt() * SPEED_STEP

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedControl(
    currentSpeed: Float,
    onSetSpeed: (Float) -> Unit,
) {
    var showPopup by remember { mutableStateOf(false) }
    var sliderSpeed by remember(currentSpeed) { mutableStateOf(currentSpeed) }

    Box {
        FilledTonalButton(onClick = { showPopup = true }) {
            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatSpeed(currentSpeed),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (showPopup) {
            Popup(
                alignment = Alignment.BottomCenter,
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(320.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Playback Speed",
                            style = MaterialTheme.typography.titleMedium,
                        )

                        Spacer(Modifier.height(16.dp))

                        // Large speed display
                        Text(
                            text = formatSpeed(sliderSpeed),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        Spacer(Modifier.height(20.dp))

                        // Slider
                        Slider(
                            value = sliderSpeed,
                            onValueChange = { sliderSpeed = snapSpeed(it) },
                            onValueChangeFinished = { onSetSpeed(sliderSpeed) },
                            valueRange = MIN_SPEED..MAX_SPEED,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${MIN_SPEED}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${MAX_SPEED}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // Preset chips
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SPEED_PRESETS.forEach { preset ->
                                val isSelected = (sliderSpeed - preset).absoluteValue < 0.01f
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        sliderSpeed = preset
                                        onSetSpeed(preset)
                                    },
                                    label = { Text(formatSpeed(preset)) },
                                )
                            }
                        }

                        // Reset button (shown when not at 1.0x)
                        if ((sliderSpeed - DEFAULT_SPEED).absoluteValue > 0.01f) {
                            Spacer(Modifier.height(12.dp))
                            TextButton(
                                onClick = {
                                    sliderSpeed = DEFAULT_SPEED
                                    onSetSpeed(DEFAULT_SPEED)
                                },
                            ) {
                                Text("Reset to ${formatSpeed(DEFAULT_SPEED)}")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlaybackTime(duration: Duration): String {
    val hours = duration.inWholeHours
    val minutes = duration.inWholeMinutes % 60
    val seconds = duration.inWholeSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
