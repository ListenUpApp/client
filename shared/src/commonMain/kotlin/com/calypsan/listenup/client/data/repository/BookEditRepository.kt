@file:Suppress("StringLiteralDuplication")

package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.EntityType
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.remote.BookUpdateRequest
import com.calypsan.listenup.client.data.remote.ContributorInput
import com.calypsan.listenup.client.data.remote.SeriesInput
import com.calypsan.listenup.client.data.sync.push.BookUpdateHandler
import com.calypsan.listenup.client.data.sync.push.BookUpdatePayload
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.data.sync.push.SetBookContributorsHandler
import com.calypsan.listenup.client.data.sync.push.SetBookContributorsPayload
import com.calypsan.listenup.client.data.sync.push.SetBookSeriesHandler
import com.calypsan.listenup.client.data.sync.push.SetBookSeriesPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import com.calypsan.listenup.client.data.sync.push.ContributorInput as PayloadContributorInput
import com.calypsan.listenup.client.data.sync.push.SeriesInput as PayloadSeriesInput

private val logger = KotlinLogging.logger {}

/**
 * Contract for book editing operations.
 *
 * Provides methods for modifying book metadata and contributors.
 * Uses offline-first pattern: changes are applied locally immediately
 * and queued for sync to server.
 */
interface BookEditRepositoryContract {
    /**
     * Update book metadata.
     *
     * Applies update locally and queues for server sync.
     * Only non-null fields in the request are updated (PATCH semantics).
     *
     * @param bookId ID of the book to update
     * @param update Fields to update
     * @return Result indicating success or failure
     */
    suspend fun updateBook(
        bookId: String,
        update: BookUpdateRequest,
    ): Result<Unit>

    /**
     * Set book contributors (replaces all existing contributors).
     *
     * Queues operation for server sync. On sync, contributors are matched
     * by name - existing contributors are linked, new names create new contributors.
     *
     * @param bookId ID of the book to update
     * @param contributors New list of contributors with roles
     * @return Result indicating success or failure
     */
    suspend fun setBookContributors(
        bookId: String,
        contributors: List<ContributorInput>,
    ): Result<Unit>

    /**
     * Set book series (replaces all existing series relationships).
     *
     * Queues operation for server sync. On sync, series are matched
     * by name - existing series are linked, new names create new series.
     *
     * @param bookId ID of the book to update
     * @param series New list of series with sequence numbers
     * @return Result indicating success or failure
     */
    suspend fun setBookSeries(
        bookId: String,
        series: List<SeriesInput>,
    ): Result<Unit>
}

/**
 * Repository for book editing operations using offline-first pattern.
 *
 * Handles the edit flow:
 * 1. Apply optimistic update to local database (syncState = PENDING)
 * 2. Queue operation for server sync via PendingOperationRepository
 * 3. Return success immediately
 *
 * The PushSyncOrchestrator will later:
 * - Send changes to server
 * - Handle conflicts if server version is newer
 * - Mark entity as SYNCED on success
 *
 * @property bookDao Room DAO for book operations
 * @property pendingOperationRepository Repository for queuing sync operations
 * @property bookUpdateHandler Handler for book update operations
 * @property setBookContributorsHandler Handler for set contributors operations
 * @property setBookSeriesHandler Handler for set series operations
 */
