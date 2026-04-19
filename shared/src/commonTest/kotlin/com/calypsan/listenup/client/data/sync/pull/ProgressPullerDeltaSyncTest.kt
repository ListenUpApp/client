package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.PendingOperationDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.AllProgressResponse
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves `ProgressPuller.pull` forwards `updatedAfter` to the SyncApi, enabling
 * SP2-backed delta sync. Prior to this commit, `updatedAfter` was documented as
 * "ignored" and the API call was parameterless — every sync re-downloaded the full
 * progress table.
 */
class ProgressPullerDeltaSyncTest {
    @Test
    fun `pull with updatedAfter forwards it to getAllProgress`() =
        runTest {
            val capturedUpdatedAfter = mutableListOf<String?>()
            val syncApi = mock<SyncApiContract> {
                everySuspend { getAllProgress(any()) } calls { args ->
                    capturedUpdatedAfter.add(args.arg(0) as String?)
                    Success(AllProgressResponse(items = emptyList()))
                }
            }
            val playbackPositionDao = mock<PlaybackPositionDao> {
                everySuspend { getByBookIds(any()) } returns emptyList()
                everySuspend { saveAll(any()) } returns Unit
            }
            val pendingOperationDao = mock<PendingOperationDao> {
                everySuspend { getPendingMarkCompleteBookIds() } returns emptyList()
            }

            val puller = ProgressPuller(syncApi, playbackPositionDao, pendingOperationDao)

            puller.pull(updatedAfter = "2026-04-19T10:00:00Z") {}

            assertEquals(listOf<String?>("2026-04-19T10:00:00Z"), capturedUpdatedAfter)
        }

    @Test
    fun `pull with null updatedAfter forwards null to getAllProgress`() =
        runTest {
            val capturedUpdatedAfter = mutableListOf<String?>()
            val syncApi = mock<SyncApiContract> {
                everySuspend { getAllProgress(any()) } calls { args ->
                    capturedUpdatedAfter.add(args.arg(0) as String?)
                    Success(AllProgressResponse(items = emptyList()))
                }
            }
            val playbackPositionDao = mock<PlaybackPositionDao> {
                everySuspend { getByBookIds(any()) } returns emptyList()
                everySuspend { saveAll(any()) } returns Unit
            }
            val pendingOperationDao = mock<PendingOperationDao> {
                everySuspend { getPendingMarkCompleteBookIds() } returns emptyList()
            }

            val puller = ProgressPuller(syncApi, playbackPositionDao, pendingOperationDao)

            puller.pull(updatedAfter = null) {}

            assertEquals(listOf<String?>(null), capturedUpdatedAfter)
        }
}
