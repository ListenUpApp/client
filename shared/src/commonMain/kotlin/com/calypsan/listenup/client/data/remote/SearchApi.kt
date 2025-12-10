package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.data.remote.model.ApiResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API client for search endpoints.
 *
 * Provides federated search across books, contributors, and series.
 * Uses server-side Bleve full-text search for fuzzy matching and faceting.
 *
 * Implements [SearchApiContract] for testability - tests can mock the interface
 * without needing to mock HTTP client internals.
 *
 * @property clientFactory Factory for creating authenticated HttpClient
 */
class SearchApi(
    private val clientFactory: ApiClientFactory,
) : SearchApiContract {
    /**
     * Search across books, contributors, and series.
     *
     * Endpoint: GET /api/v1/search
     *
     * @param query Search query string
     * @param types Comma-separated types to search (book,contributor,series)
     * @param genres Comma-separated genre slugs to filter by
     * @param genrePath Genre path prefix for hierarchical filtering
     * @param minDuration Minimum duration in hours
     * @param maxDuration Maximum duration in hours
     * @param limit Max results to return
     * @param offset Pagination offset
     */
    override suspend fun search(
        query: String,
        types: String?,
        genres: String?,
        genrePath: String?,
        minDuration: Float?,
        maxDuration: Float?,
        limit: Int,
        offset: Int,
    ): SearchResponse {
        val client = clientFactory.getClient()
        val response: ApiResponse<SearchResponse> =
            client
                .get("/api/v1/search") {
                    parameter("q", query)
                    types?.let { parameter("types", it) }
                    genres?.let { parameter("genres", it) }
                    genrePath?.let { parameter("genre_path", it) }
                    minDuration?.let { parameter("min_duration", it) }
                    maxDuration?.let { parameter("max_duration", it) }
                    parameter("limit", limit)
                    parameter("offset", offset)
                    parameter("facets", "true")
                }.body()

        return response.data ?: throw SearchException(response.error ?: "Search failed")
    }
}

/**
 * Search API response.
 */
@Serializable
data class SearchResponse(
    val query: String,
    val total: Long,
    @SerialName("took_ms")
    val tookMs: Long,
    val hits: List<SearchHitResponse>,
    val facets: SearchFacetsResponse? = null,
)

@Serializable
data class SearchHitResponse(
    val id: String,
    val type: String,
    val score: Float,
    val name: String,
    val subtitle: String? = null,
    val author: String? = null,
    val narrator: String? = null,
    @SerialName("series_name")
    val seriesName: String? = null,
    val duration: Long? = null,
    @SerialName("book_count")
    val bookCount: Int? = null,
    val highlights: Map<String, String>? = null,
)

@Serializable
data class SearchFacetsResponse(
    val types: List<FacetCountResponse>? = null,
    val genres: List<FacetCountResponse>? = null,
    val authors: List<FacetCountResponse>? = null,
    val narrators: List<FacetCountResponse>? = null,
)

@Serializable
data class FacetCountResponse(
    val value: String,
    val count: Int,
)

/**
 * Exception thrown when search fails.
 */
class SearchException(
    message: String,
) : Exception(message)