class BookEditRepository(
    private val bookDao: BookDao,
    private val pendingOperationRepository: PendingOperationRepositoryContract,
    private val bookUpdateHandler: BookUpdateHandler,
    private val setBookContributorsHandler: SetBookContributorsHandler,
    private val setBookSeriesHandler: SetBookSeriesHandler,
) : BookEditRepositoryContract {
    /**
     * Update book metadata.
     *
     * Flow:
     * 1. Get existing book from local database
     * 2. Apply optimistic update with syncState = PENDING
     * 3. Queue operation (coalesces with any pending update)
     * 4. Return success immediately
     */
    override suspend fun updateBook(
        bookId: String,
        update: BookUpdateRequest,
    ): Result<Unit> =
        withContext(IODispatcher) {
            logger.debug { "Updating book (offline-first): $bookId" }

            // Get existing book
            val existing = bookDao.getById(BookId(bookId))
            if (existing == null) {
                logger.error { "Book not found: $bookId" }
                return@withContext Failure(Exception("Book not found: $bookId"))
            }

            // Apply optimistic update
            val updated =
                existing.copy(
                    title = update.title ?: existing.title,
                    subtitle = update.subtitle ?: existing.subtitle,
                    description = update.description ?: existing.description,
                    publisher = update.publisher ?: existing.publisher,
                    publishYear = update.publishYear?.toIntOrNull() ?: existing.publishYear,
                    language = update.language ?: existing.language,
                    isbn = update.isbn ?: existing.isbn,
                    asin = update.asin ?: existing.asin,
                    abridged = update.abridged ?: existing.abridged,
                    createdAt =
                        update.createdAt?.let {
                            Timestamp.fromEpochMillis(
                                kotlinx.datetime.Instant
                                    .parse(it)
                                    .toEpochMilliseconds(),
                            )
                        } ?: existing.createdAt,
                    syncState = SyncState.NOT_SYNCED,
                    lastModified = Timestamp.now(),
                )
            bookDao.upsert(updated)

            // Queue operation (coalesces if pending update exists for this book)
            val payload =
                BookUpdatePayload(
                    title = update.title,
                    subtitle = update.subtitle,
                    description = update.description,
                    publisher = update.publisher,
                    publishYear = update.publishYear,
                    language = update.language,
                    isbn = update.isbn,
                    asin = update.asin,
                    abridged = update.abridged,
                    createdAt = update.createdAt,
                )
            pendingOperationRepository.queue(
                type = OperationType.BOOK_UPDATE,
                entityType = EntityType.BOOK,
                entityId = bookId,
                payload = payload,
                handler = bookUpdateHandler,
            )

            logger.info { "Book update queued: $bookId" }
            Success(Unit)
        }

    /**
     * Set book contributors.
     *
     * Flow:
     * 1. Mark book as pending sync
     * 2. Queue operation with full contributor list
     * 3. Return success immediately
     *
     * Note: Local book-contributor relationships are not updated here.
     * The pull sync after successful push will bring in the correct relationships.
     */
    override suspend fun setBookContributors(
        bookId: String,
        contributors: List<ContributorInput>,
    ): Result<Unit> =
        withContext(IODispatcher) {
            logger.debug { "Setting contributors for book (offline-first): $bookId, count: ${contributors.size}" }

            // Mark book as pending sync
            val existing = bookDao.getById(BookId(bookId))
            if (existing == null) {
                logger.error { "Book not found: $bookId" }
                return@withContext Failure(Exception("Book not found: $bookId"))
            }

            val updated =
                existing.copy(
                    syncState = SyncState.NOT_SYNCED,
                    lastModified = Timestamp.now(),
                )
            bookDao.upsert(updated)

            // Queue operation
            val payload =
                SetBookContributorsPayload(
                    contributors =
                        contributors.map { c ->
                            PayloadContributorInput(
                                name = c.name,
                                roles = c.roles,
                            )
                        },
                )
            pendingOperationRepository.queue(
                type = OperationType.SET_BOOK_CONTRIBUTORS,
                entityType = EntityType.BOOK,
                entityId = bookId,
                payload = payload,
                handler = setBookContributorsHandler,
            )

            logger.info { "Set book contributors queued: $bookId, ${contributors.size} contributor(s)" }
            Success(Unit)
        }

    /**
     * Set book series.
     *
     * Flow:
     * 1. Mark book as pending sync
     * 2. Queue operation with full series list
     * 3. Return success immediately
     *
     * Note: Local book-series relationships are not updated here.
     * The pull sync after successful push will bring in the correct relationships.
     */
    override suspend fun setBookSeries(
        bookId: String,
        series: List<SeriesInput>,
    ): Result<Unit> =
        withContext(IODispatcher) {
            logger.debug { "Setting series for book (offline-first): $bookId, count: ${series.size}" }

            // Mark book as pending sync
            val existing = bookDao.getById(BookId(bookId))
            if (existing == null) {
                logger.error { "Book not found: $bookId" }
                return@withContext Failure(Exception("Book not found: $bookId"))
            }

            val updated =
                existing.copy(
                    syncState = SyncState.NOT_SYNCED,
                    lastModified = Timestamp.now(),
                )
            bookDao.upsert(updated)

            // Queue operation
            val payload =
                SetBookSeriesPayload(
                    series =
                        series.map { s ->
                            PayloadSeriesInput(
                                name = s.name,
                                sequence = s.sequence,
                            )
                        },
                )
            pendingOperationRepository.queue(
                type = OperationType.SET_BOOK_SERIES,
                entityType = EntityType.BOOK,
                entityId = bookId,
                payload = payload,
                handler = setBookSeriesHandler,
            )

            logger.info { "Set book series queued: $bookId, ${series.size} series" }
            Success(Unit)
        }
}
