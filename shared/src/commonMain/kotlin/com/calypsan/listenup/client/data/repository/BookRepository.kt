package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookWithContributors
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
 * Uses Room Relations to efficiently batch-load books with their contributors,
 * avoiding N+1 query problems when loading book lists.
 *
 * @property bookDao Room DAO for book operations
 * @property chapterDao Room DAO for chapter operations
 * @property syncManager Sync orchestrator for server communication
 * @property imageStorage Storage for resolving cover image paths
 */
class BookRepository(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
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
     * Uses Room Relations to efficiently batch-load books with their
     * contributors, avoiding N+1 query problems.
     *
     * Transforms BookEntity to domain Book model with local cover paths.
     *
     * Offline-first: Returns local data immediately, even if
     * not yet synced with server.
     *
     * @return Flow emitting list of domain Book models
     */
    fun observeBooks(): Flow<List<Book>> {
        return bookDao.observeAllWithContributors().map { booksWithContributors ->
            booksWithContributors.map { bookWithContributors ->
                bookWithContributors.toDomain(imageStorage)
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
     * Uses Room Relations to efficiently load the book with its contributors
     * in a single batched query.
     *
     * @param id The book ID
     * @return Domain Book model or null if not found
     */
    suspend fun getBook(id: String): Book? {
        val bookId = BookId(id)
        val bookWithContributors = bookDao.getByIdWithContributors(bookId) ?: return null
        return bookWithContributors.toDomain(imageStorage)
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

    /**
     * Convert BookWithContributors to domain Book model.
     *
     * Filters contributors by role using the cross-reference data,
     * avoiding additional database queries.
     *
     * Handles contributors with multiple roles (e.g., same person as author AND narrator)
     * by filtering the cross-ref list by role, then looking up the contributor entity.
     */
    private fun BookWithContributors.toDomain(imageStorage: ImageStorage): Book {
        // Create a lookup map for contributor entities
        val contributorsById = contributors.associateBy { it.id }

        // Get authors: find all cross-refs with role "author", then look up the contributor
        val authors = contributorRoles
            .filter { it.role == "author" }
            .mapNotNull { crossRef -> contributorsById[crossRef.contributorId] }
            .distinctBy { it.id }
            .map { Contributor(it.id, it.name) }

        // Get narrators: find all cross-refs with role "narrator", then look up the contributor
        val narrators = contributorRoles
            .filter { it.role == "narrator" }
            .mapNotNull { crossRef -> contributorsById[crossRef.contributorId] }
            .distinctBy { it.id }
            .map { Contributor(it.id, it.name) }

        return book.toDomain(imageStorage, authors, narrators)
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
            coverPath = this.coverUrl?.let {
                if (imageStorage.exists(this.id)) imageStorage.getCoverPath(this.id) else null
            },
            addedAt = this.createdAt,
            updatedAt = this.updatedAt,
            description = this.description,
            genres = this.genres,
            seriesId = this.seriesId,
            seriesName = this.seriesName,
            seriesSequence = this.sequence,
            publishYear = this.publishYear,
            rating = null // Rating is not directly stored in BookEntity yet, default to null
        )
    }
}
