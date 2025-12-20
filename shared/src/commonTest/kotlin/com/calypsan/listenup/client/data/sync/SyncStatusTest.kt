package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.sync.model.SyncPhase
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Tests for SyncStatus sealed interface.
 *
 * Covers:
 * - Status state properties
 * - Progress tracking
 * - Error handling
 * - Retry state
 */
class SyncStatusTest {
    // ========== Idle State Tests ==========

    @Test
    fun `Idle is a valid sync status`() {
        val status: SyncStatus = SyncStatus.Idle
        assertIs<SyncStatus.Idle>(status)
    }

    // ========== Syncing State Tests ==========

    @Test
    fun `Syncing is a valid sync status`() {
        val status: SyncStatus = SyncStatus.Syncing
        assertIs<SyncStatus.Syncing>(status)
    }

    // ========== Progress State Tests ==========

    @Test
    fun `Progress stores phase correctly`() {
        val status =
            SyncStatus.Progress(
                phase = SyncPhase.SYNCING_BOOKS,
                current = 5,
                total = 10,
                message = "Syncing books...",
            )

        assertEquals(SyncPhase.SYNCING_BOOKS, status.phase)
    }

    @Test
    fun `Progress stores current and total correctly`() {
        val status =
            SyncStatus.Progress(
                phase = SyncPhase.SYNCING_SERIES,
                current = 3,
                total = 20,
                message = "Progress message",
            )

        assertEquals(3, status.current)
        assertEquals(20, status.total)
    }

    @Test
    fun `Progress handles unknown total`() {
        val status =
            SyncStatus.Progress(
                phase = SyncPhase.SYNCING_CONTRIBUTORS,
                current = 1,
                total = -1, // Unknown total
                message = "Syncing...",
            )

        assertEquals(-1, status.total)
    }

    @Test
    fun `Progress stores message correctly`() {
        val message = "Syncing books (page 3)..."
        val status =
            SyncStatus.Progress(
                phase = SyncPhase.SYNCING_BOOKS,
                current = 3,
                total = 10,
                message = message,
            )

        assertEquals(message, status.message)
    }

    // ========== Retrying State Tests ==========

    @Test
    fun `Retrying stores attempt number correctly`() {
        val status = SyncStatus.Retrying(attempt = 2, maxAttempts = 3)

        assertEquals(2, status.attempt)
        assertEquals(3, status.maxAttempts)
    }

    @Test
    fun `Retrying can represent first retry`() {
        val status = SyncStatus.Retrying(attempt = 1, maxAttempts = 5)

        assertEquals(1, status.attempt)
    }

    @Test
    fun `Retrying can represent last retry`() {
        val status = SyncStatus.Retrying(attempt = 3, maxAttempts = 3)

        assertEquals(status.attempt, status.maxAttempts)
    }

    // ========== Success State Tests ==========

    @Test
    fun `Success stores timestamp correctly`() {
        val timestamp = Timestamp.now()
        val status = SyncStatus.Success(timestamp = timestamp)

        assertEquals(timestamp, status.timestamp)
    }

    @Test
    fun `Success timestamp is accessible`() {
        val status = SyncStatus.Success(timestamp = Timestamp(1_700_000_000_000L))

        assertEquals(1_700_000_000_000L, status.timestamp.epochMillis)
    }

    // ========== Error State Tests ==========

    @Test
    fun `Error stores exception correctly`() {
        val exception = RuntimeException("Sync failed")
        val status = SyncStatus.Error(exception = exception)

        assertEquals(exception, status.exception)
        assertEquals("Sync failed", status.exception.message)
    }

    @Test
    fun `Error preserves exception type`() {
        val exception = IllegalStateException("Invalid state")
        val status = SyncStatus.Error(exception = exception)

        assertIs<IllegalStateException>(status.exception)
    }

    @Test
    fun `Error handles exception with null message`() {
        val exception = RuntimeException()
        val status = SyncStatus.Error(exception = exception)

        assertNotNull(status.exception)
        assertEquals(null, status.exception.message)
    }

    // ========== SyncPhase Tests ==========

    @Test
    fun `SyncPhase has all expected phases`() {
        val phases = SyncPhase.entries

        assertEquals(5, phases.size)
        assertNotNull(phases.find { it == SyncPhase.FETCHING_METADATA })
        assertNotNull(phases.find { it == SyncPhase.SYNCING_BOOKS })
        assertNotNull(phases.find { it == SyncPhase.SYNCING_SERIES })
        assertNotNull(phases.find { it == SyncPhase.SYNCING_CONTRIBUTORS })
        assertNotNull(phases.find { it == SyncPhase.FINALIZING })
    }

    @Test
    fun `SyncPhase ordinals are stable`() {
        // These ordinals might be used for persistence or ordering
        assertEquals(0, SyncPhase.FETCHING_METADATA.ordinal)
        assertEquals(1, SyncPhase.SYNCING_BOOKS.ordinal)
        assertEquals(2, SyncPhase.SYNCING_SERIES.ordinal)
        assertEquals(3, SyncPhase.SYNCING_CONTRIBUTORS.ordinal)
        assertEquals(4, SyncPhase.FINALIZING.ordinal)
    }

    // ========== State Transition Pattern Tests ==========

    @Test
    fun `typical sync lifecycle follows expected pattern`() {
        // Simulating typical sync state transitions
        val states = mutableListOf<SyncStatus>()

        // 1. Start idle
        states.add(SyncStatus.Idle)

        // 2. Begin sync
        states.add(SyncStatus.Syncing)

        // 3. Progress updates
        states.add(
            SyncStatus.Progress(
                phase = SyncPhase.FETCHING_METADATA,
                current = 0,
                total = 3,
                message = "Preparing...",
            ),
        )

        states.add(
            SyncStatus.Progress(
                phase = SyncPhase.SYNCING_BOOKS,
                current = 1,
                total = 3,
                message = "Syncing books...",
            ),
        )

        // 4. Complete
        states.add(SyncStatus.Success(timestamp = Timestamp.now()))

        // Verify transitions
        assertIs<SyncStatus.Idle>(states[0])
        assertIs<SyncStatus.Syncing>(states[1])
        assertIs<SyncStatus.Progress>(states[2])
        assertIs<SyncStatus.Progress>(states[3])
        assertIs<SyncStatus.Success>(states[4])
    }

    @Test
    fun `retry lifecycle follows expected pattern`() {
        val states = mutableListOf<SyncStatus>()

        states.add(SyncStatus.Syncing)
        states.add(SyncStatus.Retrying(attempt = 1, maxAttempts = 3))
        states.add(SyncStatus.Retrying(attempt = 2, maxAttempts = 3))
        states.add(SyncStatus.Error(exception = RuntimeException("Failed after retries")))

        assertIs<SyncStatus.Syncing>(states[0])
        assertIs<SyncStatus.Retrying>(states[1])
        assertIs<SyncStatus.Retrying>(states[2])
        assertIs<SyncStatus.Error>(states[3])
    }
}
