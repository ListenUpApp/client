package com.calypsan.listenup.client.features.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.calypsan.listenup.client.design.components.BookProgressBar
import com.calypsan.listenup.client.domain.model.ContinueListeningBook
import java.io.File

/**
 * Card for a book in the Continue Listening section.
 *
 * Features:
 * - Floating cover art with shadow
 * - Progress bar showing listening progress
 * - Time remaining indicator
 * - Press-to-scale animation
 *
 * @param book The continue listening book to display
 * @param onClick Callback when card is clicked
 * @param modifier Optional modifier
 */
@Composable
fun ContinueListeningCard(
    book: ContinueListeningBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "card_scale"
    )

    Column(
        modifier = modifier
            .width(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Cover with shadow
        CoverWithShadow(
            coverPath = book.coverPath,
            contentDescription = book.title,
            modifier = Modifier
                .aspectRatio(1f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar
        BookProgressBar(
            progress = book.progress,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Metadata
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = book.authorNames,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = book.timeRemainingFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Cover art with shadow for depth.
 */
@Composable
private fun CoverWithShadow(
    coverPath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = shape
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (coverPath != null) {
            val file = File(coverPath)
            AsyncImage(
                model = file,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            // Gradient placeholder
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}
