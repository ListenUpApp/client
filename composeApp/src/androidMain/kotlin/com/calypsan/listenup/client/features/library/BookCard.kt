package com.calypsan.listenup.client.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.calypsan.listenup.client.domain.model.Book
import java.io.File

/**
 * Card displaying a single book in the library grid.
 *
 * Features:
 * - Cover image loaded from local storage via Coil
 * - Title and author text with ellipsis overflow
 * - Duration display
 * - Placeholder icon when cover is missing
 * - Material 3 Expressive shapes (large corner radius)
 * - Smooth animations on load via Coil crossfade
 *
 * @param book The book to display
 * @param onClick Callback when card is clicked
 * @param modifier Optional modifier for the card
 */
@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large) // 28dp corners (Expressive)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh) // Elevated surface with wallpaper tint
            .clickable(onClick = onClick)
    ) {
        // Cover Art
        BookCover(
            coverPath = book.coverPath,
            contentDescription = book.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f) // Square aspect ratio for audiobook covers
        )

        // Text content with natural spacing and proper padding from curved edges
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp) // Padding from left/right curves
        ) {
            Spacer(modifier = Modifier.height(12.dp)) // Top spacing

            // Title - single line for uniform card heights
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1, // Single line keeps all cards same height
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Author
            Text(
                text = book.authorNames,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Duration
            Text(
                text = book.formatDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp)) // Bottom spacing - clears the 28dp curve
        }
    }
}

/**
 * Book cover image with placeholder fallback.
 *
 * Loads image from local file path using Coil. Shows placeholder
 * icon if cover is missing or failed to load.
 *
 * @param coverPath Local file path to cover image (or null)
 * @param contentDescription Accessibility description
 * @param modifier Optional modifier
 */
@Composable
private fun BookCover(
    coverPath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (coverPath != null) {
            // Load from local file path
            val file = File(coverPath)
            AsyncImage(
                model = file,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.large)
            )
        } else {
            // Placeholder when no cover available
            Icon(
                imageVector = Icons.Default.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(32.dp)
            )
        }
    }
}
