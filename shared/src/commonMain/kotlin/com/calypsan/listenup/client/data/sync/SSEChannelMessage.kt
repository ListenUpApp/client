package com.calypsan.listenup.client.data.sync

/**
 * Consumer-facing message type emitted by [SSEManager.eventFlow].
 *
 * Splits the channel into wire events (decoded from server JSON) and synthetic
 * channel-level signals (like reconnect notifications that never round-trip through
 * JSON). Consumers pattern-match on [SSEChannelMessage] with compiler-checked
 * exhaustiveness.
 *
 * See [SSEEvent] for the polymorphic wire hierarchy inside [Wire].
 */
sealed interface SSEChannelMessage {
    /**
     * A wire event decoded from the SSE stream.
     */
    data class Wire(
        val event: SSEEvent,
    ) : SSEChannelMessage

    /**
     * SSE connection was re-established after a disconnect.
     * Emitted synthetically by [SSEManager]; not decoded from JSON.
     *
     * @property disconnectedAt RFC3339 timestamp of when the connection was lost.
     *   Used as `updated_after` for delta sync to catch missed events.
     */
    data class Reconnected(
        val disconnectedAt: String,
    ) : SSEChannelMessage
}
