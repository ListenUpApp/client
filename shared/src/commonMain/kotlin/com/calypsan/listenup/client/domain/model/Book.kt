package com.calypsan.listenup.client.domain.model

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Timestamp

/**
 * Series membership with position within that series.
 */
data class BookSeries(
    val seriesId: String,
    val seriesName: String,
    val sequence: String? = null, // e.g., "1", "1.5"
)

/**
 * Domain model representing an audiobook.
 *
 * Clean model for UI consumption without sync infrastructure concerns.
 * Cover images are stored locally and accessed via file paths.
 */
data class Book(
    val id: BookId,
    val title: String,
    val sortTitle: String? = null,
    val subtitle: String? = null,
    val authors: List<BookContributor>,
    val narrators: List<BookContributor>,
    val allContributors: List<BookContributor> = emptyList(), // All contributors with all their roles
    val duration: Long, // Milliseconds
    val coverPath: String?, // Local file path or null if no cover
    val coverBlurHash: String? = null, // BlurHash for cover placeholder
    // Cached palette colors extracted from cover (ARGB ints for instant gradient rendering)
    val dominantColor: Int? = null,
    val darkMutedColor: Int? = null,
    val vibrantColor: Int? = null,
    val addedAt: Timestamp,
    val updatedAt: Timestamp,
    val description: String? = null,
    val genres: List<Genre> = emptyList(),
    val tags: List<Tag> = emptyList(),
    // Multiple series support (many-to-many)
    val series: List<BookSeries> = emptyList(),
    val publishYear: Int? = null,
    val publisher: String? = null,
    val language: String? = null, // ISO 639-1 code (e.g., "en", "es")
    val isbn: String? = null, // ISBN for metadata lookup
    val asin: String? = null, // Amazon ASIN for metadata lookup
    val abridged: Boolean = false, // Whether this is an abridged version
    val rating: Double? = null,
) {
    // Convenience properties for accessing primary series (first in list)
    val seriesId: String? get() = series.firstOrNull()?.seriesId
    val seriesName: String? get() = series.firstOrNull()?.seriesName
    val seriesSequence: String? get() = series.firstOrNull()?.sequence

    /**
     * Format duration as human-readable string.
     * Example: "2h 34m" or "45m"
     */
    fun formatDuration(): String {
        val totalMinutes = duration / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /**
     * Check if this book has a cover image available locally.
     */
    val hasCover: Boolean
        get() = coverPath != null

    /**
     * Combines series name and sequence for display, e.g., "A Song of Ice and Fire #1".
     * Returns null if no series name is available.
     * Uses first series if book belongs to multiple series.
     */
    val fullSeriesTitle: String?
        get() {
            val firstSeries = series.firstOrNull() ?: return null
            val name = firstSeries.seriesName
            val seq = firstSeries.sequence
            return if (seq != null && seq.isNotBlank()) {
                "$name #$seq"
            } else {
                name
            }
        }

    /**
     * Helper to get comma-separated author names for display.
     */
    val authorNames: String
        get() = authors.joinToString(", ") { it.name }

    /**
     * Helper to get comma-separated narrator names for display.
     */
    val narratorNames: String
        get() = narrators.joinToString(", ") { it.name }
}

/**
 * Lightweight representation of a contributor in the context of a specific book.
 *
 * Contains only the contributor's identity and their roles for the book (e.g., author, narrator).
 * For full contributor details (biography, image, etc.), see the [Contributor] domain model.
 */
data class BookContributor(
    val id: String,
    val name: String,
    val roles: List<String> = emptyList(),
)

/**
 * Domain model for a book chapter.
 */
data class Chapter(
    val id: String,
    val title: String,
    // Milliseconds
    val duration: Long,
    // Milliseconds
    val startTime: Long,
) {
    fun formatDuration(): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}
