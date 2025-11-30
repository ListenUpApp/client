package com.calypsan.listenup.client.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.playback.PlayerUiState
import com.calypsan.listenup.client.playback.PlayerViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Simple player screen for Phase 1 testing.
 *
 * Features:
 * - Play/pause toggle
 * - Skip forward (30s) / backward (10s)
 * - Seek slider
 * - Speed control (tap to cycle)
 * - Time display (current / total)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplePlayerScreen(
    bookId: String,
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel()
) {
    LaunchedEffect(bookId) {
        viewModel.playBook(com.calypsan.listenup.client.data.local.db.BookId(bookId))
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.bookTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stop()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    ListenUpLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onBackClick) {
                            Text("Go Back")
                        }
                    }
                }
                else -> {
                    PlayerContent(
                        state = state,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onSkipForward = { viewModel.skipForward() },
                        onSkipBackward = { viewModel.skipBackward() },
                        onSeek = viewModel::seekTo,
                        onCycleSpeed = viewModel::cycleSpeed
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerContent(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleSpeed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Book title (large)
        Text(
            text = state.bookTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Time display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = state.formattedPosition,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = state.formattedDuration,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Seek slider
        SeekSlider(
            progress = state.progress,
            totalDurationMs = state.totalDurationMs,
            onSeek = onSeek,
            enabled = !state.isLoading && state.totalDurationMs > 0
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Playback controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skip backward (10s)
            IconButton(
                onClick = onSkipBackward,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "Skip back 10 seconds",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Play/Pause button
            Box(contentAlignment = Alignment.Center) {
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // Skip forward (30s)
            IconButton(
                onClick = onSkipForward,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Forward30,
                    contentDescription = "Skip forward 30 seconds",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Speed control
        TextButton(
            onClick = onCycleSpeed,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = state.formattedSpeed,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }

        if (state.isFinished) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Book finished!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SeekSlider(
    progress: Float,
    totalDurationMs: Long,
    onSeek: (Long) -> Unit,
    enabled: Boolean
) {
    // Track user dragging separately from playback progress
    var isDragging by remember { mutableFloatStateOf(-1f) }
    val displayProgress = if (isDragging >= 0) isDragging else progress

    Slider(
        value = displayProgress,
        onValueChange = { newValue ->
            isDragging = newValue
        },
        onValueChangeFinished = {
            if (isDragging >= 0) {
                val seekPosition = (isDragging * totalDurationMs).toLong()
                onSeek(seekPosition)
                isDragging = -1f
            }
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    )
}
