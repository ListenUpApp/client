package com.calypsan.listenup.client.features.discover.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.presentation.discover.CurrentlyListeningUiBook
import com.calypsan.listenup.client.design.components.ListenUpAsyncImage
import com.calypsan.listenup.client.design.components.StackedAvatarsOverlay
import com.calypsan.listenup.client.presentation.discover.DiscoverViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Horizontal section showing books that other users are currently listening to.
 *
 * Displays book covers with stacked avatar overlays showing who's reading each book.
 * Creates social proof and enables discovery through others' reading activity.
 */
@Composable
fun CurrentlyListeningSection(
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val state by viewModel.currentlyListeningState.collectAsStateWithLifecycle()

    // Don't show section if empty or loading
    if (state.isEmpty) return

    Column(modifier = modifier) {
        // Section header
        Text(
            text = "What Others Are Listening To",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal scroll of book cards
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = state.books,
                key = { it.id },
            ) { book ->
                CurrentlyListeningCard(
                    book = book,
                    onClick = { onBookClick(book.id) },
                )
            }
        }
    }
}

/**
 * Card for a book in the Currently Listening section.
 *
 * Features:
 * - Book cover with stacked avatar overlay in bottom-right
 * - Press-to-scale animation
 * - Title and author below cover
 */
@Composable
private fun CurrentlyListeningCard(
    book: CurrentlyListeningUiBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "card_scale",
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
                onClick = onClick,
            ),
    ) {
        // Cover with avatar overlay
        CoverWithAvatarOverlay(
            coverPath = book.coverPath,
            blurHash = book.coverBlurHash,
            contentDescription = book.title,
            readers = book.readers,
            totalReaderCount = book.totalReaderCount,
            modifier = Modifier.aspectRatio(1f),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Metadata (title and author)
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            book.authorName?.takeIf { it.isNotBlank() }?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Cover art with stacked avatar overlay in bottom-right corner.
 */
@Composable
private fun CoverWithAvatarOverlay(
    coverPath: String?,
    blurHash: String?,
    contentDescription: String?,
    readers: List<com.calypsan.listenup.client.data.remote.CurrentlyListeningReaderResponse>,
    totalReaderCount: Int,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        // Cover image
        if (coverPath != null || blurHash != null) {
            ListenUpAsyncImage(
                path = coverPath,
                blurHash = blurHash,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Gradient placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
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
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        // Avatar overlay in bottom-right
        StackedAvatarsOverlay(
            readers = readers,
            totalCount = totalReaderCount,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
