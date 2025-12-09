package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.SearchDao
import com.calypsan.listenup.client.data.local.db.SeriesEntity
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.SearchApi
import com.calypsan.listenup.client.data.remote.SearchFacetsResponse
import com.calypsan.listenup.client.data.remote.SearchHitResponse
import com.calypsan.listenup.client.data.remote.SearchResponse
import com.calypsan.listenup.client.domain.model.FacetCount
import com.calypsan.listenup.client.domain.model.SearchFacets
import com.calypsan.listenup.client.domain.model.SearchHit
import com.calypsan.listenup.client.domain.model.SearchHitType
import com.calypsan.listenup.client.domain.model.SearchResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger {}

/**
 * Repository for search operations.
 *
 * Implements "never stranded" pattern:
 * - Online: Use server Bleve search (fuzzy, faceted, hierarchical)
 * - Offline: Fall back to local Room FTS5 (simpler but always works)
 *
 * The caller doesn't need to know which path was taken—both return
 * the same SearchResult type. The `isOfflineResult` flag indicates
 * which was used (for optional UI indication).
 *
 * @property searchApi Server search API client
 * @property searchDao Local FTS5 search DAO
 * @property imageStorage For resolving local cover paths
 * @property networkMonitor For checking online/offline status
 */
class SearchRepository(
    private val searchApi: SearchApi,
    private val searchDao: SearchDao,
    private val imageStorage: ImageStorage,
    private val networkMonitor: NetworkMonitor
) {
    /**
     * Search across books, contributors, and series.
     *
     * Tries server search first if online. Falls back to local FTS
     * on network error or if offline.
     *
     * @param query Search query string
     * @param types Types to search (null = all)
     * @param genres Genre slugs to filter by
     * @param genrePath Genre path prefix for hierarchical filtering
     * @param limit Max results per type
     */
    suspend fun search(
        query: String,
        types: List<SearchHitType>? = null,
        genres: List<String>? = null,
        genrePath: String? = null,
        limit: Int = 20
    ): SearchResult {
        // Sanitize query
        val sanitizedQuery = sanitizeQuery(query)
        if (sanitizedQuery.isBlank()) {
            return SearchResult(
                query = query,
                total = 0,
                tookMs = 0,
                hits = emptyList()
            )
        }

        // Try server search if online
        if (networkMonitor.isOnline()) {
            try {
                return searchServer(sanitizedQuery, types, genres, genrePath, limit)
            } catch (e: Exception) {
                logger.warn(e) { "Server search failed, falling back to local FTS" }
                // Fall through to local search
            }
        }

        // Offline or server failed - use local FTS
        return searchLocal(sanitizedQuery, types, limit)
    }

    /**
     * Server-side Bleve search.
     */
    private suspend fun searchServer(
        query: String,
        types: List<SearchHitType>?,
        genres: List<String>?,
        genrePath: String?,
        limit: Int
    ): SearchResult = withContext(Dispatchers.IO) {
        val typesParam = types?.joinToString(",") { it.name.lowercase() }
        val genresParam = genres?.joinToString(",")

        val response = searchApi.search(
            query = query,
            types = typesParam,
            genres = genresParam,
            genrePath = genrePath,
            limit = limit
        )

        response.toDomain(imageStorage)
    }

    /**
     * Local Room FTS5 search.
     */
    private suspend fun searchLocal(
        query: String,
        types: List<SearchHitType>?,
        limit: Int
    ): SearchResult = withContext(Dispatchers.IO) {
        val (result, duration) = measureTimedValue {
            val ftsQuery = toFtsQuery(query)
            val searchTypes = types ?: SearchHitType.entries

            val hits = mutableListOf<SearchHit>()

            // Search each type
            if (SearchHitType.BOOK in searchTypes) {
                try {
                    val books = searchDao.searchBooks(ftsQuery, limit)
                    hits.addAll(books.map { it.toSearchHit(imageStorage) })
                } catch (e: Exception) {
                    logger.warn(e) { "Book FTS search failed" }
                }
            }

            if (SearchHitType.CONTRIBUTOR in searchTypes) {
                try {
                    val contributors = searchDao.searchContributors(ftsQuery, limit / 2)
                    hits.addAll(contributors.map { it.toSearchHit() })
                } catch (e: Exception) {
                    logger.warn(e) { "Contributor FTS search failed" }
                }
            }

            if (SearchHitType.SERIES in searchTypes) {
                try {
                    val series = searchDao.searchSeries(ftsQuery, limit / 2)
                    hits.addAll(series.map { it.toSearchHit() })
                } catch (e: Exception) {
                    logger.warn(e) { "Series FTS search failed" }
                }
            }

            hits
        }

        SearchResult(
            query = query,
            total = result.size,
            tookMs = duration.inWholeMilliseconds,
            hits = result,
            facets = SearchFacets(), // No facets in local search
            isOfflineResult = true
        )
    }

    /**
     * Sanitize search query to prevent injection and handle special chars.
     */
    private fun sanitizeQuery(query: String): String {
        return query
            .trim()
            .replace(Regex("[\"*():]"), "") // Remove FTS special chars
            .take(100) // Limit length
    }

    /**
     * Convert user query to FTS5 query syntax.
     *
     * "brandon sanderson" -> "brandon* sanderson*"
     * Adds prefix matching for partial word search.
     */
    private fun toFtsQuery(query: String): String {
        return query
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
    }
}

