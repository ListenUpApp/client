package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.SearchDao
import com.calypsan.listenup.client.data.local.db.SeriesEntity
import com.calypsan.listenup.client.data.remote.ListenUpApiContract
import com.calypsan.listenup.client.data.remote.SeriesSearchResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger {}

/**
 * Contract interface for series repository operations.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [SeriesRepository], test implementation can be a mock or fake.
 */
interface SeriesRepositoryContract {
    /**
     * Search series for autocomplete during book editing.
     *
     * Implements "never stranded" pattern:
     * - Online: Uses server Bleve search (fuzzy, ranked by book count)
     * - Offline: Falls back to local Room FTS5 (simpler but always works)
     *
     * @param query Search query (min 2 characters recommended)
     * @param limit Maximum results to return (default 10)
     * @return List of matching series with their book counts
     */
    suspend fun searchSeries(
        query: String,
        limit: Int = 10,
    ): SeriesSearchResponse
}

/**
 * Result of series search.
 *
 * @property series List of matching series
 * @property isOfflineResult True if results came from local FTS (offline fallback)
 * @property tookMs Time taken for the search in milliseconds
 */
data class SeriesSearchResponse(
    val series: List<SeriesSearchResult>,
    val isOfflineResult: Boolean,
    val tookMs: Long,
)

/**
 * Repository for series search operations.
 *
 * Implements "never stranded" pattern:
 * - Online: Use server Bleve search (fuzzy matching, ranked by popularity)
 * - Offline: Fall back to local Room FTS5 (prefix matching, always available)
 *
 * @property api Server API client for series search
 * @property searchDao Local FTS5 search DAO
 * @property networkMonitor For checking online/offline status
 */
class SeriesRepository(
    private val api: ListenUpApiContract,
    private val searchDao: SearchDao,
    private val networkMonitor: NetworkMonitor,
) : SeriesRepositoryContract {
    /**
     * Search series for autocomplete.
     *
     * Tries server search first if online. Falls back to local FTS
     * on network error or if offline.
     */
    override suspend fun searchSeries(
        query: String,
        limit: Int,
    ): SeriesSearchResponse {
        // Sanitize query
        val sanitizedQuery = sanitizeQuery(query)
        if (sanitizedQuery.isBlank() || sanitizedQuery.length < 2) {
            return SeriesSearchResponse(
                series = emptyList(),
                isOfflineResult = false,
                tookMs = 0,
            )
        }

        // Try server search if online
        if (networkMonitor.isOnline()) {
            try {
                return searchServer(sanitizedQuery, limit)
            } catch (e: Exception) {
                logger.warn(e) { "Server series search failed, falling back to local FTS" }
                // Fall through to local search
            }
        }

        // Offline or server failed - use local FTS
        return searchLocal(sanitizedQuery, limit)
    }

    /**
     * Server-side Bleve search.
     */
    private suspend fun searchServer(
        query: String,
        limit: Int,
    ): SeriesSearchResponse =
        withContext(Dispatchers.IO) {
            val (series, duration) =
                measureTimedValue {
                    when (val result = api.searchSeries(query, limit)) {
                        is Success -> result.data
                        is Failure -> throw result.exception
                    }
                }

            logger.debug { "Server series search: query='$query', results=${series.size}, took=${duration.inWholeMilliseconds}ms" }

            SeriesSearchResponse(
                series = series,
                isOfflineResult = false,
                tookMs = duration.inWholeMilliseconds,
            )
        }

    /**
     * Local Room FTS5 search.
     */
    private suspend fun searchLocal(
        query: String,
        limit: Int,
    ): SeriesSearchResponse =
        withContext(Dispatchers.IO) {
            val (entities, duration) =
                measureTimedValue {
                    val ftsQuery = toFtsQuery(query)
                    try {
                        searchDao.searchSeries(ftsQuery, limit)
                    } catch (e: Exception) {
                        logger.warn(e) { "Series FTS search failed" }
                        emptyList()
                    }
                }

            val series = entities.map { it.toSearchResult() }

            logger.debug { "Local series search: query='$query', results=${series.size}, took=${duration.inWholeMilliseconds}ms" }

            SeriesSearchResponse(
                series = series,
                isOfflineResult = true,
                tookMs = duration.inWholeMilliseconds,
            )
        }

    /**
     * Sanitize search query to prevent injection and handle special chars.
     */
    private fun sanitizeQuery(query: String): String =
        query
            .trim()
            .replace(Regex("[\"*():]"), "") // Remove FTS special chars
            .take(100) // Limit length

    /**
     * Convert user query to FTS5 query syntax.
     */
    private fun toFtsQuery(query: String): String =
        query
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
}

/**
 * Map SeriesEntity to SeriesSearchResult.
 */
private fun SeriesEntity.toSearchResult(): SeriesSearchResult =
    SeriesSearchResult(
        id = id,
        name = name,
        bookCount = 0, // Not available in offline mode
    )
