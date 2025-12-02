package com.calypsan.listenup.client.domain.model

import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.Timestamp

/**
 * Domain model representing an audiobook.
 *
 * Clean model for UI consumption without sync infrastructure concerns.
 * Separates data layer (BookEntity with sync fields) from presentation layer.
 *
 * Cover images are stored locally and accessed via file paths generated
 * by ImageStorage. No network access needed for display.
 */
data class Book(
    val id: BookId,
    val title: String,
    val subtitle: String? = null,
    val authors: List<Contributor>,
    val narrators: List<Contributor>,
    val allContributors: List<Contributor> = emptyList(), // All contributors with all their roles
    val duration: Long, // Milliseconds
    val coverPath: String?, // Local file path or null if no cover
    val addedAt: Timestamp,
    val updatedAt: Timestamp,
    val description: String? = null,
    val genres: String? = null, // Comma-separated string
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seriesSequence: String? = null, // e.g., "1", "1.5"
    val publishYear: Int? = null,
    val rating: Double? = null
) {
    /**
     * Format duration as human-readable string.
     * Example: "2h 34m" or "45m"
     */
    fun formatDuration(): String {
        val totalMinutes = duration / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    /**
     * Check if this book has a cover image available locally.
     */
    val hasCover: Boolean
        get() = coverPath != null

    /**
     * Combines series name and sequence for display, e.g., "A Song of Ice and Fire #1".
     * Returns null if no series name is available.
     */
    val fullSeriesTitle: String?
        get() = if (seriesName != null && seriesSequence != null && seriesSequence.isNotBlank()) {
            "$seriesName #$seriesSequence"
        } else if (seriesName != null) {
            seriesName
        } else {
            null
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

data class Contributor(
    val id: String,
    val name: String,
    val roles: List<String> = emptyList()
)

/**
 * Domain model for a book chapter.
 */
data class Chapter(
    val id: String,
    val title: String,
    val duration: Long, // Milliseconds
    val startTime: Long // Milliseconds
) {
    fun formatDuration(): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}
