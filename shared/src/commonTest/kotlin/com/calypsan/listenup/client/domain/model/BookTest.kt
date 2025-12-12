package com.calypsan.listenup.client.domain.model

import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.Timestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Book domain model.
 *
 * Covers:
 * - Duration formatting
 * - Series title formatting
 * - Cover availability checks
 * - Contributor name formatting
 */
class BookTest {
    private fun createTestBook(
        duration: Long = 3600000L, // 1 hour
        coverPath: String? = null,
        authors: List<Contributor> = emptyList(),
        narrators: List<Contributor> = emptyList(),
        series: List<BookSeries> = emptyList(),
    ): Book =
        Book(
            id = BookId("book-1"),
            title = "Test Book",
            subtitle = null,
            authors = authors,
            narrators = narrators,
            duration = duration,
            coverPath = coverPath,
            addedAt = Timestamp.now(),
            updatedAt = Timestamp.now(),
            series = series,
        )

    // ========== Duration Formatting Tests ==========

    @Test
    fun `formatDuration returns hours and minutes for long duration`() {
        val book = createTestBook(duration = 9_000_000L) // 2.5 hours (150 minutes)
        assertEquals("2h 30m", book.formatDuration())
    }

    @Test
    fun `formatDuration returns only minutes for short duration`() {
        val book = createTestBook(duration = 2_700_000L) // 45 minutes
        assertEquals("45m", book.formatDuration())
    }

    @Test
    fun `formatDuration handles exactly one hour`() {
        val book = createTestBook(duration = 3_600_000L) // 60 minutes
        assertEquals("1h 0m", book.formatDuration())
    }

    @Test
    fun `formatDuration handles zero duration`() {
        val book = createTestBook(duration = 0L)
        assertEquals("0m", book.formatDuration())
    }

    @Test
    fun `formatDuration handles very long duration`() {
        val book = createTestBook(duration = 86_400_000L) // 24 hours
        assertEquals("24h 0m", book.formatDuration())
    }

    @Test
    fun `formatDuration truncates partial minutes`() {
        // 1 hour 30 minutes 45 seconds - should show 1h 30m (ignoring seconds)
        val book = createTestBook(duration = 5_445_000L)
        assertEquals("1h 30m", book.formatDuration())
    }

    // ========== Cover Path Tests ==========

    @Test
    fun `hasCover returns true when cover path is set`() {
        val book = createTestBook(coverPath = "/path/to/cover.jpg")
        assertTrue(book.hasCover)
    }

    @Test
    fun `hasCover returns false when cover path is null`() {
        val book = createTestBook(coverPath = null)
        assertFalse(book.hasCover)
    }

    // ========== Series Title Tests ==========

    @Test
    fun `fullSeriesTitle returns name and sequence when both present`() {
        val book = createTestBook(series = listOf(BookSeries(seriesId = "series-1", seriesName = "Mistborn", sequence = "1")))
        assertEquals("Mistborn #1", book.fullSeriesTitle)
    }

    @Test
    fun `fullSeriesTitle handles decimal sequence numbers`() {
        val book = createTestBook(series = listOf(BookSeries(seriesId = "series-1", seriesName = "Stormlight Archive", sequence = "2.5")))
        assertEquals("Stormlight Archive #2.5", book.fullSeriesTitle)
    }

    @Test
    fun `fullSeriesTitle returns only name when sequence is null`() {
        val book = createTestBook(series = listOf(BookSeries(seriesId = "series-1", seriesName = "Wheel of Time", sequence = null)))
        assertEquals("Wheel of Time", book.fullSeriesTitle)
    }

    @Test
    fun `fullSeriesTitle returns only name when sequence is blank`() {
        val book = createTestBook(series = listOf(BookSeries(seriesId = "series-1", seriesName = "Wheel of Time", sequence = "  ")))
        assertEquals("Wheel of Time", book.fullSeriesTitle)
    }

    @Test
    fun `fullSeriesTitle returns null when no series`() {
        val book = createTestBook(series = emptyList())
        assertNull(book.fullSeriesTitle)
    }

    // ========== Contributor Names Tests ==========

    @Test
    fun `authorNames joins multiple authors with commas`() {
        val book =
            createTestBook(
                authors =
                    listOf(
                        Contributor("1", "Brandon Sanderson"),
                        Contributor("2", "Robert Jordan"),
                    ),
            )
        assertEquals("Brandon Sanderson, Robert Jordan", book.authorNames)
    }

    @Test
    fun `authorNames returns single author name`() {
        val book =
            createTestBook(
                authors = listOf(Contributor("1", "Brandon Sanderson")),
            )
        assertEquals("Brandon Sanderson", book.authorNames)
    }

    @Test
    fun `authorNames returns empty string when no authors`() {
        val book = createTestBook(authors = emptyList())
        assertEquals("", book.authorNames)
    }

    @Test
    fun `narratorNames joins multiple narrators with commas`() {
        val book =
            createTestBook(
                narrators =
                    listOf(
                        Contributor("1", "Michael Kramer"),
                        Contributor("2", "Kate Reading"),
                    ),
            )
        assertEquals("Michael Kramer, Kate Reading", book.narratorNames)
    }

    @Test
    fun `narratorNames returns single narrator name`() {
        val book =
            createTestBook(
                narrators = listOf(Contributor("1", "Michael Kramer")),
            )
        assertEquals("Michael Kramer", book.narratorNames)
    }

    @Test
    fun `narratorNames returns empty string when no narrators`() {
        val book = createTestBook(narrators = emptyList())
        assertEquals("", book.narratorNames)
    }
}
