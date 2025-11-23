package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.toDomain
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.sync.SyncManager
import com.calypsan.listenup.client.domain.model.Book
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
 * Transforms data layer (BookEntity) to domain layer (Book) for UI consumption.
 * Uses ImageStorage to resolve local cover file paths.
 *
 * @property bookDao Room DAO for book operations
 * @property syncManager Sync orchestrator for server communication
 * @property imageStorage Storage for resolving cover image paths
 */
class BookRepository(
    private val bookDao: BookDao,
    private val syncManager: SyncManager,
    private val imageStorage: ImageStorage
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Observe all books as a reactive Flow of domain models.
     *
     * Returns Flow from Room that automatically emits new list
     * whenever any book changes. UI can collect this to display
     * book library with real-time updates.
     *
     * Transforms BookEntity to domain Book model with local cover paths.
     *
     * Offline-first: Returns local data immediately, even if
     * not yet synced with server.
     *
     * @return Flow emitting list of domain Book models
     */
    fun observeBooks(): Flow<List<Book>> {
        return bookDao.observeAll().map { entities ->
            entities.map { it.toDomain(imageStorage) }
        }
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
