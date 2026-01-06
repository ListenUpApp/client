package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.SearchDao
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SeriesEntity
import com.calypsan.listenup.client.data.local.db.SeriesWithBookCount
import com.calypsan.listenup.client.data.local.db.SeriesWithBooks
import com.calypsan.listenup.client.data.remote.SeriesApiContract
import kotlinx.coroutines.flow.Flow
import com.calypsan.listenup.client.data.remote.SeriesSearchResult
import com.calypsan.listenup.client.data.repository.common.QueryUtils
import io.github.oshai.kotlinlogging.KotlinLogging
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

    /**
     * Observe all series with their book counts.
     *
     * Used for displaying series list in library views.
     *
     * @return Flow emitting list of series with book counts
     */
    fun observeSeriesWithBookCount(): Flow<List<SeriesWithBookCount>>

    /**
     * Observe a specific series with all its books.
     *
     * Used for series detail page.
     *
     * @param seriesId The series ID
     * @return Flow emitting series with books, or null if not found
     */
    fun observeSeriesWithBooks(seriesId: String): Flow<SeriesWithBooks?>

    /**
     * Get a series by ID synchronously.
     *
     * @param seriesId The series ID
     * @return SeriesEntity if found, null otherwise
     */
    suspend fun getById(seriesId: String): SeriesEntity?
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
    private val api: SeriesApiContract,
    private val searchDao: SearchDao,
    private val seriesDao: SeriesDao,
    private val networkMonitor: NetworkMonitor,
) : SeriesRepositoryContract {
    override fun observeSeriesWithBookCount(): Flow<List<SeriesWithBookCount>> =
        seriesDao.observeAllWithBookCount()

    override fun observeSeriesWithBooks(seriesId: String): Flow<SeriesWithBooks?> =
        seriesDao.observeByIdWithBooks(seriesId)

    override suspend fun getById(seriesId: String): SeriesEntity? =
        seriesDao.getById(seriesId)

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
        val sanitizedQuery = QueryUtils.sanitize(query)
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
        withContext(IODispatcher) {
            val (series, duration) =
                measureTimedValue {
                    when (val result = api.searchSeries(query, limit)) {
                        is Success -> result.data
                        is Failure -> throw result.exception
                    }
                }

            logger.debug {
                "Server series search: query='$query', results=${series.size}, took=${duration.inWholeMilliseconds}ms"
            }

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
        withContext(IODispatcher) {
            val (entities, duration) =
                measureTimedValue {
                    val ftsQuery = QueryUtils.toFtsQuery(query)
                    try {
                        searchDao.searchSeries(ftsQuery, limit)
                    } catch (e: Exception) {
                        logger.warn(e) { "Series FTS search failed" }
                        emptyList()
                    }
                }

            val series = entities.map { it.toSearchResult() }

            logger.debug {
                "Local series search: query='$query', results=${series.size}, took=${duration.inWholeMilliseconds}ms"
            }

            SeriesSearchResponse(
                series = series,
                isOfflineResult = true,
                tookMs = duration.inWholeMilliseconds,
            )
        }
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
