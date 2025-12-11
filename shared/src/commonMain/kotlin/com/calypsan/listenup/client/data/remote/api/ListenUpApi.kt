package com.calypsan.listenup.client.data.remote.api

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.remote.ApiClientFactory
import com.calypsan.listenup.client.data.remote.BookEditResponse
import com.calypsan.listenup.client.data.remote.BookUpdateRequest
import com.calypsan.listenup.client.data.remote.ContributorInput
import com.calypsan.listenup.client.data.remote.ContributorSearchResult
import com.calypsan.listenup.client.data.remote.ListenUpApiContract
import com.calypsan.listenup.client.data.remote.model.ApiResponse
import com.calypsan.listenup.client.data.remote.model.PlaybackProgressResponse
import com.calypsan.listenup.client.domain.model.Instance
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * HTTP client for the ListenUp audiobook server API.
 *
 * Handles both public and authenticated API endpoints:
 * - Public endpoints (like getInstance) use a simple unauthenticated client
 * - Authenticated endpoints use ApiClientFactory for automatic token refresh
 *
 * This separation ensures clean architecture - public endpoints don't carry
 * unnecessary auth overhead, while authenticated endpoints get automatic
 * token management.
 *
 * Uses Ktor 3 for modern, multiplatform HTTP client functionality.
 */
class ListenUpApi(
    private val baseUrl: String,
    private val apiClientFactory: ApiClientFactory? = null,
) : ListenUpApiContract {
    /**
     * Simple HTTP client for public endpoints (no authentication).
     * Used for endpoints like getInstance that don't require credentials.
     */
    private val publicClient =
        HttpClient {
            // JSON content negotiation for request/response serialization
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = false
                        isLenient = false
                        ignoreUnknownKeys = true
                    },
                )
            }

            // HTTP logging for debugging
            install(Logging) {
                logger =
                    object : Logger {
                        override fun log(message: String) {
                            com.calypsan.listenup.client.data.remote.api.logger
                                .debug { message }
                        }
                    }
                level = LogLevel.HEADERS
            }

            // Request timeout configuration
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }

            // Default request configuration
            defaultRequest {
                url(baseUrl)
            }
        }

    /**
     * Get authenticated HTTP client from factory.
     * Used for endpoints that require Bearer token authentication.
     *
     * @throws IllegalStateException if factory not provided
     */
    private suspend fun getAuthenticatedClient(): HttpClient =
        apiClientFactory?.getClient()
            ?: error("ApiClientFactory required for authenticated endpoints")

    /**
     * Fetch the server instance information.
     *
     * This is a public endpoint - no authentication required.
     *
     * @return Result containing the Instance on success, or an error on failure
     */
    override suspend fun getInstance(): Result<Instance> =
        suspendRunCatching {
            logger.debug { "Fetching instance information from $baseUrl/api/v1/instance" }

            val response: ApiResponse<Instance> = publicClient.get("/api/v1/instance").body()

            logger.debug { "Received response: success=${response.success}" }

            // Convert API response to Result and extract data
            when (val result = response.toResult()) {
                is Success -> result.data
                is Failure -> throw result.exception
            }
        }

    /**
     * Fetch books the user is currently listening to.
     *
     * This is an authenticated endpoint - requires valid access token.
     * Returns playback progress for books sorted by last played time.
     *
     * @param limit Maximum number of books to return (default 10)
     * @return Result containing list of PlaybackProgressResponse on success
     */
    override suspend fun getContinueListening(limit: Int): Result<List<PlaybackProgressResponse>> =
        suspendRunCatching {
            logger.debug { "Fetching continue listening from $baseUrl/api/v1/listening/continue" }

            val client = getAuthenticatedClient()
            val response: ApiResponse<List<PlaybackProgressResponse>> =
                client
                    .get("/api/v1/listening/continue") {
                        parameter("limit", limit)
                    }.body()

            logger.debug { "Received continue listening response: success=${response.success}" }

            when (val result = response.toResult()) {
                is Success -> result.data
                is Failure -> throw result.exception
            }
        }

    /**
     * Search contributors for autocomplete during book editing.
     *
     * Uses server-side Bleve full-text search for O(log n) performance:
     * - Prefix matching ("bran" → "Brandon Sanderson")
     * - Word matching ("sanderson" in "Brandon Sanderson")
     * - Fuzzy matching for typo tolerance (1 edit distance for 3+ char queries)
     *
     * Endpoint: GET /api/v1/contributors/search?q={query}&limit={limit}
     *
     * @param query Search query (min 2 characters recommended)
     * @param limit Maximum results to return (default 10, max 50)
     * @return Result containing list of matching contributors
     */
    override suspend fun searchContributors(
        query: String,
        limit: Int,
    ): Result<List<ContributorSearchResult>> =
        suspendRunCatching {
            logger.debug { "Searching contributors: query='$query', limit=$limit" }

            val client = getAuthenticatedClient()
            val response: ApiResponse<ContributorSearchResponse> =
                client
                    .get("/api/v1/contributors/search") {
                        parameter("q", query)
                        parameter("limit", limit.coerceIn(1, 50))
                    }.body()

            logger.debug { "Received contributor search response: success=${response.success}" }

            when (val result = response.toResult()) {
                is Success -> result.data.contributors.map { it.toDomain() }
                is Failure -> throw result.exception
            }
        }

    /**
     * Update book metadata (PATCH semantics).
     *
     * Only fields present in the request are updated.
     * Endpoint: PATCH /api/v1/books/{id}
     *
     * @param bookId Book to update
     * @param update Fields to update (null = don't change, empty = clear)
     * @return Result containing the updated book
     */
    override suspend fun updateBook(
        bookId: String,
        update: BookUpdateRequest,
    ): Result<BookEditResponse> =
        suspendRunCatching {
            logger.debug { "Updating book: id=$bookId" }

            val client = getAuthenticatedClient()
            val response: ApiResponse<BookEditApiResponse> =
                client
                    .patch("/api/v1/books/$bookId") {
                        contentType(ContentType.Application.Json)
                        setBody(update.toApiRequest())
                    }.body()

            logger.debug { "Received book update response: success=${response.success}" }

            when (val result = response.toResult()) {
                is Success -> result.data.toDomain()
                is Failure -> throw result.exception
            }
        }

    /**
     * Set book contributors (replaces all existing contributors).
     *
     * Endpoint: PUT /api/v1/books/{id}/contributors
     *
     * @param bookId Book to update
     * @param contributors New list of contributors with roles
     * @return Result containing the updated book
     */
    override suspend fun setBookContributors(
        bookId: String,
        contributors: List<ContributorInput>,
    ): Result<BookEditResponse> =
        suspendRunCatching {
            logger.debug { "Setting book contributors: id=$bookId, count=${contributors.size}" }

            val client = getAuthenticatedClient()
            val request = SetContributorsApiRequest(
                contributors = contributors.map { ContributorApiInput(it.name, it.roles) },
            )
            val response: ApiResponse<BookEditApiResponse> =
                client
                    .put("/api/v1/books/$bookId/contributors") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }.body()

            logger.debug { "Received set contributors response: success=${response.success}" }

            when (val result = response.toResult()) {
                is Success -> result.data.toDomain()
                is Failure -> throw result.exception
            }
        }

    /**
     * Clean up resources when the API client is no longer needed.
     */
    fun close() {
        publicClient.close()
    }
}

