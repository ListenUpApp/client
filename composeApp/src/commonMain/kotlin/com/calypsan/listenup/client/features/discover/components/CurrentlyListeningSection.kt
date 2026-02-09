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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.collectAsState
import com.calypsan.listenup.client.design.components.BookCoverImage
import com.calypsan.listenup.client.design.components.ProfileAvatar
import com.calypsan.listenup.client.presentation.discover.CurrentlyListeningUiSession
import com.calypsan.listenup.client.presentation.discover.DiscoverViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Horizontal section showing books that other users are currently listening to.
 *
 * Displays each active session as a card with book cover and user avatar.
 * Data comes from Room database via SSE sync - no API calls.
 */
@Composable
fun CurrentlyListeningSection(
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val state by viewModel.currentlyListeningState.collectAsState()

    // Don't show section if empty or loading
    if (state.isEmpty) return

    Column(modifier = modifier) {
        // Section header
        Text(
            text = "What Others Are Listening To",
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal scroll of session cards
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = state.sessions,
                key = { it.sessionId },
            ) { session ->
                CurrentlyListeningCard(
                    session = session,
                    onClick = { onBookClick(session.bookId) },
                )
            }
        }
    }
}

/**
 * Card for an active listening session.
 *
 * Features:
 * - Book cover with user avatar overlay in bottom-right
 * - Press-to-scale animation
 * - Book title and author below cover (user represented by avatar)
 */
@Composable
private fun CurrentlyListeningCard(
    session: CurrentlyListeningUiSession,
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
        modifier =
            modifier
                .width(140.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        // Cover with avatar overlay
        CoverWithAvatarOverlay(
            bookId = session.bookId,
            coverPath = session.coverPath,
            blurHash = session.coverBlurHash,
            contentDescription = session.bookTitle,
            userId = session.userId,
            displayName = session.displayName,
            avatarType = session.avatarType,
            avatarValue = session.avatarValue,
            avatarColor = session.avatarColor,
            modifier = Modifier.aspectRatio(1f),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Metadata (book title and author - user represented by avatar overlay)
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = session.bookTitle,
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            session.authorName?.let { author ->
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
 * Cover art with single user avatar overlay in bottom-right corner.
 *
 * Uses a wrapper Box to allow avatar to overflow the clipped cover area.
 */
@Composable
private fun CoverWithAvatarOverlay(
    bookId: String,
    coverPath: String?,
    blurHash: String?,
    contentDescription: String?,
    userId: String,
    displayName: String,
    avatarType: String,
    avatarValue: String?,
    avatarColor: String,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium

    // Outer Box allows avatar to overflow; inner Box clips the cover
    Box(modifier = modifier) {
        // Cover container with shadow and clip
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .shadow(elevation = 6.dp, shape = shape)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            // Cover image
            if (coverPath != null || blurHash != null) {
                BookCoverImage(
                    bookId = bookId,
                    coverPath = coverPath,
                    blurHash = blurHash,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Gradient placeholder
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
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
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }

        // Avatar overlay in bottom-right corner (slightly inset from edge)
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .size(36.dp)
                    .shadow(elevation = 4.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp),
        ) {
            ProfileAvatar(
                userId = userId,
                displayName = displayName,
                avatarColor = avatarColor,
                avatarType = avatarType,
                avatarValue = avatarValue,
                size = 32.dp,
            )
        }
    }
}
