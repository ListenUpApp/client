package com.calypsan.listenup.client.features.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calypsan.listenup.client.design.components.ListenUpAsyncImage
import com.calypsan.listenup.client.design.components.ProgressOverlay
import com.calypsan.listenup.client.domain.model.Book

/**
 * Floating book card with editorial design.
 *
 * Design philosophy: Cover art is the hero. No container boxing.
 * A soft glow radiates from behind, creating depth without harsh shadows.
 * Press interaction uses scale animation for tactile feedback.
 *
 * @param book The book to display
 * @param onClick Callback when card is clicked
 * @param progress Optional progress (0.0-1.0) to show overlay. Null = no overlay.
 * @param timeRemaining Optional formatted time remaining (e.g., "2h 15m left")
 * @param isInSelectionMode Whether multi-select mode is active
 * @param isSelected Whether this book is currently selected
 * @param onLongPress Callback when card is long-pressed (for entering selection mode)
 * @param modifier Optional modifier for the card
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    progress: Float? = null,
    timeRemaining: String? = null,
    isInSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    // Animate scale for press and selection
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isSelected -> 0.98f
            else -> 1f
        },
        label = "card_scale",
    )

    // Animate border color for selection
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        label = "border_color",
    )

    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = {
                        if (onLongPress != null) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        }
                    },
                ),
    ) {
        // Cover with glow, optional progress overlay, and selection indicator
        Box {
            CoverWithGlow(
                coverPath = book.coverPath,
                blurHash = book.coverBlurHash,
                contentDescription = book.title,
                progress = progress,
                timeRemaining = timeRemaining,
                isSelected = isSelected,
                borderColor = borderColor,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
            )

            // Selection checkbox indicator
            if (isInSelectionMode) {
                SelectionIndicator(
                    isSelected = isSelected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Metadata
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = book.title,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = book.authorNames,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = book.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Cover art with shadow for depth and optional progress overlay.
 *
 * Uses standard elevation shadow to lift the cover off the surface.
 *
 * @param coverPath Local file path to cover image
 * @param blurHash BlurHash string for placeholder
 * @param contentDescription Accessibility description
 * @param progress Optional progress (0.0-1.0) to show overlay
 * @param timeRemaining Optional formatted time remaining
 * @param isSelected Whether this item is selected in multi-select mode
 * @param borderColor Color for the selection border
 * @param modifier Optional modifier
 */
@Composable
private fun CoverWithGlow(
    coverPath: String?,
    blurHash: String?,
    contentDescription: String?,
    progress: Float? = null,
    timeRemaining: String? = null,
    isSelected: Boolean = false,
    borderColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier =
            modifier
                .shadow(
                    elevation = 6.dp,
                    shape = shape,
                ).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 3.dp,
                            color = borderColor,
                            shape = shape,
                        )
                    } else {
                        Modifier
                    }
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (coverPath != null || blurHash != null) {
            ListenUpAsyncImage(
                path = coverPath,
                blurHash = blurHash,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            // Gradient placeholder
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surfaceContainer,
                                    ),
                            ),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.padding(32.dp),
                )
            }
        }

        // Progress overlay (only shown when progress is provided and > 0)
        if (progress != null && progress > 0f) {
            ProgressOverlay(
                progress = progress,
                timeRemaining = timeRemaining,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Selection indicator shown when in multi-select mode.
 * Shows a filled checkmark when selected, empty circle when not.
 */
@Composable
private fun SelectionIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        },
        label = "selection_bg",
    )

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        label = "selection_icon",
    )

    Box(
        modifier = modifier
            .size(28.dp)
            .shadow(
                elevation = 2.dp,
                shape = CircleShape,
            )
            .background(
                color = backgroundColor,
                shape = CircleShape,
            )
            .then(
                if (!isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
