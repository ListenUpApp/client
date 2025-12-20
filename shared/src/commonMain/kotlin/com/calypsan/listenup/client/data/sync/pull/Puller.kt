package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.data.sync.model.SyncStatus

/**
 * Common interface for entity pullers.
 *
 * Enables testing of PullSyncOrchestrator by allowing mock implementations.
 */
interface Puller {
    /**
     * Pull entities from server with pagination.
     *
     * @param updatedAfter ISO timestamp for delta sync, null for full sync
     * @param onProgress Callback for progress updates
     */
    suspend fun pull(
        updatedAfter: String?,
        onProgress: (SyncStatus) -> Unit,
    )
}
