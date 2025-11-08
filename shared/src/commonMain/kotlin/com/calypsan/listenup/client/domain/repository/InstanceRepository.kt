package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.domain.model.Instance

/**
 * Repository for managing server instance data.
 *
 * This interface defines the contract for accessing instance information.
 * Implementations handle data fetching, caching, and error handling.
 */
interface InstanceRepository {
    /**
     * Retrieves the current server instance information.
     *
     * @param forceRefresh If true, bypasses any cached data and fetches fresh data from the server
     * @return Result containing the Instance on success, or an error on failure
     */
    suspend fun getInstance(forceRefresh: Boolean = false): Result<Instance>
}
