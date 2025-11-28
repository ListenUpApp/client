package com.calypsan.listenup.client.data.remote.model

import com.calypsan.listenup.client.data.local.db.SyncState
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MappersTest {

    @Test
    fun bookResponseToEntity_mapsAllFields() {
        val response = BookResponse(
            id = "book-123",
            createdAt = "2025-11-22T10:00:00Z",
            updatedAt = "2025-11-22T14:30:45Z",
            deletedAt = null,
            title = "Test Book",
            subtitle = "Subtitle",
            author = "Test Author",
            coverImage = null,
            totalDuration = 3600000
        )

        val entity = response.toEntity()

        assertEquals("book-123", entity.id)
        assertEquals("Test Book", entity.title)
        assertEquals("Test Author", entity.author)
        assertNull(entity.coverUrl)
        assertEquals(3600000, entity.totalDuration)
        assertEquals(SyncState.SYNCED, entity.syncState)
    }

    @Test
    fun bookResponseToEntity_parsesCoverUrl() {
        val response = BookResponse(
            id = "book-456",
            createdAt = "2025-11-22T10:00:00Z",
            updatedAt = "2025-11-22T14:30:45Z",
            deletedAt = null,
            title = "Book with Cover",
            subtitle = null,
            author = "Author",
            coverImage = ImageFileInfoResponse(
                path = "/covers/book-456.jpg",
                filename = "cover.jpg",
                format = "jpeg",
                size = 125000,
                inode = 9876543,
                modTime = 1700000000000
            ),
            totalDuration = 7200000
        )

        val entity = response.toEntity()

        assertEquals("/covers/book-456.jpg", entity.coverUrl)
    }

    @Test
    fun bookResponseToEntity_handlesNullAuthor() {
        val response = BookResponse(
            id = "book-789",
            createdAt = "2025-11-22T10:00:00Z",
            updatedAt = "2025-11-22T14:30:45Z",
            deletedAt = null,
            title = "Book Without Author",
            subtitle = null,
            author = null,  // No author provided
            coverImage = null,
            totalDuration = 1800000
        )

        val entity = response.toEntity()

        assertEquals("Unknown Author", entity.author)
    }

    @Test
    fun iso8601ToEpochMillis_parsesTimestamp() {
        val timestamp = "2025-11-22T14:30:45Z"
        val millis = timestamp.toEpochMillis()

        val instant = Instant.fromEpochMilliseconds(millis)
        assertEquals("2025-11-22T14:30:45Z", instant.toString())
    }
}
