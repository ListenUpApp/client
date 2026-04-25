package com.calypsan.listenup.client.domain.model

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Timestamp

/**
 * Domain model for a book in list/shelf surfaces.
 *
 * No `genres`, `tags`, or `allContributors` — those are detail-only and would
 * always be empty here. Use [BookDetail] when those fields are required.
 */
data class BookListItem(
    val id: BookId,
    val title: String,
    val sortTitle: String? = null,
    val subtitle: String? = null,
    val authors: List<BookContributor>,
    val narrators: List<BookContributor>,
    val duration: Long,
    val coverPath: String?,
    val coverBlurHash: String? = null,
    val dominantColor: Int? = null,
    val darkMutedColor: Int? = null,
    val vibrantColor: Int? = null,
    val addedAt: Timestamp,
    val updatedAt: Timestamp,
    val description: String? = null,
    val series: List<BookSeries> = emptyList(),
    val publishYear: Int? = null,
    val publisher: String? = null,
    val language: String? = null,
    val isbn: String? = null,
    val asin: String? = null,
    val abridged: Boolean = false,
    val rating: Double? = null,
) {
    val seriesId: String? get() = series.firstOrNull()?.seriesId
    val seriesName: String? get() = series.firstOrNull()?.seriesName
    val seriesSequence: String? get() = series.firstOrNull()?.sequence

    val hasCover: Boolean get() = coverPath != null

    val fullSeriesTitle: String?
        get() {
            val firstSeries = series.firstOrNull() ?: return null
            val name = firstSeries.seriesName
            val seq = firstSeries.sequence
            return if (seq != null && seq.isNotBlank()) "$name #$seq" else name
        }

    val authorNames: String get() = authors.joinToString(", ") { it.name }
    val narratorNames: String get() = narrators.joinToString(", ") { it.name }

    fun formatDuration(): String {
        val totalMinutes = duration / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
