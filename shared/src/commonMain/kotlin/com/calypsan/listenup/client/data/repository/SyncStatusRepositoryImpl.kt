package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.clearLastSyncTime
import com.calypsan.listenup.client.data.local.db.getLastSyncTime
import com.calypsan.listenup.client.data.local.db.setLastSyncTime
import com.calypsan.listenup.client.domain.repository.SyncStatusRepository

/**
 * Implementation of SyncStatusRepository using Room.
 *
 * Wraps SyncDao and uses existing extension functions for type-safe
 * timestamp operations. Delegates to extension functions defined
 * alongside SyncDao for consistency.
 *
 * @property syncDao Room DAO for sync metadata operations
 */
class SyncStatusRepositoryImpl(
    private val syncDao: SyncDao,
) : SyncStatusRepository {

    override suspend fun getLastSyncTime(): Timestamp? =
        syncDao.getLastSyncTime()

    override suspend fun setLastSyncTime(timestamp: Timestamp) =
        syncDao.setLastSyncTime(timestamp)

    override suspend fun clearLastSyncTime() =
        syncDao.clearLastSyncTime()
}
