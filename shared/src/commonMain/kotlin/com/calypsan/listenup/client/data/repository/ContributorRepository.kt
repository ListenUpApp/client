package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.SearchDao
import com.calypsan.listenup.client.data.remote.ApplyContributorMetadataResult
import com.calypsan.listenup.client.data.remote.ContributorApiContract
import com.calypsan.listenup.client.data.remote.ContributorSearchResult
import com.calypsan.listenup.client.data.remote.MetadataApiContract
import com.calypsan.listenup.client.data.remote.model.ContributorMetadataSearchResult
import com.calypsan.listenup.client.data.repository.common.QueryUtils
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

    /**
     * Apply Audible metadata to a contributor.
     *
     * Fetches contributor profile (biography, image) from Audible and applies
     * selected fields to the local contributor.
     *
     * @param contributorId Local contributor ID
     * @param asin Audible ASIN for the contributor
     * @param imageUrl Optional image URL to apply
     * @param applyName Whether to apply the name from Audible
     * @param applyBiography Whether to apply the biography from Audible
     * @param applyImage Whether to download and apply the image
     * @return Result indicating success, need for disambiguation, or error
     */
    suspend fun applyMetadataFromAudible(
        contributorId: String,
        asin: String,
        imageUrl: String? = null,
        applyName: Boolean = true,
        applyBiography: Boolean = true,
        applyImage: Boolean = true,
    ): ContributorMetadataResult

    /**
     * Delete a contributor.
     *
     * Soft-deletes the contributor on the server. The contributor will be
     * removed from the local database on the next sync.
     *
     * @param contributorId Contributor ID to delete
     * @return Success or failure result
     */
    suspend fun deleteContributor(contributorId: String): Result<Unit>
}

/**
 * Result of applying contributor metadata from Audible.
 */
sealed class ContributorMetadataResult {
    /** Metadata applied successfully */
    data object Success : ContributorMetadataResult()

    /**
     * Disambiguation required - either multiple matches found or no matches found.
     * If candidates is empty, the user should be prompted to search with a different name.
     *
     * @param candidates List of matching contributors from Audible (may be empty)
     * @param searchedName The name that was searched on Audible
     * @param message Server message explaining the situation
     */
    data class NeedsDisambiguation(
        val candidates: List<ContributorMetadataSearchResult>,
        val searchedName: String? = null,
        val message: String? = null,
    ) : ContributorMetadataResult()

    /** Error occurred */
    data class Error(
        val message: String,
    ) : ContributorMetadataResult()
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
 * Repository for contributor operations.
 *
 * Implements "never stranded" pattern for search:
 * - Online: Use server Bleve search (fuzzy matching, ranked by popularity)
 * - Offline: Fall back to local Room FTS5 (prefix matching, always available)
 *
 * Also handles Audible metadata fetching and contributor deletion.
 *
 * @property api Server API client for contributor operations
 * @property metadataApi API client for Audible metadata
 * @property searchDao Local FTS5 search DAO
 * @property networkMonitor For checking online/offline status
 */
class ContributorRepository(
    private val api: ContributorApiContract,
    private val metadataApi: MetadataApiContract,
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
        val sanitizedQuery = QueryUtils.sanitize(query)
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
                    val ftsQuery = QueryUtils.toFtsQuery(query)
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

    // === Metadata Operations ===

    /**
     * Apply Audible metadata to a contributor.
     *
     * Uses the metadata API to fetch contributor profile from Audible
     * and apply it to the local contributor.
     */
    override suspend fun applyMetadataFromAudible(
        contributorId: String,
        asin: String,
        imageUrl: String?,
        applyName: Boolean,
        applyBiography: Boolean,
        applyImage: Boolean,
    ): ContributorMetadataResult =
        withContext(IODispatcher) {
            try {
                when (
                    val result =
                        metadataApi.applyContributorMetadata(
                            contributorId = contributorId,
                            asin = asin,
                            imageUrl = imageUrl,
                            applyName = applyName,
                            applyBiography = applyBiography,
                            applyImage = applyImage,
                        )
                ) {
                    is ApplyContributorMetadataResult.Success -> {
                        logger.info { "Applied Audible metadata to contributor $contributorId" }
                        ContributorMetadataResult.Success
                    }

                    is ApplyContributorMetadataResult.NeedsDisambiguation -> {
                        logger.debug {
                            "Contributor metadata needs disambiguation: ${result.candidates.size} candidates for '${result.searchedName}'"
                        }
                        ContributorMetadataResult.NeedsDisambiguation(
                            candidates = result.candidates,
                            searchedName = result.searchedName,
                            message = result.message,
                        )
                    }

                    is ApplyContributorMetadataResult.Error -> {
                        logger.warn { "Failed to apply contributor metadata: ${result.message}" }
                        ContributorMetadataResult.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error applying contributor metadata" }
                ContributorMetadataResult.Error(e.message ?: "Unknown error")
            }
        }

    /**
     * Delete a contributor.
     *
     * Calls the server API to soft-delete the contributor.
     */
    override suspend fun deleteContributor(contributorId: String): Result<Unit> =
        suspendRunCatching {
            withContext(IODispatcher) {
                api.deleteContributor(contributorId)
                logger.info { "Deleted contributor $contributorId" }
            }
        }
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
