package com.calypsan.listenup.client.features.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * M3 Expressive wavy progress bar with seek functionality.
 *
 * Combines LinearWavyProgressIndicator with drag gesture handling
 * and a thumb indicator for seeking.
 *
 * @param progress Current progress value (0f to 1f)
 * @param onSeek Called when user finishes seeking with new progress value
 * @param modifier Modifier for the composable
 * @param enabled Whether seeking is enabled
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val density = LocalDensity.current

    // Track width for calculating drag position
    var trackWidth by remember { mutableFloatStateOf(0f) }

    // Track dragging state
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }

    // Use drag progress while dragging, otherwise use actual progress
    val displayProgress = if (isDragging) dragProgress else progress

    // Thumb size
    val thumbSize = 20.dp
    val thumbSizePx = with(density) { thumbSize.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp) // Touch target height
            .onSizeChanged { size ->
                trackWidth = size.width.toFloat()
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                detectTapGestures { offset ->
                    // Calculate progress from tap position
                    val newProgress = (offset.x / trackWidth).coerceIn(0f, 1f)
                    onSeek(newProgress)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / trackWidth).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        if (isDragging) {
                            onSeek(dragProgress)
                            isDragging = false
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (trackWidth > 0) {
                            dragProgress = (dragProgress + dragAmount / trackWidth).coerceIn(0f, 1f)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Wavy progress indicator track with gentle wave animation
        LinearWavyProgressIndicator(
            progress = { displayProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .align(Alignment.Center),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            amplitude = { 1f },
            wavelength = 24.dp,
            waveSpeed = 15.dp // Gentle wave animation
        )

        // Thumb indicator
        Box(
            modifier = Modifier
                .offset {
                    val thumbOffset = (displayProgress * (trackWidth - thumbSizePx)).roundToInt()
                    IntOffset(thumbOffset, 0)
                }
                .size(thumbSize)
                .shadow(
                    elevation = if (isDragging) 8.dp else 4.dp,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
