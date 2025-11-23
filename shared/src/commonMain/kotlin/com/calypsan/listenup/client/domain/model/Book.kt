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
    val author: String,
    val narrator: String?,
    val duration: Long, // Milliseconds
    val coverPath: String?, // Local file path or null if no cover
    val addedAt: Timestamp,
    val updatedAt: Timestamp
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
}
