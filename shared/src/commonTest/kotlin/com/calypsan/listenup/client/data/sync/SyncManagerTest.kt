package com.calypsan.listenup.client.data.sync

import app.cash.turbine.test
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.getLastSyncTime
import com.calypsan.listenup.client.data.remote.SyncApi
import com.calypsan.listenup.client.data.remote.model.SyncBooksResponse
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncManagerTest {

    @Test
    fun syncManager_initialState_isIdle() = runTest {
        val syncApi = mock<SyncApi>(MockMode.autoUnit)
        val bookDao = mock<BookDao>(MockMode.autoUnit)
        val syncDao = mock<SyncDao>(MockMode.autoUnit)
        val imageDownloader = mock<ImageDownloader>(MockMode.autoUnit)
        val sseManager = mock<SSEManager>(MockMode.autoUnit)

        val manager = SyncManager(syncApi, bookDao, syncDao, imageDownloader, sseManager)

        manager.syncState.test {
            assertEquals(SyncStatus.Idle, awaitItem())
        }
    }

    @Test
    fun sync_transitionsToSyncing() = runTest {
        val syncApi = mock<SyncApi>(MockMode.autoUnit)
        val bookDao = mock<BookDao>(MockMode.autoUnit)
        val syncDao = mock<SyncDao>(MockMode.autoUnit)
        val imageDownloader = mock<ImageDownloader>(MockMode.autoUnit)
        val sseManager = mock<SSEManager>(MockMode.autoUnit)

        everySuspend { syncApi.getAllBooks(any()) } returns Result.Success(emptyList())
        everySuspend { syncDao.getLastSyncTime() } returns null

        val manager = SyncManager(syncApi, bookDao, syncDao, imageDownloader, sseManager)

        manager.syncState.test {
            assertEquals(SyncStatus.Idle, awaitItem())

            manager.sync()

            // Should transition Idle -> Syncing -> Success
            assertEquals(SyncStatus.Syncing, awaitItem())
            assertTrue(awaitItem() is SyncStatus.Success)
        }
    }
}
