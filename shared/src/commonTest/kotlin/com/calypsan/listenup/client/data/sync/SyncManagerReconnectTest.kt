package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.Timestamp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for SSE reconnection behavior.
 *
 * Validates:
 * - Reconnected event type carries disconnectedAt timestamp
 * - Reconnected events are distinguishable by timestamp
 * - SSEEventType sealed interface properly includes Reconnected
 * - Reconnected event can be pattern-matched in when expressions
 */
class SyncManagerReconnectTest {

    @Test
    fun `Reconnected event carries disconnectedAt timestamp`() {
        val timestamp = "2026-03-14T18:30:00.000Z"
        val event = SSEEventType.Reconnected(disconnectedAt = timestamp)

        assertEquals(timestamp, event.disconnectedAt)
    }

    @Test
    fun `Reconnected event is data class with equality`() {
        val ts = "2026-03-14T18:30:00.000Z"
        val event1 = SSEEventType.Reconnected(disconnectedAt = ts)
        val event2 = SSEEventType.Reconnected(disconnectedAt = ts)

        assertEquals(event1, event2)
    }

    @Test
    fun `Reconnected events with different timestamps are not equal`() {
        val event1 = SSEEventType.Reconnected(disconnectedAt = "2026-03-14T18:30:00.000Z")
        val event2 = SSEEventType.Reconnected(disconnectedAt = "2026-03-14T18:31:00.000Z")

        assertTrue(event1 != event2)
    }

    @Test
    fun `Reconnected event is SSEEventType`() {
        val event: SSEEventType = SSEEventType.Reconnected(disconnectedAt = "2026-03-14T18:30:00.000Z")

        assertIs<SSEEventType.Reconnected>(event)
        assertNotNull(event.disconnectedAt)
    }

    @Test
    fun `Reconnected event can be pattern matched in when expression`() {
        val event: SSEEventType = SSEEventType.Reconnected(disconnectedAt = "2026-03-14T18:30:00.000Z")

        val result = when (event) {
            is SSEEventType.Reconnected -> event.disconnectedAt
            else -> null
        }

        assertEquals("2026-03-14T18:30:00.000Z", result)
    }

    @Test
    fun `Reconnected event flows through SharedFlow with timestamp`() = runTest {
        val eventFlow = MutableSharedFlow<SSEEventType>(replay = 1)
        val disconnectedAt = "2026-03-14T18:30:00.000Z"

        eventFlow.emit(SSEEventType.Reconnected(disconnectedAt = disconnectedAt))

        val received = eventFlow.first()
        assertIs<SSEEventType.Reconnected>(received)
        assertEquals(disconnectedAt, received.disconnectedAt)
    }

    @Test
    fun `Reconnected event is correctly identified vs other event types`() {
        val reconnected = SSEEventType.Reconnected(disconnectedAt = "2026-03-14T18:30:00.000Z")
        val heartbeat = SSEEventType.Heartbeat

        assertIs<SSEEventType.Reconnected>(reconnected)
        assertIs<SSEEventType.Heartbeat>(heartbeat)
        assertTrue(reconnected != heartbeat)
    }

    @Test
    fun `Timestamp toIsoString produces RFC3339 format`() {
        // Verify Timestamp.toIsoString() produces valid strings that could be used as disconnectedAt
        val timestamp = Timestamp.now()
        val iso = timestamp.toIsoString()

        // RFC3339 format should contain 'T' separator and end with 'Z' or timezone offset
        assertTrue(iso.contains("T"), "ISO string should contain T separator: $iso")
        assertTrue(iso.contains("Z") || iso.contains("+") || iso.contains("-"), "ISO string should have timezone: $iso")
    }
}
