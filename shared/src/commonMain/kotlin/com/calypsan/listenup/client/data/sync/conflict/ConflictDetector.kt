package com.calypsan.listenup.client.data.sync.conflict

import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp

/**
 * Interface for conflict detection operations.
 *
 * Enables testing by allowing mock implementations.
 */
interface ConflictDetectorContract {
    /**
     * Detect conflicts where server has newer version than local unsynced changes.
     */
    suspend fun detectBookConflicts(serverBooks: List<BookEntity>): List<Pair<BookId, Timestamp>>

    /**
     * Check if local changes should be preserved (local is newer than server).
     */
    suspend fun shouldPreserveLocalChanges(serverBook: BookEntity): Boolean
}

/**
 * Detects conflicts between local and server data using timestamp comparison.
 *
 * Per offline-first-operations-design.md:
 * - If server timestamp > local edit timestamp -> conflict (server wins, mark for user review)
 * - If local edit timestamp >= server timestamp -> local wins (preserve local changes)
 */
class ConflictDetector(
    private val bookDao: BookDao,
) : ConflictDetectorContract {
    /**
     * Detect conflicts where server has newer version than local unsynced changes.
     *
     * @param serverBooks Books fetched from server
     * @return List of (BookId, Timestamp) pairs for books with conflicts
     */
    override suspend fun detectBookConflicts(serverBooks: List<BookEntity>): List<Pair<BookId, Timestamp>> =
        serverBooks.mapNotNull { serverBook ->
            bookDao
                .getById(serverBook.id)
                ?.takeIf { it.syncState == SyncState.NOT_SYNCED }
                ?.takeIf { serverBook.updatedAt > it.lastModified }
                ?.let { serverBook.id to serverBook.updatedAt }
        }

    /**
     * Check if local changes should be preserved (local is newer than server).
     *
     * @param serverBook Book from server
     * @return true if local version should be kept, false if server should overwrite
     */
    override suspend fun shouldPreserveLocalChanges(serverBook: BookEntity): Boolean =
        bookDao
            .getById(serverBook.id)
            ?.takeIf { it.syncState == SyncState.NOT_SYNCED }
            ?.let { it.lastModified >= serverBook.updatedAt }
            ?: false
}
