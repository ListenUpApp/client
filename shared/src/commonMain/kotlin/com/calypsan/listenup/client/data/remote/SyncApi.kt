@file:Suppress("StringLiteralDuplication")

package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.getOrThrow
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.remote.model.ApiResponse
import com.calypsan.listenup.client.data.remote.model.BookResponse
import com.calypsan.listenup.client.data.remote.model.ContinueListeningItemResponse
import com.calypsan.listenup.client.data.remote.model.PlaybackProgressResponse
import com.calypsan.listenup.client.data.remote.model.SyncBooksResponse
import com.calypsan.listenup.client.data.remote.model.SyncContributorsResponse
import com.calypsan.listenup.client.data.remote.model.SyncManifestResponse
import com.calypsan.listenup.client.data.remote.model.SyncSeriesResponse
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * API client for sync endpoints.
 *
 * Handles communication with server sync infrastructure:
 * - Manifest fetching (library overview)
 * - Paginated book syncing
 * - SSE connection (in SSEManager, separate from HTTP calls)
 *
 * Uses ApiClientFactory to obtain authenticated HttpClient at call time,
 * avoiding runBlocking during dependency injection initialization.
 *
 * Implements [SyncApiContract] for testability - tests can mock the interface
 * without needing to mock HTTP client internals.
 *
 * @property clientFactory Factory for creating authenticated HttpClient
 */
