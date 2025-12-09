package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.remote.api.ListenUpApi
import com.calypsan.listenup.client.domain.model.Instance
import com.calypsan.listenup.client.domain.repository.InstanceRepository

/**
 * Implementation of InstanceRepository that fetches data from the ListenUp API.
 *
 * This repository acts as a single source of truth for instance data.
 * Future enhancements could include:
 * - In-memory caching with cache invalidation
 * - Offline support with local persistence
 * - Automatic refresh on stale data
 */
class InstanceRepositoryImpl(
    private val api: ListenUpApi,
) : InstanceRepository {
    /**
     * Cached instance data.
     * TODO: Add proper cache invalidation strategy when requirements are clearer.
     */
    private var cachedInstance: Instance? = null

    override suspend fun getInstance(forceRefresh: Boolean): Result<Instance> {
        // Return cached data if available and refresh not forced
        if (!forceRefresh && cachedInstance != null) {
            return Result.Success(cachedInstance!!)
        }

        // Fetch fresh data from API
        return api.getInstance().also { result ->
            // Cache successful results
            if (result is Result.Success) {
                cachedInstance = result.data
            }
        }
    }
}
