package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.ChapterDao
import com.calypsan.listenup.client.data.local.db.ChapterEntity
import com.calypsan.listenup.client.data.local.db.ChapterId
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.sync.SyncManager
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Chapter
import com.calypsan.listenup.client.domain.model.Contributor
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
 * @property chapterDao Room DAO for chapter operations
 * @property syncManager Sync orchestrator for server communication
 * @property imageStorage Storage for resolving cover image paths
 */
class BookRepository(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val bookContributorDao: com.calypsan.listenup.client.data.local.db.BookContributorDao,
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
            entities.map { entity ->
                // TODO: Optimize this N+1 query with a proper Room Relation POJO
                val authors = bookContributorDao.getContributorsForBookByRole(entity.id, "author")
                    .map { Contributor(it.id, it.name) }
                val narrators = bookContributorDao.getContributorsForBookByRole(entity.id, "narrator")
                    .map { Contributor(it.id, it.name) }
                    
                entity.toDomain(imageStorage, authors, narrators) 
            }
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

    /**
     * Get a single book by ID.
     *
     * @param id The book ID
     * @return Domain Book model or null if not found
     */
    suspend fun getBook(id: String): Book? {
        val bookId = BookId(id)
        val bookEntity = bookDao.getById(bookId) ?: return null
        
        val authors = bookContributorDao.getContributorsForBookByRole(bookId, "author")
            .map { Contributor(it.id, it.name) }
            
        val narrators = bookContributorDao.getContributorsForBookByRole(bookId, "narrator")
            .map { Contributor(it.id, it.name) }

        return bookEntity.toDomain(imageStorage, authors, narrators)
    }

    /**
     * Get chapters for a book.
     *
     * Currently, the backend does not sync chapters.
     * To simulate "real" data, if the database is empty for this book,
     * we generate mock chapters and persist them to the local database.
     * This ensures the UI is always consuming from the Single Source of Truth (DAO).
     *
     * @param bookId The book ID
     * @return List of chapters
     */
    suspend fun getChapters(bookId: String): List<Chapter> {
        val id = BookId(bookId)
        val localChapters = chapterDao.getChaptersForBook(id)

        if (localChapters.isNotEmpty()) {
            return localChapters.map { it.toDomain() }
        }

        // Temporary: Seed mock data into DB if empty
        // TODO: Remove this once backend syncs chapters
        val mockChapters = List(15) { index ->
            ChapterEntity(
                id = ChapterId("ch-${bookId}-$index"),
                bookId = id,
                title = "Chapter ${index + 1}",
                duration = 1_800_000L + (index * 60_000L), // ~30 mins varying
                startTime = index * 1_800_000L,
                syncState = SyncState.SYNCED,
                lastModified = Timestamp.now(),
                serverVersion = Timestamp.now()
            )
        }
        chapterDao.upsertAll(mockChapters)

        return mockChapters.map { it.toDomain() }
    }
    
    private fun ChapterEntity.toDomain(): Chapter {
        return Chapter(
            id = id.value,
            title = title,
            duration = duration,
            startTime = startTime
        )
    }

    private fun BookEntity.toDomain(
        imageStorage: ImageStorage,
        authors: List<Contributor>,
        narrators: List<Contributor>
    ): Book {
        return Book(
            id = this.id,
            title = this.title,
            authors = authors,
            narrators = narrators,
            duration = this.totalDuration,
            coverPath = this.coverUrl?.let { imageStorage.getCoverPath(this.id) },
            addedAt = this.createdAt,
            updatedAt = this.updatedAt,
            description = this.description,
            genres = this.genres,
            seriesName = this.seriesName,
            seriesSequence = this.sequence,
            publishYear = this.publishYear,
            rating = null // Rating is not directly stored in BookEntity yet, default to null
        )
    }
}
