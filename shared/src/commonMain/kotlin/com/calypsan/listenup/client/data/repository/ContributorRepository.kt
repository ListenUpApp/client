package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.SearchDao
import com.calypsan.listenup.client.data.remote.ContributorSearchResult
import com.calypsan.listenup.client.data.remote.ListenUpApiContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger {}

/**
 * Contract interface for contributor repository operations.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [ContributorRepository], test implementation can be a mock or fake.
 */
interface ContributorRepositoryContract {
    /**
     * Search contributors for autocomplete during book editing.
     *
     * Implements "never stranded" pattern:
     * - Online: Uses server Bleve search (fuzzy, ranked by book count)
     * - Offline: Falls back to local Room FTS5 (simpler but always works)
     *
     * @param query Search query (min 2 characters recommended)
     * @param limit Maximum results to return (default 10)
     * @return List of matching contributors with their book counts
     */
    suspend fun searchContributors(
        query: String,
        limit: Int = 10,
    ): ContributorSearchResponse
}

/**
 * Result of contributor search.
 *
 * @property contributors List of matching contributors
 * @property isOfflineResult True if results came from local FTS (offline fallback)
 * @property tookMs Time taken for the search in milliseconds
 */
data class ContributorSearchResponse(
    val contributors: List<ContributorSearchResult>,
    val isOfflineResult: Boolean,
    val tookMs: Long,
)

/**
 * Repository for contributor search operations.
 *
 * Implements "never stranded" pattern:
 * - Online: Use server Bleve search (fuzzy matching, ranked by popularity)
 * - Offline: Fall back to local Room FTS5 (prefix matching, always available)
 *
 * The caller doesn't need to know which path was taken—both return
 * the same ContributorSearchResult type. The `isOfflineResult` flag indicates
 * which was used (for optional UI indication).
 *
 * @property api Server API client for contributor search
 * @property searchDao Local FTS5 search DAO
 * @property networkMonitor For checking online/offline status
 */
class ContributorRepository(
    private val api: ListenUpApiContract,
    private val searchDao: SearchDao,
    private val networkMonitor: NetworkMonitor,
) : ContributorRepositoryContract {
    /**
     * Search contributors for autocomplete.
     *
     * Tries server search first if online. Falls back to local FTS
     * on network error or if offline.
     *
     * @param query Search query (min 2 characters recommended)
     * @param limit Maximum results to return
     */
    override suspend fun searchContributors(
        query: String,
        limit: Int,
    ): ContributorSearchResponse {
        // Sanitize query
        val sanitizedQuery = sanitizeQuery(query)
        if (sanitizedQuery.isBlank() || sanitizedQuery.length < 2) {
            return ContributorSearchResponse(
                contributors = emptyList(),
                isOfflineResult = false,
                tookMs = 0,
            )
        }

        // Try server search if online
        if (networkMonitor.isOnline()) {
            try {
                return searchServer(sanitizedQuery, limit)
            } catch (e: Exception) {
                logger.warn(e) { "Server contributor search failed, falling back to local FTS" }
                // Fall through to local search
            }
        }

        // Offline or server failed - use local FTS
        return searchLocal(sanitizedQuery, limit)
    }

    /**
     * Server-side Bleve search.
     *
     * Uses O(log n) Bleve index with:
     * - Prefix matching ("bran" → "Brandon Sanderson")
     * - Word matching ("sanderson" in "Brandon Sanderson")
     * - Fuzzy matching for typo tolerance
     * - Results ranked by score and book count
     */
    private suspend fun searchServer(
        query: String,
        limit: Int,
    ): ContributorSearchResponse =
        withContext(IODispatcher) {
            val (contributors, duration) =
                measureTimedValue {
                    when (val result = api.searchContributors(query, limit)) {
                        is Success -> result.data
                        is Failure -> throw result.exception
                    }
                }

            logger.debug {
                "Server contributor search: query='$query', results=${contributors.size}, took=${duration.inWholeMilliseconds}ms"
            }

            ContributorSearchResponse(
                contributors = contributors,
                isOfflineResult = false,
                tookMs = duration.inWholeMilliseconds,
            )
        }

    /**
     * Local Room FTS5 search.
     *
     * Uses FTS5 prefix matching on contributor name.
     * Simpler than server search but always available offline.
     * Note: Book count is not available in offline mode.
     */
    private suspend fun searchLocal(
        query: String,
        limit: Int,
    ): ContributorSearchResponse =
        withContext(IODispatcher) {
            val (entities, duration) =
                measureTimedValue {
                    val ftsQuery = toFtsQuery(query)
                    try {
                        searchDao.searchContributors(ftsQuery, limit)
                    } catch (e: Exception) {
                        logger.warn(e) { "Contributor FTS search failed" }
                        emptyList()
                    }
                }

            val contributors = entities.map { it.toSearchResult() }

            logger.debug {
                "Local contributor search: query='$query', results=${contributors.size}, took=${duration.inWholeMilliseconds}ms"
            }

            ContributorSearchResponse(
                contributors = contributors,
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
     *
     * "brandon sanderson" -> "brandon* sanderson*"
     * Adds prefix matching for partial word search.
     */
    private fun toFtsQuery(query: String): String =
        query
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
}

/**
 * Map ContributorEntity to ContributorSearchResult.
 *
 * Note: Book count is not available from local FTS, defaults to 0.
 * This is acceptable for offline mode where we prioritize availability
 * over complete information.
 */
private fun ContributorEntity.toSearchResult(): ContributorSearchResult =
    ContributorSearchResult(
        id = id,
        name = name,
        bookCount = 0, // Not available in offline mode
    )
