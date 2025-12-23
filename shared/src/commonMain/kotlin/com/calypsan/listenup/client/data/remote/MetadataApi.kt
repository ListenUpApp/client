package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.data.remote.model.ApiResponse
import com.calypsan.listenup.client.data.remote.model.ApplyMatchRequest
import com.calypsan.listenup.client.data.remote.model.MetadataBook
import com.calypsan.listenup.client.data.remote.model.MetadataBookResponse
import com.calypsan.listenup.client.data.remote.model.MetadataSearchResponse
import com.calypsan.listenup.client.data.remote.model.MetadataSearchResult
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Contract interface for metadata API operations.
 *
 * Provides Audible metadata search and book matching functionality.
 * Extracted to enable mocking in tests.
 */
interface MetadataApiContract {
    /**
     * Search Audible for matching audiobooks.
     *
     * @param query Search query (title, author, etc.)
     * @param region Audible region code (default: "us")
     * @return List of matching results
     */
    suspend fun search(
        query: String,
        region: String = "us",
    ): List<MetadataSearchResult>

    /**
     * Get full metadata for a specific Audible book.
     *
     * @param asin Audible Standard Identification Number
     * @param region Audible region code (default: "us")
     * @return Full book metadata
     */
    suspend fun getBook(
        asin: String,
        region: String = "us",
    ): MetadataBook

    /**
     * Apply Audible metadata match to a book.
     *
     * Downloads cover art and updates book metadata from the matched
     * Audible entry.
     *
     * @param bookId Local book ID to update
     * @param request Match request with field selections
     */
    suspend fun applyMatch(
        bookId: String,
        request: ApplyMatchRequest,
    )
}

/**
 * API client for Audible metadata operations.
 *
 * Provides search, preview, and match application for enriching
 * local audiobook metadata with Audible information.
 *
 * @property clientFactory Factory for creating authenticated HttpClient
 */
class MetadataApi(
    private val clientFactory: ApiClientFactory,
) : MetadataApiContract {
    /**
     * Search Audible for matching audiobooks.
     *
     * Endpoint: GET /api/v1/metadata/search
     */
    override suspend fun search(
        query: String,
        region: String,
    ): List<MetadataSearchResult> {
        val client = clientFactory.getClient()
        val response: ApiResponse<MetadataSearchResponse> =
            client
                .get("/api/v1/metadata/search") {
                    parameter("q", query)
                    parameter("region", region)
                }.body()

        if (!response.success) {
            throw Exception(response.error ?: "Search failed")
        }
        return response.data?.matches ?: emptyList()
    }

    /**
     * Get full metadata for a specific Audible book.
     *
     * Endpoint: GET /api/v1/metadata/book/{asin}
     */
    override suspend fun getBook(
        asin: String,
        region: String,
    ): MetadataBook {
        val client = clientFactory.getClient()
        val response: ApiResponse<MetadataBookResponse> =
            client
                .get("/api/v1/metadata/book/$asin") {
                    parameter("region", region)
                }.body()

        if (!response.success) {
            throw Exception(response.error ?: "Failed to get book metadata")
        }
        return response.data?.book ?: throw Exception("No book data in response")
    }

    /**
     * Apply Audible metadata match to a book.
     *
     * Endpoint: POST /api/v1/books/{bookId}/match
     *
     * This endpoint:
     * 1. Fetches full metadata from Audible
     * 2. Downloads and processes cover art
     * 3. Updates book metadata (title, subtitle, narrators, series, etc.)
     * 4. Returns the updated book
     */
    override suspend fun applyMatch(
        bookId: String,
        request: ApplyMatchRequest,
    ) {
        val client = clientFactory.getClient()
        client.post("/api/v1/books/$bookId/match") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
