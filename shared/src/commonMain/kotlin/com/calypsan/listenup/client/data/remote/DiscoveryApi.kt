package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.data.remote.model.ApiResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contract interface for discovery API operations.
 *
 * Provides methods for social discovery features:
 * - What others are listening to
 * - Random book discovery
 */
interface DiscoveryApiContract {
    /**
     * Get books that other users are actively listening to.
     *
     * Returns books with reader avatars for social proof display.
     *
     * @param limit Maximum books to return (default 10, max 20)
     * @return Currently listening response with books and readers
     */
    suspend fun getCurrentlyListening(limit: Int = 10): CurrentlyListeningResponse

    /**
     * Get random books for discovery.
     *
     * Series-aware: only shows first book in series (or standalone books).
     * Excludes books the user has already started.
     *
     * @param limit Maximum books to return (default 10, max 20)
     * @return Discover books response
     */
    suspend fun getDiscoverBooks(limit: Int = 10): DiscoverBooksResponse
}

/**
 * API client for discovery operations.
 *
 * @property clientFactory Factory for creating authenticated HttpClient
 */
class DiscoveryApi(
    private val clientFactory: ApiClientFactory,
) : DiscoveryApiContract {
    /**
     * Get books that other users are actively listening to.
     *
     * Endpoint: GET /api/v1/social/currently-listening
     */
    override suspend fun getCurrentlyListening(limit: Int): CurrentlyListeningResponse {
        val client = clientFactory.getClient()
        val response: ApiResponse<CurrentlyListeningResponse> =
            client
                .get("/api/v1/social/currently-listening") {
                    parameter("limit", limit)
                }.body()

        if (!response.success || response.data == null) {
            throw RuntimeException("Currently listening API error: ${response.error ?: "Unknown error"}")
        }

        return response.data
    }

    /**
     * Get random books for discovery.
     *
     * Endpoint: GET /api/v1/social/discover
     */
    override suspend fun getDiscoverBooks(limit: Int): DiscoverBooksResponse {
        val client = clientFactory.getClient()
        val response: ApiResponse<DiscoverBooksResponse> =
            client
                .get("/api/v1/social/discover") {
                    parameter("limit", limit)
                }.body()

        if (!response.success || response.data == null) {
            throw RuntimeException("Discover books API error: ${response.error ?: "Unknown error"}")
        }

        return response.data
    }
}

// === Currently Listening DTOs ===

/**
 * Reader info for avatar display.
 */
@Serializable
data class CurrentlyListeningReaderResponse(
    @SerialName("user_id")
    val userId: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("avatar_color")
    val avatarColor: String,
    @SerialName("avatar_type")
    val avatarType: String = "auto",
    @SerialName("avatar_value")
    val avatarValue: String? = null,
)

/**
 * Book that others are actively reading.
 */
@Serializable
data class CurrentlyListeningBookResponse(
    @SerialName("id")
    val id: String,
    @SerialName("title")
    val title: String,
    @SerialName("author_name")
    val authorName: String? = null,
    @SerialName("cover_path")
    val coverPath: String? = null,
    @SerialName("cover_blur_hash")
    val coverBlurHash: String? = null,
    @SerialName("duration_ms")
    val durationMs: Long = 0,
    @SerialName("readers")
    val readers: List<CurrentlyListeningReaderResponse> = emptyList(),
    @SerialName("total_reader_count")
    val totalReaderCount: Int = 0,
)

/**
 * Response for currently listening endpoint.
 */
@Serializable
data class CurrentlyListeningResponse(
    @SerialName("books")
    val books: List<CurrentlyListeningBookResponse> = emptyList(),
)

// === Discover Books DTOs ===

/**
 * Book for discovery.
 */
@Serializable
data class DiscoverBookResponse(
    @SerialName("id")
    val id: String,
    @SerialName("title")
    val title: String,
    @SerialName("author_name")
    val authorName: String? = null,
    @SerialName("cover_path")
    val coverPath: String? = null,
    @SerialName("cover_blur_hash")
    val coverBlurHash: String? = null,
    @SerialName("duration_ms")
    val durationMs: Long = 0,
    @SerialName("series_name")
    val seriesName: String? = null,
)

/**
 * Response for discover books endpoint.
 */
@Serializable
data class DiscoverBooksResponse(
    @SerialName("books")
    val books: List<DiscoverBookResponse> = emptyList(),
)
