package com.calypsan.listenup.client.features.nowplaying

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.calypsan.listenup.client.playback.NowPlayingState
import kotlin.time.Duration

/**
 * Full screen Now Playing view.
 *
 * M3 Expressive styling:
 * - Dynamic color glow from cover art behind the book
 * - Diverse button shapes (large play, medium skip, small chapter)
 * - Chapter-scoped seek bar
 */
@Composable
fun NowPlayingScreen(
    state: NowPlayingState,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSpeedClick: () -> Unit,
    onChaptersClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Extract dominant color from cover
    var dominantColor by remember { mutableStateOf(Color.Transparent) }

    // Drag-to-dismiss state
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dismissThreshold = screenHeightPx * 0.33f

    val dragOffset = remember { Animatable(0f) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragOffset.value
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dragOffset.value > dismissThreshold) {
                                // Animate off screen then collapse
                                dragOffset.animateTo(
                                    targetValue = screenHeightPx,
                                    animationSpec = tween(200)
                                )
                                onCollapse()
                            } else {
                                // Snap back to open
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(200)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragOffset.animateTo(0f, tween(200))
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        scope.launch {
                            // Only allow dragging down (positive values)
                            val newOffset = (dragOffset.value + dragAmount).coerceAtLeast(0f)
                            dragOffset.snapTo(newOffset)
                        }
                    }
                )
            },
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Large colored glow behind cover - rendered at screen level to avoid clipping
            if (dominantColor != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    dominantColor.copy(alpha = 0.6f),
                                    dominantColor.copy(alpha = 0.3f),
                                    dominantColor.copy(alpha = 0.1f),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(
                                    Float.POSITIVE_INFINITY / 2, // Will be centered
                                    350f
                                ),
                                radius = 800f
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                // Top bar with collapse handle
                NowPlayingTopBar(onCollapse = onCollapse)

                Spacer(Modifier.height(16.dp))

                // Cover art (glow is rendered behind at screen level)
                Box(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CoverArt(
                        coverUrl = state.coverUrl,
                        onColorExtracted = { dominantColor = it }
                    )
                }

            Spacer(Modifier.height(24.dp))

            // Title and chapter info
            TitleSection(
                title = state.title,
                author = state.author,
                chapterTitle = state.chapterTitle,
                chapterLabel = state.chapterLabel
            )

            Spacer(Modifier.height(24.dp))

            // Chapter seek bar
            ChapterSeekBar(
                progress = state.chapterProgress,
                currentTime = state.chapterPosition,
                totalTime = state.chapterDuration,
                onSeek = onSeek
            )

            Spacer(Modifier.height(32.dp))

            // Main controls
            MainControls(
                isPlaying = state.isPlaying,
                onPlayPause = onPlayPause,
                onSkipBack = onSkipBack,
                onSkipForward = onSkipForward,
                onPreviousChapter = onPreviousChapter,
                onNextChapter = onNextChapter
            )

            Spacer(Modifier.height(24.dp))

            // Secondary controls
            SecondaryControls(
                playbackSpeed = state.playbackSpeed,
                onSpeedClick = onSpeedClick,
                onChaptersClick = onChaptersClick,
                onSleepTimerClick = onSleepTimerClick
            )

            Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun NowPlayingTopBar(onCollapse: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Drag handle indicator
        Surface(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        ) {}

        // Collapse button
        IconButton(
            onClick = onCollapse,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Collapse"
            )
        }
    }
}

@Composable
private fun CoverArt(
    coverUrl: String?,
    onColorExtracted: (Color) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Cover art with shadow
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 24.dp,
        tonalElevation = 0.dp
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(coverUrl)
                .allowHardware(false)
                .build(),
            contentDescription = "Book cover",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            success = { state ->
                // Extract color when image loads successfully
                scope.launch(Dispatchers.IO) {
                    try {
                        val bitmap = state.result.image.toBitmap()
                        val color = extractDominantColor(bitmap)
                        if (color != null) {
                            withContext(Dispatchers.Main) {
                                onColorExtracted(color)
                            }
                        }
                    } catch (e: Exception) {
                        // Color extraction failed
                    }
                }
                SubcomposeAsyncImageContent()
            }
        )
    }
}

