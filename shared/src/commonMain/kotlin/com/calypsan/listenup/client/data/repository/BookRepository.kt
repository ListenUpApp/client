package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.sync.SyncManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow

/**
 * Repository for book data operations.
 *
 * Implements offline-first pattern:
 * - UI observes Room database via Flow
 * - Writes update local database immediately
 * - Sync happens in background
 *
 * Single source of truth: Room database
 *
 * @property bookDao Room DAO for book operations
 * @property syncManager Sync orchestrator for server communication
 */
class BookRepository(
    private val bookDao: BookDao,
    private val syncManager: SyncManager
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Observe all books as a reactive Flow.
     *
     * Returns Flow from Room that automatically emits new list
     * whenever any book changes. UI can collect this to display
     * book library with real-time updates.
     *
     * Offline-first: Returns local data immediately, even if
     * not yet synced with server.
     *
     * @return Flow emitting list of all books
     */
    fun observeBooks(): Flow<List<BookEntity>> {
        return bookDao.observeAll()
    }

    /**
     * Trigger sync to refresh books from server.
     *
     * Initiates pull operation via SyncManager. Room Flow will
     * automatically emit updated list when sync completes.
     *
     * Safe to call multiple times - SyncManager handles concurrent sync.
     *
     * @return Result indicating sync success or failure
     */
    suspend fun refreshBooks(): Result<Unit> {
        logger.debug { "Refreshing books from server" }
        return syncManager.sync()
    }

    // Future methods for v2:
    // - suspend fun updateProgress(bookId: String, position: Long)
    // - suspend fun toggleFavorite(bookId: String)
    // - suspend fun updateBookMetadata(bookId: String, ...)
}
