package com.calypsan.listenup.client.util

import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.domain.model.Book

/**
 * Calculate playback progress for a list of books.
 *
 * Returns a map of bookId -> progress (0.0-1.0) for books that are
 * in progress (not unstarted, not complete).
 *
 * @param books The books to calculate progress for
 * @param excludeComplete If true (default), excludes books that are >99% complete
 * @param excludeUnstarted If true (default), excludes books with no progress
 * @return Map of bookId to progress fraction
 */
suspend fun PlaybackPositionDao.calculateProgressMap(
    books: List<Book>,
    excludeComplete: Boolean = true,
    excludeUnstarted: Boolean = true,
): Map<String, Float> =
    books
        .mapNotNull { book ->
            val position = get(BookId(book.id.value))
            if (position != null && book.duration > 0) {
                val progress = (position.positionMs.toFloat() / book.duration).coerceIn(0f, 1f)
                val isInProgress =
                    (!excludeUnstarted || progress > 0f) &&
                        (!excludeComplete || progress < 0.99f)
                if (isInProgress) book.id.value to progress else null
            } else {
                null
            }
        }.toMap()
