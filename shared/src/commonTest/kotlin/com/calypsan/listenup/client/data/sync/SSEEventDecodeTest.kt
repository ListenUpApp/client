package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.appJson
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Regression coverage for W6 Phase B (Finding 07 D1) polymorphic SSE decode.
 *
 * Verifies per-variant round-trip decode, unknown-discriminator sentinel handling,
 * and malformed-payload resilience. Decode is exercised via the shared [appJson]
 * instance; the shape of each test fixture mirrors the server's actual wire format.
 */
class SSEEventDecodeTest {
    @Test
    fun `book created decodes to BookCreated variant`() {
        val json =
            """
            {
                "type": "book.created",
                "timestamp": "2026-04-18T10:00:00Z",
                "data": {
                    "book": {
                        "id": "b1",
                        "created_at": "2026-04-18T09:00:00Z",
                        "updated_at": "2026-04-18T10:00:00Z",
                        "title": "Test",
                        "sort_title": "Test",
                        "total_duration": 0
                    }
                }
            }
            """.trimIndent()
        val event = appJson.decodeFromString<SSEEvent>(json)
        assertIs<SSEEvent.BookCreated>(event)
        assertEquals("b1", event.data.book.id)
        assertEquals("2026-04-18T10:00:00Z", event.timestamp)
    }

    @Test
    fun `book deleted decodes to BookDeleted variant`() {
        val json =
            """
            {
                "type": "book.deleted",
                "timestamp": "2026-04-18T10:00:00Z",
                "data": { "book_id": "b1", "deleted_at": "2026-04-18T10:00:00Z" }
            }
            """.trimIndent()
        val event = appJson.decodeFromString<SSEEvent>(json)
        assertIs<SSEEvent.BookDeleted>(event)
        assertEquals("b1", event.data.bookId)
    }

    @Test
    fun `heartbeat decodes to Heartbeat variant without data`() {
        val json = """{"type": "heartbeat", "timestamp": "2026-04-18T10:00:00Z"}"""
        val event = appJson.decodeFromString<SSEEvent>(json)
        assertIs<SSEEvent.Heartbeat>(event)
    }

    @Test
    fun `progress_updated decodes with startedAt and finishedAt fields`() {
        val json =
            """
            {
                "type": "listening.progress_updated",
                "timestamp": "2026-04-18T10:00:00Z",
                "data": {
                    "book_id": "b1",
                    "current_position_ms": 6000,
                    "progress": 0.5,
                    "total_listen_time_ms": 3000,
                    "is_finished": false,
                    "last_played_at": "2026-04-18T10:00:00Z",
                    "started_at": "2026-04-18T09:00:00Z",
                    "finished_at": null
                }
            }
            """.trimIndent()
        val event = appJson.decodeFromString<SSEEvent>(json)
        assertIs<SSEEvent.ProgressUpdated>(event)
        assertEquals("b1", event.data.bookId)
        assertEquals("2026-04-18T09:00:00Z", event.data.startedAt)
        assertNull(event.data.finishedAt)
    }

    @Test
    fun `progress_updated decodes when startedAt and finishedAt are missing (pre-SP1 echo)`() {
        val json =
            """
            {
                "type": "listening.progress_updated",
                "timestamp": "2026-04-18T10:00:00Z",
                "data": {
                    "book_id": "b1",
                    "current_position_ms": 6000,
                    "progress": 0.5,
                    "total_listen_time_ms": 3000,
                    "is_finished": false,
                    "last_played_at": "2026-04-18T10:00:00Z"
                }
            }
            """.trimIndent()
        val event = appJson.decodeFromString<SSEEvent>(json)
        assertIs<SSEEvent.ProgressUpdated>(event)
        assertNull(event.data.startedAt)
        assertNull(event.data.finishedAt)
    }

    @Test
    fun `shelf created decodes all fields`() {
        val json =
            """
            {
                "type": "shelf.created",
                "timestamp": "2026-04-18T10:00:00Z",
                "data": {
                    "id": "s1",
                    "owner_id": "u1",
                    "name": "Favorites",
                    "description": null,
                    "book_count": 0,
                    "owner_display_name": "Alice",
                    "owner_avatar_color": "#ff0000",
                    "created_at": "2026-04-18T10:00:00Z",
                    "updated_at": "2026-04-18T10:00:00Z"
                }
            }
            """.trimIndent()
        val event = appJson.decodeFromString<SSEEvent>(json)
        assertIs<SSEEvent.ShelfCreated>(event)
        assertEquals("s1", event.data.id)
    }

    @Test
    fun `unknown discriminator decodes to Unknown sentinel with rawType preserved`() {
        val json =
            """
            {
                "type": "future.archived_event_type",
                "timestamp": "2026-04-18T10:00:00Z",
                "arbitrary_field": "that doesn't match any enumerated variant"
            }
            """.trimIndent()
        val event = appJson.decodeFromString<SSEEvent>(json)
        assertIs<SSEEvent.Unknown>(event)
        assertEquals("future.archived_event_type", event.rawType)
        assertEquals("2026-04-18T10:00:00Z", event.timestamp)
    }

    @Test
    fun `malformed JSON throws SerializationException`() {
        val malformed = "not valid json at all"
        assertFailsWith<SerializationException> {
            appJson.decodeFromString<SSEEvent>(malformed)
        }
    }

    @Test
    fun `missing type field throws SerializationException`() {
        val json = """{"timestamp": "2026-04-18T10:00:00Z"}"""
        assertFailsWith<SerializationException> {
            appJson.decodeFromString<SSEEvent>(json)
        }
    }

    @Test
    fun `known type with wrong payload shape throws SerializationException`() {
        // book.created expects {"data": {"book": {...}}} — supplying an empty `data`
        // object means the required `book` field is missing.
        val json =
            """
            {
                "type": "book.created",
                "timestamp": "2026-04-18T10:00:00Z",
                "data": {}
            }
            """.trimIndent()
        assertFailsWith<SerializationException> {
            appJson.decodeFromString<SSEEvent>(json)
        }
    }
}
