package com.calypsan.listenup.client.features.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.data.local.db.SeriesWithBooks
import com.calypsan.listenup.client.data.local.images.ImageStorage
import org.koin.compose.koinInject

/**
 * Card displaying a series with animated overlapping book covers.
 *
 * Features:
 * - Animated cover stack cycling through book covers
 * - Series name below covers
 * - Book count display
 * - Full-width layout for mobile
 * - Clickable to navigate to series detail
 *
 * @param seriesWithBooks The series with its associated books
 * @param onClick Callback when the card is clicked
 * @param modifier Optional modifier
 */
@Composable
fun SeriesCard(
    seriesWithBooks: SeriesWithBooks,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageStorage: ImageStorage = koinInject()
) {
    val series = seriesWithBooks.series
    val books = seriesWithBooks.books
    val bookCount = books.size

    // Extract cover paths from books, sorted by series sequence
    // Uses ImageStorage to resolve actual file paths from book IDs
    val coverPaths = books
        .sortedBy { it.sequence?.toFloatOrNull() ?: Float.MAX_VALUE }
        .map { bookEntity ->
            if (imageStorage.exists(bookEntity.id)) {
                imageStorage.getCoverPath(bookEntity.id)
            } else {
                null
            }
        }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Animated cover stack with Material 3 Expressive animations
            AnimatedCoverStack(
                coverPaths = coverPaths,
                coverHeight = 140.dp,
                cycleDurationMs = 3000L,
                maxVisibleCovers = 5
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Series name
            Text(
                text = series.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Book count
            Text(
                text = "$bookCount ${if (bookCount == 1) "book" else "books"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
