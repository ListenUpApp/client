package com.calypsan.listenup.client.data.local.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookEntityTest {

    @Test
    fun bookEntity_implementsSyncable() {
        val book = BookEntity(
            id = "book-123",
            title = "Test Book",
            author = "Test Author",
            coverUrl = null,
            totalDuration = 3600000,
            syncState = SyncState.SYNCED,
            lastModified = 1700000000000,
            serverVersion = 1700000000000,
            createdAt = 1700000000000,
            updatedAt = 1700000000000
        )

        assertTrue(book is Syncable)
        assertEquals(SyncState.SYNCED, book.syncState)
        assertEquals(1700000000000, book.lastModified)
        assertEquals(1700000000000, book.serverVersion)
    }

    @Test
    fun bookEntity_canHaveNullServerVersion() {
        val book = BookEntity(
            id = "book-456",
            title = "New Book",
            author = "New Author",
            coverUrl = null,
            totalDuration = 0,
            syncState = SyncState.NOT_SYNCED,
            lastModified = 1700000000000,
            serverVersion = null, // Never synced
            createdAt = 1700000000000,
            updatedAt = 1700000000000
        )

        assertNull(book.serverVersion)
        assertEquals(SyncState.NOT_SYNCED, book.syncState)
    }

    @Test
    fun bookEntity_hasRequiredFields() {
        val book = BookEntity(
            id = "book-789",
            title = "Complete Book",
            author = "Full Author",
            coverUrl = "https://example.com/cover.jpg",
            totalDuration = 7200000,
            syncState = SyncState.SYNCED,
            lastModified = 1700000000000,
            serverVersion = 1700000000000,
            createdAt = 1700000000000,
            updatedAt = 1700000000000
        )

        assertNotNull(book.id)
        assertNotNull(book.title)
        assertNotNull(book.author)
        assertNotNull(book.coverUrl)
        assertEquals(7200000, book.totalDuration)
    }
}