private fun extractDominantColor(bitmap: Bitmap): Color? {
    return try {
        val palette = Palette.from(bitmap).generate()
        val swatch = palette.vibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
        swatch?.rgb?.let { Color(it) }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun TitleSection(
    title: String,
    author: String,
    chapterTitle: String?,
    chapterLabel: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = author,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (chapterTitle != null) {
            Spacer(Modifier.height(8.dp))

            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = chapterLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChapterSeekBar(
    progress: Float,
    currentTime: Duration,
    totalTime: Duration,
    onSeek: (Float) -> Unit
) {
    // Track user dragging separately from playback progress
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }
    val displayProgress = if (isDragging) dragProgress else progress

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = displayProgress,
            onValueChange = { newValue ->
                isDragging = true
                dragProgress = newValue
            },
            onValueChangeFinished = {
                if (isDragging) {
                    onSeek(dragProgress)
                    isDragging = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = currentTime.formatPlaybackTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = totalTime.formatPlaybackTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * M3 Expressive media controls following ButtonGroup pattern.
 *
 * Key principles:
 * - UNIFORM height (all buttons 56dp)
 * - DIVERSE shape (leading/trailing rounded, middle squared)
 * - DIVERSE color (play button filled, others tonal)
 * - Connected layout (minimal 4dp gaps, flows as unit)
 * - Play button slightly wider via weight
 */
@Composable
private fun MainControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit
) {
    // Shape definitions matching M3 Expressive connected button group
    val buttonHeight = 56.dp
    val cornerRadius = 16.dp
    val fullRadius = 28.dp  // For pill ends

    // Leading shape: left side fully rounded, right side squared
    val leadingShape = RoundedCornerShape(
        topStart = fullRadius,
        bottomStart = fullRadius,
        topEnd = cornerRadius,
        bottomEnd = cornerRadius
    )

    // Middle shape: all corners slightly rounded (squircle)
    val middleShape = RoundedCornerShape(cornerRadius)

    // Trailing shape: right side fully rounded, left side squared
    val trailingShape = RoundedCornerShape(
        topStart = cornerRadius,
        bottomStart = cornerRadius,
        topEnd = fullRadius,
        bottomEnd = fullRadius
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous chapter - LEADING shape (left pill end)
        FilledTonalButton(
            onClick = onPreviousChapter,
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight),
            shape = leadingShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "Previous chapter",
                modifier = Modifier.size(24.dp)
            )
        }

        // Skip back 10s - MIDDLE shape
        FilledTonalButton(
            onClick = onSkipBack,
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight),
            shape = middleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                Icons.Default.Replay10,
                contentDescription = "Skip back 10 seconds",
                modifier = Modifier.size(24.dp)
            )
        }

        // Play/Pause - MIDDLE shape but FILLED color (hero) and wider
        Button(
            onClick = onPlayPause,
            modifier = Modifier
                .weight(1.4f)
                .height(buttonHeight),
            shape = middleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp)
            )
        }

        // Skip forward 30s - MIDDLE shape
        FilledTonalButton(
            onClick = onSkipForward,
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight),
            shape = middleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                Icons.Default.Forward30,
                contentDescription = "Skip forward 30 seconds",
                modifier = Modifier.size(24.dp)
            )
        }

        // Next chapter - TRAILING shape (right pill end)
        FilledTonalButton(
            onClick = onNextChapter,
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight),
            shape = trailingShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Next chapter",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SecondaryControls(
    playbackSpeed: Float,
    onSpeedClick: () -> Unit,
    onChaptersClick: () -> Unit,
    onSleepTimerClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TextButton(onClick = onSpeedClick) {
            Text(
                text = "${playbackSpeed}x",
                style = MaterialTheme.typography.labelLarge
            )
        }

        TextButton(onClick = onChaptersClick) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.labelLarge
            )
        }

        TextButton(onClick = onSleepTimerClick) {
            Icon(
                Icons.Default.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Sleep",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// Extension function for formatting playback time
fun Duration.formatPlaybackTime(): String {
    val hours = inWholeHours
    val minutes = inWholeMinutes % 60
    val seconds = inWholeSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
