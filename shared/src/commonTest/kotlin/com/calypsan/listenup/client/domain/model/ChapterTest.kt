package com.calypsan.listenup.client.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for Chapter domain model.
 *
 * Covers duration formatting in MM:SS format.
 */
class ChapterTest {
    private fun createChapter(
        duration: Long = 60_000L, // 1 minute
    ): Chapter =
        Chapter(
            id = "chapter-1",
            title = "Chapter 1",
            duration = duration,
            startTime = 0L,
        )

    // ========== Duration Formatting Tests ==========

    @Test
    fun `formatDuration returns MM SS format`() {
        val chapter = createChapter(duration = 125_000L) // 2 minutes 5 seconds
        assertEquals("02:05", chapter.formatDuration())
    }

    @Test
    fun `formatDuration pads single digit minutes`() {
        val chapter = createChapter(duration = 300_000L) // 5 minutes
        assertEquals("05:00", chapter.formatDuration())
    }

    @Test
    fun `formatDuration pads single digit seconds`() {
        val chapter = createChapter(duration = 603_000L) // 10 minutes 3 seconds
        assertEquals("10:03", chapter.formatDuration())
    }

    @Test
    fun `formatDuration handles zero duration`() {
        val chapter = createChapter(duration = 0L)
        assertEquals("00:00", chapter.formatDuration())
    }

    @Test
    fun `formatDuration handles exactly one minute`() {
        val chapter = createChapter(duration = 60_000L)
        assertEquals("01:00", chapter.formatDuration())
    }

    @Test
    fun `formatDuration handles long chapters`() {
        val chapter = createChapter(duration = 3_661_000L) // 61 minutes 1 second
        assertEquals("61:01", chapter.formatDuration())
    }

    @Test
    fun `formatDuration handles very long chapters`() {
        val chapter = createChapter(duration = 7_200_000L) // 120 minutes (2 hours)
        assertEquals("120:00", chapter.formatDuration())
    }

    @Test
    fun `formatDuration ignores milliseconds`() {
        // 1 minute, 30 seconds, 500 milliseconds - should show 01:30
        val chapter = createChapter(duration = 90_500L)
        assertEquals("01:30", chapter.formatDuration())
    }
}
