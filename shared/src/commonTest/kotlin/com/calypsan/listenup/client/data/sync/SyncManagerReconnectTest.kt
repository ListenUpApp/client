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
 * - Reconnected message carries disconnectedAt timestamp
 * - Reconnected messages are distinguishable by timestamp
 * - SSEChannelMessage sealed interface properly includes Reconnected
 * - Reconnected message can be pattern-matched in when expressions
 */
class SyncManagerReconnectTest {
    @Test
    fun `Reconnected message carries disconnectedAt timestamp`() {
        val timestamp = "2026-03-14T18:30:00.000Z"
        val message = SSEChannelMessage.Reconnected(disconnectedAt = timestamp)

        assertEquals(timestamp, message.disconnectedAt)
    }

    @Test
    fun `Reconnected message is data class with equality`() {
        val ts = "2026-03-14T18:30:00.000Z"
        val m1 = SSEChannelMessage.Reconnected(disconnectedAt = ts)
        val m2 = SSEChannelMessage.Reconnected(disconnectedAt = ts)

        assertEquals(m1, m2)
    }

    @Test
    fun `Reconnected messages with different timestamps are not equal`() {
        val m1 = SSEChannelMessage.Reconnected(disconnectedAt = "2026-03-14T18:30:00.000Z")
        val m2 = SSEChannelMessage.Reconnected(disconnectedAt = "2026-03-14T18:31:00.000Z")

        assertTrue(m1 != m2)
    }

    @Test
    fun `Reconnected message is SSEChannelMessage`() {
        val message: SSEChannelMessage = SSEChannelMessage.Reconnected(disconnectedAt = "2026-03-14T18:30:00.000Z")

        assertIs<SSEChannelMessage.Reconnected>(message)
        assertNotNull(message.disconnectedAt)
    }

    @Test
    fun `Reconnected message can be pattern matched in when expression`() {
        val message: SSEChannelMessage = SSEChannelMessage.Reconnected(disconnectedAt = "2026-03-14T18:30:00.000Z")

        val result =
            when (message) {
                is SSEChannelMessage.Reconnected -> message.disconnectedAt
                is SSEChannelMessage.Wire -> null
            }

        assertEquals("2026-03-14T18:30:00.000Z", result)
    }

    @Test
    fun `Reconnected message flows through SharedFlow with timestamp`() =
        runTest {
            val eventFlow = MutableSharedFlow<SSEChannelMessage>(replay = 1)
            val disconnectedAt = "2026-03-14T18:30:00.000Z"

            eventFlow.emit(SSEChannelMessage.Reconnected(disconnectedAt = disconnectedAt))

            val received = eventFlow.first()
            assertIs<SSEChannelMessage.Reconnected>(received)
            assertEquals(disconnectedAt, received.disconnectedAt)
        }

    @Test
    fun `Reconnected message is correctly identified vs Wire messages`() {
        val reconnected = SSEChannelMessage.Reconnected(disconnectedAt = "2026-03-14T18:30:00.000Z")
        val wire = SSEChannelMessage.Wire(SSEEvent.Heartbeat(timestamp = "2026-03-14T18:30:00.000Z"))

        assertIs<SSEChannelMessage.Reconnected>(reconnected)
        assertIs<SSEChannelMessage.Wire>(wire)
        assertTrue(reconnected != wire)
    }

    @Test
    fun `Timestamp toIsoString produces RFC3339 format`() {
        // Verify Timestamp.toIsoString() produces valid strings that could be used as disconnectedAt
        val timestamp = Timestamp.now()
        val iso = timestamp.toIsoString()

        // RFC3339 format should contain 'T' separator and end with 'Z' or timezone offset
        assertTrue(iso.contains("T"), "ISO string should contain T separator: $iso")
        assertTrue(
            iso.contains("Z") || iso.contains("+") || iso.contains("-"),
            "ISO string should have timezone: $iso",
        )
    }
}
