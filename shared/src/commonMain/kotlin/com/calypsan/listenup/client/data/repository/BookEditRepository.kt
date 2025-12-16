@file:OptIn(ExperimentalTime::class)

package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.remote.BookEditResponse
import com.calypsan.listenup.client.data.remote.BookUpdateRequest
import com.calypsan.listenup.client.data.remote.ContributorInput
import com.calypsan.listenup.client.data.remote.ListenUpApiContract
import com.calypsan.listenup.client.data.remote.SeriesInput
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Contract for book editing operations.
 *
 * Provides methods for modifying book metadata and contributors.
 * Changes are sent to the server and then applied to the local database.
 */
interface BookEditRepositoryContract {
    /**
     * Update book metadata.
     *
     * Sends update to server, then updates local database on success.
     * Only non-null fields in the request are updated (PATCH semantics).
     *
     * @param bookId ID of the book to update
     * @param update Fields to update
     * @return Result containing the updated book response
     */
    suspend fun updateBook(
        bookId: String,
        update: BookUpdateRequest,
    ): Result<BookEditResponse>

    /**
     * Set book contributors (replaces all existing contributors).
     *
     * Sends update to server, then triggers sync to update local database.
     * Contributors are matched by name - existing contributors are linked,
     * new names create new contributors.
     *
     * @param bookId ID of the book to update
     * @param contributors New list of contributors with roles
     * @return Result containing the updated book response
     */
    suspend fun setBookContributors(
        bookId: String,
        contributors: List<ContributorInput>,
    ): Result<BookEditResponse>

    /**
     * Set book series (replaces all existing series relationships).
     *
     * Sends update to server, then triggers sync to update local database.
     * Series are matched by name - existing series are linked,
     * new names create new series.
     *
     * @param bookId ID of the book to update
     * @param series New list of series with sequence numbers
     * @return Result containing the updated book response
     */
    suspend fun setBookSeries(
        bookId: String,
        series: List<SeriesInput>,
    ): Result<BookEditResponse>
}

/**
 * Repository for book editing operations.
 *
 * Handles the edit flow:
 * 1. Send changes to server via API
 * 2. On success, update local database to reflect changes
 * 3. Return result to caller
 *
 * Local database is updated immediately after successful server response,
 * ensuring the UI reflects changes without waiting for next sync.
 *
 * @property api ListenUp API client
 * @property bookDao Room DAO for book operations
 */
class BookEditRepository(
    private val api: ListenUpApiContract,
    private val bookDao: BookDao,
) : BookEditRepositoryContract {
    /**
     * Update book metadata.
     *
     * Flow:
     * 1. Send PATCH request to server
     * 2. On success, update local BookEntity with new values
     * 3. Return the server response
     */
    override suspend fun updateBook(
        bookId: String,
        update: BookUpdateRequest,
    ): Result<BookEditResponse> =
        withContext(Dispatchers.IO) {
            logger.debug { "Updating book: $bookId" }

            // Send update to server
            when (val result = api.updateBook(bookId, update)) {
                is Success -> {
                    // Update local database
                    updateLocalBook(bookId, result.data)
                    logger.info { "Book updated successfully: $bookId" }
                    result
                }

                is Failure -> {
                    logger.error(result.exception) { "Failed to update book: $bookId" }
                    result
                }
            }
        }

    /**
     * Set book contributors.
     *
     * Flow:
     * 1. Send PUT request to server
     * 2. On success, the server response contains updated book
     * 3. Update local book entity with new timestamp
     *
     * Note: Contributors are synced separately - the book's updatedAt
     * timestamp change will trigger a full sync that brings in contributor changes.
     */
    override suspend fun setBookContributors(
        bookId: String,
        contributors: List<ContributorInput>,
    ): Result<BookEditResponse> =
        withContext(Dispatchers.IO) {
            logger.debug { "Setting contributors for book: $bookId, count: ${contributors.size}" }

            // Send update to server
            when (val result = api.setBookContributors(bookId, contributors)) {
                is Success -> {
                    // Update local database timestamp
                    // Full contributor sync will happen on next sync cycle
                    updateLocalBook(bookId, result.data)
                    logger.info { "Contributors updated for book: $bookId" }
                    result
                }

                is Failure -> {
                    logger.error(result.exception) { "Failed to set contributors for book: $bookId" }
                    result
                }
            }
        }

    /**
     * Set book series.
     *
     * Flow:
     * 1. Send PUT request to server
     * 2. On success, the server response contains updated book
     * 3. Update local book entity with new timestamp
     *
     * Note: Series relationships are synced separately - the book's updatedAt
     * timestamp change will trigger a full sync that brings in series changes.
     */
    override suspend fun setBookSeries(
        bookId: String,
        series: List<SeriesInput>,
    ): Result<BookEditResponse> =
        withContext(Dispatchers.IO) {
            logger.debug { "Setting series for book: $bookId, count: ${series.size}" }

            // Send update to server
            when (val result = api.setBookSeries(bookId, series)) {
                is Success -> {
                    // Update local database timestamp
                    // Full series sync will happen on next sync cycle
                    updateLocalBook(bookId, result.data)
                    logger.info { "Series updated for book: $bookId" }
                    result
                }

                is Failure -> {
                    logger.error(result.exception) { "Failed to set series for book: $bookId" }
                    result
                }
            }
        }

    /**
     * Update local BookEntity with server response data.
     *
     * Only updates fields that are present in both BookEntity and BookEditResponse.
     * Some fields (isbn, asin, abridged) are not currently stored in
     * BookEntity and will be ignored.
     */
    private suspend fun updateLocalBook(
        bookId: String,
        response: BookEditResponse,
    ) {
        val id = BookId(bookId)
        val existing =
            bookDao.getById(id) ?: run {
                logger.warn { "Book not found in local database: $bookId" }
                return
            }

        // Parse the ISO 8601 timestamp from server
        val serverUpdatedAt =
            try {
                Timestamp.fromEpochMillis(Instant.parse(response.updatedAt).toEpochMilliseconds())
            } catch (e: Exception) {
                logger.warn { "Failed to parse timestamp: ${response.updatedAt}" }
                Timestamp.now()
            }

        // Update fields that BookEntity supports
        // Note: Series is now managed via book_series junction table and separate API endpoint
        val updated =
            existing.copy(
                title = response.title,
                subtitle = response.subtitle,
                description = response.description,
                publishYear = response.publishYear?.toIntOrNull(),
                publisher = response.publisher,
                language = response.language,
                isbn = response.isbn,
                asin = response.asin,
                abridged = response.abridged,
                // Update sync metadata
                updatedAt = serverUpdatedAt,
                lastModified = Timestamp.now(),
                syncState = SyncState.SYNCED, // We just synced with server
            )

        bookDao.upsert(updated)
        logger.debug { "Local book updated: $bookId" }
    }
}