// --- Extension functions for mapping ---

private fun SearchResponse.toDomain(imageStorage: ImageStorage): SearchResult {
    return SearchResult(
        query = query,
        total = total.toInt(),
        tookMs = tookMs,
        hits = hits.map { it.toDomain(imageStorage) },
        facets = facets?.toDomain() ?: SearchFacets(),
        isOfflineResult = false
    )
}

private fun SearchHitResponse.toDomain(imageStorage: ImageStorage): SearchHit {
    val hitType = when (type.lowercase()) {
        "book" -> SearchHitType.BOOK
        "contributor" -> SearchHitType.CONTRIBUTOR
        "series" -> SearchHitType.SERIES
        else -> SearchHitType.BOOK
    }

    // Resolve cover path for books (convert String to BookId)
    val coverPath = if (hitType == SearchHitType.BOOK) {
        val bookId = BookId(id)
        if (imageStorage.exists(bookId)) imageStorage.getCoverPath(bookId) else null
    } else null

    return SearchHit(
        id = id,
        type = hitType,
        name = name,
        subtitle = subtitle,
        author = author,
        narrator = narrator,
        seriesName = seriesName,
        duration = duration,
        bookCount = bookCount,
        coverPath = coverPath,
        score = score,
        highlight = highlights?.values?.firstOrNull()
    )
}

private fun SearchFacetsResponse.toDomain(): SearchFacets {
    return SearchFacets(
        types = types?.map { FacetCount(it.value, it.count) } ?: emptyList(),
        genres = genres?.map { FacetCount(it.value, it.count) } ?: emptyList(),
        authors = authors?.map { FacetCount(it.value, it.count) } ?: emptyList(),
        narrators = narrators?.map { FacetCount(it.value, it.count) } ?: emptyList()
    )
}

private fun BookEntity.toSearchHit(imageStorage: ImageStorage): SearchHit {
    val coverPath = if (imageStorage.exists(id)) imageStorage.getCoverPath(id) else null

    return SearchHit(
        id = id.value,
        type = SearchHitType.BOOK,
        name = title,
        subtitle = subtitle,
        author = null, // Would need join - acceptable for offline
        narrator = null,
        seriesName = seriesName,
        duration = totalDuration,
        bookCount = null,
        coverPath = coverPath,
        score = 1.0f // No scoring in local search
    )
}

private fun ContributorEntity.toSearchHit(): SearchHit {
    return SearchHit(
        id = id,
        type = SearchHitType.CONTRIBUTOR,
        name = name,
        bookCount = null, // Would need count - acceptable for offline
        score = 1.0f
    )
}

private fun SeriesEntity.toSearchHit(): SearchHit {
    return SearchHit(
        id = id,
        type = SearchHitType.SERIES,
        name = name,
        bookCount = null,
        score = 1.0f
    )
}
