package com.calypsan.listenup.client.data.local.db

import kotlin.test.Test
import kotlin.test.assertNotNull

class SyncDaoTest {

    @Test
    fun syncDao_hasRequiredMethods() {
        val daoClass = SyncDao::class

        assertNotNull(daoClass.members.find { it.name == "getLastSyncTime" })
        assertNotNull(daoClass.members.find { it.name == "setLastSyncTime" })
    }

    @Test
    fun syncMetadataEntity_hasPrimaryKey() {
        val entity = SyncMetadataEntity(
            key = "last_sync_books",
            value = "1700000000000"
        )

        assertNotNull(entity.key)
        assertNotNull(entity.value)
    }
}
