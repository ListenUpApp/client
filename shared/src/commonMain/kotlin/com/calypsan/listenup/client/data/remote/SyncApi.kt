package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.getOrThrow
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.remote.model.ApiResponse
import com.calypsan.listenup.client.data.remote.model.BookResponse
import com.calypsan.listenup.client.data.remote.model.SyncBooksResponse
import com.calypsan.listenup.client.data.remote.model.SyncManifestResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

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
 * @property clientFactory Factory for creating authenticated HttpClient
 */
class SyncApi(
    private val clientFactory: ApiClientFactory
) {

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
    suspend fun getManifest(): Result<SyncManifestResponse> {
        return suspendRunCatching {
            val client = clientFactory.getClient()
            val response: ApiResponse<SyncManifestResponse> =
                client.get("/api/v1/sync/manifest").body()
            response.toResult().getOrThrow()
        }
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
     * @return Result containing SyncBooksResponse or error
     */
    suspend fun getBooks(
        limit: Int = 100,
        cursor: String? = null
    ): Result<SyncBooksResponse> {
        return suspendRunCatching {
            val client = clientFactory.getClient()
            val response: ApiResponse<SyncBooksResponse> =
                client.get("/api/v1/sync/books") {
                    parameter("limit", limit)
                    cursor?.let { parameter("cursor", it) }
                }.body()
            response.toResult().getOrThrow()
        }
    }

    /**
     * Fetch all books across all pages.
     *
     * Automatically handles pagination by following nextCursor until
     * all books have been fetched. Use with caution on large libraries.
     *
     * @param limit Number of books per page
     * @return Result containing list of all BookResponse objects
     */
    suspend fun getAllBooks(limit: Int = 100): Result<List<BookResponse>> {
        return suspendRunCatching {
            val allBooks = mutableListOf<BookResponse>()
            var cursor: String? = null

            do {
                when (val result = getBooks(limit, cursor)) {
                    is Result.Success -> {
                        allBooks.addAll(result.data.books)
                        cursor = result.data.nextCursor
                    }
                    is Result.Failure -> throw result.exception
                }
            } while (cursor != null)

            allBooks
        }
    }
}
