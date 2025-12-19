package com.calypsan.listenup.client.data.sync.conflict

import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ConflictDetector.
 *
 * Verifies offline-first conflict detection logic:
 * - Server newer than local unsynced → conflict detected
 * - Local newer than server → preserve local (no conflict)
 * - Already synced books → no conflict
 * - Books not in local DB → no conflict
 */
class ConflictDetectorTest {

    private fun createBookEntity(
        id: String,
        syncState: SyncState,
        lastModified: Long,
        updatedAt: Long = lastModified,
    ): BookEntity =
        BookEntity(
            id = BookId(id),
            title = "Book $id",
            coverUrl = null,
            totalDuration = 3_600_000L,
            syncState = syncState,
            lastModified = Timestamp(lastModified),
            serverVersion = Timestamp(updatedAt),
            createdAt = Timestamp(1000L),
            updatedAt = Timestamp(updatedAt),
        )

    // ========== detectBookConflicts Tests ==========

    @Test
    fun `detectBookConflicts returns empty when no local books exist`() = runTest {
        // Given - server has books but local DB is empty
        val bookDao: BookDao = mock()
        val detector = ConflictDetector(bookDao)

        val serverBooks = listOf(
            createBookEntity("book-1", SyncState.SYNCED, lastModified = 1000L, updatedAt = 2000L),
        )

        everySuspend { bookDao.getById(BookId("book-1")) } returns null

        // When
        val conflicts = detector.detectBookConflicts(serverBooks)

        // Then - no conflict because book doesn't exist locally
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `detectBookConflicts returns empty when local book is already synced`() = runTest {
        // Given - local book exists but is SYNCED (no local changes)
        val bookDao: BookDao = mock()
        val detector = ConflictDetector(bookDao)

        val serverBook = createBookEntity("book-1", SyncState.SYNCED, lastModified = 1000L, updatedAt = 2000L)
        val localBook = createBookEntity("book-1", SyncState.SYNCED, lastModified = 1000L)

        everySuspend { bookDao.getById(BookId("book-1")) } returns localBook

        // When
        val conflicts = detector.detectBookConflicts(listOf(serverBook))

        // Then - no conflict because local is already synced
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `detectBookConflicts detects conflict when server is newer than local unsynced`() = runTest {
        // Given - local has unsynced changes, but server has newer version
        val bookDao: BookDao = mock()
        val detector = ConflictDetector(bookDao)

        val serverBook = createBookEntity("book-1", SyncState.SYNCED, lastModified = 3000L, updatedAt = 3000L)
        val localBook = createBookEntity("book-1", SyncState.NOT_SYNCED, lastModified = 2000L)

        everySuspend { bookDao.getById(BookId("book-1")) } returns localBook

        // When
        val conflicts = detector.detectBookConflicts(listOf(serverBook))

        // Then - conflict detected: server (3000) > local (2000)
        assertEquals(1, conflicts.size)
        assertEquals(BookId("book-1"), conflicts[0].first)
        assertEquals(Timestamp(3000L), conflicts[0].second)
    }

    @Test
    fun `detectBookConflicts returns empty when local unsynced is newer than server`() = runTest {
        // Given - local has unsynced changes that are NEWER than server
        val bookDao: BookDao = mock()
        val detector = ConflictDetector(bookDao)

        val serverBook = createBookEntity("book-1", SyncState.SYNCED, lastModified = 2000L, updatedAt = 2000L)
        val localBook = createBookEntity("book-1", SyncState.NOT_SYNCED, lastModified = 3000L)

        everySuspend { bookDao.getById(BookId("book-1")) } returns localBook

        // When
        val conflicts = detector.detectBookConflicts(listOf(serverBook))

        // Then - no conflict: local (3000) >= server (2000)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `detectBookConflicts returns empty when timestamps are equal`() = runTest {
        // Given - local and server have same timestamp
        val bookDao: BookDao = mock()
        val detector = ConflictDetector(bookDao)

        val serverBook = createBookEntity("book-1", SyncState.SYNCED, lastModified = 2000L, updatedAt = 2000L)
        val localBook = createBookEntity("book-1", SyncState.NOT_SYNCED, lastModified = 2000L)

        everySuspend { bookDao.getById(BookId("book-1")) } returns localBook

        // When
        val conflicts = detector.detectBookConflicts(listOf(serverBook))

        // Then - no conflict: local (2000) >= server (2000)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `detectBookConflicts handles multiple books with mixed results`() = runTest {
        // Given - multiple books with different scenarios
        val bookDao: BookDao = mock()
        val detector = ConflictDetector(bookDao)

        val serverBooks = listOf(
            createBookEntity("book-1", SyncState.SYNCED, lastModified = 5000L, updatedAt = 5000L), // Conflict
            createBookEntity("book-2", SyncState.SYNCED, lastModified = 1000L, updatedAt = 1000L), // No conflict (local newer)
            createBookEntity("book-3", SyncState.SYNCED, lastModified = 3000L, updatedAt = 3000L), // No conflict (synced)
            createBookEntity("book-4", SyncState.SYNCED, lastModified = 4000L, updatedAt = 4000L), // No conflict (not in local)
        )

        everySuspend { bookDao.getById(BookId("book-1")) } returns
            createBookEntity("book-1", SyncState.NOT_SYNCED, lastModified = 2000L) // Conflict: server 5000 > local 2000

        everySuspend { bookDao.getById(BookId("book-2")) } returns
            createBookEntity("book-2", SyncState.NOT_SYNCED, lastModified = 3000L) // No conflict: local 3000 > server 1000

        everySuspend { bookDao.getById(BookId("book-3")) } returns
            createBookEntity("book-3", SyncState.SYNCED, lastModified = 2000L) // No conflict: already synced

        everySuspend { bookDao.getById(BookId("book-4")) } returns null // No conflict: not in local

        // When
        val conflicts = detector.detectBookConflicts(serverBooks)

        // Then - only book-1 has conflict
        assertEquals(1, conflicts.size)
        assertEquals(BookId("book-1"), conflicts[0].first)
    }

    // ========== shouldPreserveLocalChanges Tests ==========

    @Test
    fun `shouldPreserveLocalChanges returns false when book not in local DB`() = runTest {
        // Given
        val bookDao: BookDao = mock()
        val detector = ConflictDetector(bookDao)

        val serverBook = createBookEntity("book-1", SyncState.SYNCED, lastModified = 2000L)
        everySuspend { bookDao.getById(BookId("book-1")) } returns null

        // When
        val shouldPreserve = detector.shouldPreserveLocalChanges(serverBook)

        // Then - can't preserve what doesn't exist
        assertFalse(shouldPreserve)
    }

    @Test
    fun `shouldPreserveLocalChanges returns false when local is synced`() = runTest {
        // Given - local exists but has no unsynced changes
        val bookDao: BookDao = mock()
        val detector = ConflictDetector(bookDao)

        val serverBook = createBookEntity("book-1", SyncState.SYNCED, lastModified = 1000L, updatedAt = 1000L)
        val localBook = createBookEntity("book-1", SyncState.SYNCED, lastModified = 2000L)

        everySuspend { bookDao.getById(BookId("book-1")) } returns localBook

        // When
        val shouldPreserve = detector.shouldPreserveLocalChanges(serverBook)

        // Then - no local changes to preserve
        assertFalse(shouldPreserve)
    }

    @Test
    fun `shouldPreserveLocalChanges returns true when local unsynced is newer`() = runTest {
        // Given - local has newer unsynced changes
        val bookDao: BookDao = mock()
        val detector = ConflictDetector(bookDao)

        val serverBook = createBookEntity("book-1", SyncState.SYNCED, lastModified = 2000L, updatedAt = 2000L)
        val localBook = createBookEntity("book-1", SyncState.NOT_SYNCED, lastModified = 3000L)

        everySuspend { bookDao.getById(BookId("book-1")) } returns localBook

        // When
        val shouldPreserve = detector.shouldPreserveLocalChanges(serverBook)

        // Then - local changes should be preserved
        assertTrue(shouldPreserve)
    }

    @Test
    fun `shouldPreserveLocalChanges returns true when timestamps equal`() = runTest {
        // Given - same timestamp, but local has unsynced changes
        val bookDao: BookDao = mock()
        val detector = ConflictDetector(bookDao)

        val serverBook = createBookEntity("book-1", SyncState.SYNCED, lastModified = 2000L, updatedAt = 2000L)
        val localBook = createBookEntity("book-1", SyncState.NOT_SYNCED, lastModified = 2000L)

        everySuspend { bookDao.getById(BookId("book-1")) } returns localBook

        // When
        val shouldPreserve = detector.shouldPreserveLocalChanges(serverBook)

        // Then - preserve local (tie goes to local)
        assertTrue(shouldPreserve)
    }

    @Test
    fun `shouldPreserveLocalChanges returns false when server is newer`() = runTest {
        // Given - server has newer changes
        val bookDao: BookDao = mock()
        val detector = ConflictDetector(bookDao)

        val serverBook = createBookEntity("book-1", SyncState.SYNCED, lastModified = 3000L, updatedAt = 3000L)
        val localBook = createBookEntity("book-1", SyncState.NOT_SYNCED, lastModified = 2000L)

        everySuspend { bookDao.getById(BookId("book-1")) } returns localBook

        // When
        val shouldPreserve = detector.shouldPreserveLocalChanges(serverBook)

        // Then - server wins, don't preserve local
        assertFalse(shouldPreserve)
    }
}