/**
 * Internal response model for contributor search endpoint.
 * Maps to server's JSON response structure.
 */
@Serializable
private data class ContributorSearchResponse(
    val contributors: List<ContributorSearchResultResponse>,
)

/**
 * Individual contributor result from search endpoint.
 */
@Serializable
private data class ContributorSearchResultResponse(
    val id: String,
    val name: String,
    @SerialName("book_count")
    val bookCount: Int,
) {
    fun toDomain(): ContributorSearchResult =
        ContributorSearchResult(
            id = id,
            name = name,
            bookCount = bookCount,
        )
}

// --- Book Edit API Models ---

/**
 * API request for PATCH /api/v1/books/{id}.
 * Only non-null fields are sent to the server.
 */
@Serializable
private data class BookUpdateApiRequest(
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val publisher: String? = null,
    @SerialName("publish_year")
    val publishYear: String? = null,
    val language: String? = null,
    val isbn: String? = null,
    val asin: String? = null,
    val explicit: Boolean? = null,
    val abridged: Boolean? = null,
    @SerialName("series_id")
    val seriesId: String? = null,
    val sequence: String? = null,
)

private fun BookUpdateRequest.toApiRequest(): BookUpdateApiRequest =
    BookUpdateApiRequest(
        title = title,
        subtitle = subtitle,
        description = description,
        publisher = publisher,
        publishYear = publishYear,
        language = language,
        isbn = isbn,
        asin = asin,
        explicit = explicit,
        abridged = abridged,
        seriesId = seriesId,
        sequence = sequence,
    )

/**
 * API request for PUT /api/v1/books/{id}/contributors.
 */
@Serializable
private data class SetContributorsApiRequest(
    val contributors: List<ContributorApiInput>,
)

@Serializable
private data class ContributorApiInput(
    val name: String,
    val roles: List<String>,
)

/**
 * API response for book edit operations.
 * Maps to server's enriched book response structure.
 */
@Serializable
private data class BookEditApiResponse(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val publisher: String? = null,
    @SerialName("publish_year")
    val publishYear: String? = null,
    val language: String? = null,
    val isbn: String? = null,
    val asin: String? = null,
    val explicit: Boolean = false,
    val abridged: Boolean = false,
    @SerialName("series_id")
    val seriesId: String? = null,
    @SerialName("series_name")
    val seriesName: String? = null,
    val sequence: String? = null,
    @SerialName("updated_at")
    val updatedAt: String,
) {
    fun toDomain(): BookEditResponse =
        BookEditResponse(
            id = id,
            title = title,
            subtitle = subtitle,
            description = description,
            publisher = publisher,
            publishYear = publishYear,
            language = language,
            isbn = isbn,
            asin = asin,
            explicit = explicit,
            abridged = abridged,
            seriesId = seriesId,
            seriesName = seriesName,
            sequence = sequence,
            updatedAt = updatedAt,
        )
}