class SyncApi(
    private val clientFactory: ApiClientFactory,
) : SyncApiContract {
    /**
     * Fetch sync manifest with library overview.
     *
     * Provides checkpoint timestamp and book IDs for determining
     * which books need to be fetched during sync.
     *
     * Endpoint: GET /api/v1/sync/manifest
     * Auth: Not required (public access)
     *
     * @return Result containing SyncManifestResponse or error
     */
    override suspend fun getManifest(): Result<SyncManifestResponse> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            val response: ApiResponse<SyncManifestResponse> =
                client.get("/api/v1/sync/manifest").body()
            response.toResult().getOrThrow()
        }

    /**
     * Fetch paginated books for syncing.
     *
     * Returns books with optional cursor-based pagination.
     * Use nextCursor from response to fetch subsequent pages.
     *
     * Endpoint: GET /api/v1/sync/books
     * Auth: Optional (filters by user access if authenticated)
     *
     * @param limit Number of books per page (default 100, max 1000)
     * @param cursor Base64-encoded pagination cursor (null for first page)
     * @param updatedAfter ISO 8601 timestamp to filter books updated after this time (for delta sync)
     * @return Result containing SyncBooksResponse or error
     */
    override suspend fun getBooks(
        limit: Int,
        cursor: String?,
        updatedAfter: String?,
    ): Result<SyncBooksResponse> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            val response: ApiResponse<SyncBooksResponse> =
                client
                    .get("/api/v1/sync/books") {
                        parameter("limit", limit)
                        cursor?.let { parameter("cursor", it) }
                        updatedAfter?.let { parameter("updated_after", it) }
                    }.body()
            response.toResult().getOrThrow()
        }

    /**
     * Fetch all books/changes across all pages.
     *
     * Automatically handles pagination by following nextCursor until
     * all pages have been fetched. Combines books and deleted IDs from all pages.
     *
     * @param limit Number of books per page
     * @param updatedAfter ISO 8601 timestamp for delta sync (optional)
     * @return Result containing combined SyncBooksResponse with all changes
     */
    override suspend fun getAllBooks(
        limit: Int,
        updatedAfter: String?,
    ): Result<SyncBooksResponse> =
        suspendRunCatching {
            val allBooks = mutableListOf<BookResponse>()
            val allDeletedIds = mutableListOf<String>()
            var cursor: String? = null

            do {
                when (val result = getBooks(limit, cursor, updatedAfter)) {
                    is Result.Success -> {
                        allBooks.addAll(result.data.books)
                        allDeletedIds.addAll(result.data.deletedBookIds)
                        cursor = result.data.nextCursor
                    }

                    is Result.Failure -> {
                        throw result.exception
                    }
                }
            } while (cursor != null)

            SyncBooksResponse(
                books = allBooks,
                deletedBookIds = allDeletedIds,
                hasMore = false,
            )
        }

    /**
     * Fetch paginated series for syncing.
     */
    override suspend fun getSeries(
        limit: Int,
        cursor: String?,
        updatedAfter: String?,
    ): Result<SyncSeriesResponse> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            val response: ApiResponse<SyncSeriesResponse> =
                client
                    .get("/api/v1/sync/series") {
                        parameter("limit", limit)
                        cursor?.let { parameter("cursor", it) }
                        updatedAfter?.let { parameter("updated_after", it) }
                    }.body()
            response.toResult().getOrThrow()
        }

    override suspend fun getAllSeries(
        limit: Int,
        updatedAfter: String?,
    ): Result<List<com.calypsan.listenup.client.data.remote.model.SeriesResponse>> =
        suspendRunCatching {
            val allItems = mutableListOf<com.calypsan.listenup.client.data.remote.model.SeriesResponse>()
            var cursor: String? = null

            do {
                when (val result = getSeries(limit, cursor, updatedAfter)) {
                    is Result.Success -> {
                        allItems.addAll(result.data.series)
                        cursor = result.data.nextCursor
                    }

                    is Result.Failure -> {
                        throw result.exception
                    }
                }
            } while (cursor != null)

            allItems
        }

    /**
     * Fetch paginated contributors for syncing.
     */
    override suspend fun getContributors(
        limit: Int,
        cursor: String?,
        updatedAfter: String?,
    ): Result<SyncContributorsResponse> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            val response: ApiResponse<SyncContributorsResponse> =
                client
                    .get("/api/v1/sync/contributors") {
                        parameter("limit", limit)
                        cursor?.let { parameter("cursor", it) }
                        updatedAfter?.let { parameter("updated_after", it) }
                    }.body()
            response.toResult().getOrThrow()
        }

    override suspend fun getAllContributors(
        limit: Int,
        updatedAfter: String?,
    ): Result<List<com.calypsan.listenup.client.data.remote.model.ContributorResponse>> =
        suspendRunCatching {
            val allItems = mutableListOf<com.calypsan.listenup.client.data.remote.model.ContributorResponse>()
            var cursor: String? = null

            do {
                when (val result = getContributors(limit, cursor, updatedAfter)) {
                    is Result.Success -> {
                        allItems.addAll(result.data.contributors)
                        cursor = result.data.nextCursor
                    }

                    is Result.Failure -> {
                        throw result.exception
                    }
                }
            } while (cursor != null)

            allItems
        }

    /**
     * Submit listening events to the server.
     *
     * Events are batched and sent together. Server acknowledges each
     * successfully processed event ID in the response.
     *
     * Endpoint: POST /api/v1/listening/events
     * Auth: Required
     *
     * @param events List of listening events to submit
     * @return Result containing acknowledged event IDs
     */
    override suspend fun submitListeningEvents(events: List<ListeningEventRequest>): Result<ListeningEventsResponse> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            val response: ApiResponse<ListeningEventsResponse> =
                client
                    .post("/api/v1/listening/events") {
                        contentType(ContentType.Application.Json)
                        setBody(ListeningEventsRequest(events = events))
                    }.body()
            response.toResult().getOrThrow()
        }

    /**
     * Get playback progress for a specific book.
     *
     * Used for cross-device sync: checks if another device has newer progress.
     * Returns null if no progress exists (404 response).
     *
     * Endpoint: GET /api/v1/listening/progress/{bookId}
     * Auth: Required
     *
     * @param bookId Book to get progress for
     * @return Result containing PlaybackProgressResponse or null if not found
     */
    override suspend fun getProgress(bookId: String): Result<PlaybackProgressResponse?> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            val httpResponse: HttpResponse = client.get("/api/v1/listening/progress/$bookId")

            // Handle 404 as null (no progress yet)
            if (httpResponse.status == HttpStatusCode.NotFound) {
                return@suspendRunCatching null
            }

            val response: ApiResponse<PlaybackProgressResponse> = httpResponse.body()
            response.toResult().getOrThrow()
        }

    /**
     * Get list of books with playback progress (Continue Listening).
     *
     * Returns display-ready items with embedded book details,
     * eliminating the need for client-side joins.
     *
     * Endpoint: GET /api/v1/listening/continue
     * Auth: Required
     *
     * @param limit Maximum number of books to return
     * @return Result containing list of ContinueListeningItemResponse
     */
    override suspend fun getContinueListening(limit: Int): Result<List<ContinueListeningItemResponse>> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            val response: ApiResponse<List<ContinueListeningItemResponse>> =
                client
                    .get("/api/v1/listening/continue") {
                        parameter("limit", limit)
                    }.body()
            response.toResult().getOrThrow()
        }
}

/**
 * Request body for submitting listening events.
 */
@Serializable
data class ListeningEventsRequest(
    val events: List<ListeningEventRequest>,
)

/**
 * Single listening event to submit.
 */
@Serializable
data class ListeningEventRequest(
    val id: String,
    val book_id: String,
    val start_position_ms: Long,
    val end_position_ms: Long,
    val started_at: Long,
    val ended_at: Long,
    val playback_speed: Float,
    val device_id: String,
)

/**
 * Response from listening events submission.
 */
@Serializable
data class ListeningEventsResponse(
    val acknowledged: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
)
