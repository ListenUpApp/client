@file:OptIn(ExperimentalTime::class)

package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.remote.ListenUpApiContract
import com.calypsan.listenup.client.data.remote.SeriesEditResponse
import com.calypsan.listenup.client.data.remote.SeriesUpdateRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Contract for series editing operations.
 *
 * Provides methods for modifying series metadata.
 * Changes are sent to the server and then applied to the local database.
 */
interface SeriesEditRepositoryContract {
    /**
     * Update series metadata.
     *
     * Sends update to server, then updates local database on success.
     * Only non-null fields in the request are updated (PATCH semantics).
     *
     * @param seriesId ID of the series to update
     * @param name New name (null = don't change)
     * @param description New description (null = don't change)
     * @return Result containing the updated series response
     */
    suspend fun updateSeries(
        seriesId: String,
        name: String?,
        description: String?,
    ): Result<SeriesEditResponse>
}

/**
 * Repository for series editing operations.
 *
 * Handles the edit flow:
 * 1. Send changes to server via API
 * 2. On success, update local database to reflect changes
 * 3. Return result to caller
 *
 * Local database is updated immediately after successful server response,
 * ensuring the UI reflects changes without waiting for next sync.
 *
 * @property api ListenUp API client
 * @property seriesDao Room DAO for series operations
 */
class SeriesEditRepository(
    private val api: ListenUpApiContract,
    private val seriesDao: SeriesDao,
) : SeriesEditRepositoryContract {
    /**
     * Update series metadata.
     *
     * Flow:
     * 1. Send PATCH request to server
     * 2. On success, update local SeriesEntity with new values
     * 3. Return the server response
     */
    override suspend fun updateSeries(
        seriesId: String,
        name: String?,
        description: String?,
    ): Result<SeriesEditResponse> =
        withContext(IODispatcher) {
            logger.debug { "Updating series: $seriesId" }

            // Create request with only non-null fields
            val request =
                SeriesUpdateRequest(
                    name = name,
                    description = description,
                )

            // Send update to server
            when (val result = api.updateSeries(seriesId, request)) {
                is Success -> {
                    // Update local database
                    updateLocalSeries(seriesId, result.data)
                    logger.info { "Series updated successfully: $seriesId" }
                    result
                }

                is Failure -> {
                    logger.error(result.exception) { "Failed to update series: $seriesId" }
                    result
                }
            }
        }

    /**
     * Update local SeriesEntity with server response data.
     *
     * Updates fields that are present in both SeriesEntity and SeriesEditResponse.
     */
    private suspend fun updateLocalSeries(
        seriesId: String,
        response: SeriesEditResponse,
    ) {
        val existing =
            seriesDao.getById(seriesId) ?: run {
                logger.warn { "Series not found in local database: $seriesId" }
                return
            }

        // Parse the ISO 8601 timestamp from server
        val serverUpdatedAt =
            try {
                Timestamp.fromEpochMillis(Instant.parse(response.updatedAt).toEpochMilliseconds())
            } catch (e: Exception) {
                logger.warn(e) { "Failed to parse timestamp: ${response.updatedAt}" }
                Timestamp.now()
            }

        // Update fields that SeriesEntity supports
        val updated =
            existing.copy(
                name = response.name,
                description = response.description,
                // Update sync metadata
                updatedAt = serverUpdatedAt,
                lastModified = Timestamp.now(),
                syncState = SyncState.SYNCED, // We just synced with server
            )

        seriesDao.upsert(updated)
        logger.debug { "Local series updated: $seriesId" }
    }
}
