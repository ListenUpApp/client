package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.local.db.BookId
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * API client for downloading book cover images.
 *
 * Handles communication with the cover image endpoint:
 * - Downloads JPEG cover images from server
 * - No authentication required (public endpoint)
 * - Returns raw bytes for local storage
 *
 * Uses ApiClientFactory to obtain authenticated HttpClient at call time,
 * avoiding runBlocking during dependency injection initialization.
 *
 * @property clientFactory Factory for creating authenticated HttpClient
 */
class ImageApi(
    private val clientFactory: ApiClientFactory,
) {
    /**
     * Download cover image for a book.
     *
     * Returns raw JPEG bytes which can be saved to local storage
     * via ImageStorage. Returns failure if cover doesn't exist (404).
     *
     * Endpoint: GET /api/v1/covers/{bookId}
     * Auth: Not required (public access)
     * Response: image/jpeg (raw bytes)
     *
     * @param bookId Unique identifier for the book
     * @return Result containing image bytes or error
     */
    suspend fun downloadCover(bookId: BookId): Result<ByteArray> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            client.get("/api/v1/covers/${bookId.value}").body<ByteArray>()
        }
}
