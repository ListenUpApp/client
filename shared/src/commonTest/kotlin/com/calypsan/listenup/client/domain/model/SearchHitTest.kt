package com.calypsan.listenup.client.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for SearchHit domain model.
 *
 * Covers duration formatting with various edge cases.
 */
class SearchHitTest {
    private fun createSearchHit(duration: Long? = null): SearchHit =
        SearchHit(
            id = "hit-1",
            type = SearchHitType.BOOK,
            name = "Test Book",
            duration = duration,
        )

    // ========== Duration Formatting Tests ==========

    @Test
    fun `formatDuration returns null when duration is null`() {
        val hit = createSearchHit(duration = null)
        assertNull(hit.formatDuration())
    }

    @Test
    fun `formatDuration returns hours and minutes for long duration`() {
        val hit = createSearchHit(duration = 7_200_000L) // 2 hours
        assertEquals("2h 0m", hit.formatDuration())
    }

    @Test
    fun `formatDuration returns only minutes for short duration`() {
        val hit = createSearchHit(duration = 1_800_000L) // 30 minutes
        assertEquals("30m", hit.formatDuration())
    }

    @Test
    fun `formatDuration handles mixed hours and minutes`() {
        val hit = createSearchHit(duration = 5_400_000L) // 1.5 hours (90 minutes)
        assertEquals("1h 30m", hit.formatDuration())
    }

    @Test
    fun `formatDuration handles zero duration`() {
        val hit = createSearchHit(duration = 0L)
        assertEquals("0m", hit.formatDuration())
    }

    @Test
    fun `formatDuration ignores seconds`() {
        // 1 hour, 30 minutes, 30 seconds - should show 1h 30m
        val hit = createSearchHit(duration = 5_430_000L)
        assertEquals("1h 30m", hit.formatDuration())
    }
}
