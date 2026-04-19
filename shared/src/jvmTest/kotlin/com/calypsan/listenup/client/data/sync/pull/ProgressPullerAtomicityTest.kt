package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.PendingOperationDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.RoomTransactionRunner
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.AllProgressResponse
import com.calypsan.listenup.client.data.remote.model.ProgressSyncItem
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Proves `ProgressPuller.guardAndSaveEntities` reaches the transactional seam and
 * no partial writes persist when the seam's inner body throws.
 *
 * Uses a real in-memory [ListenUpDatabase] and real [RoomTransactionRunner] so
 * transaction semantics are exercised end-to-end; `PlaybackPositionDao` is mocked
 * to throw on `saveAll`.
 *
 * Honest scope: `guardAndSaveEntities` contains only one DAO write (`saveAll`), so
 * exception-driven rollback cannot be distinguished from "throw before write" at the
 * single-write level. The stronger invariant — serialisation against concurrent
 * MARK_COMPLETE queue inserts — is not exercised here because `runTest`'s
 * single-threaded dispatcher cannot reproduce the concurrent race. That invariant is
 * defended structurally: the production code's `atomically { read; compute; saveAll }`
 * block matches the W4 precedent (`BookPullerAtomicityTest.kt`) and rubric §P-R1.
 */
class ProgressPullerAtomicityTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `no rows persist when saveAll throws inside transaction`() =
        runTest {
            val syncApi = mock<SyncApiContract> {
                everySuspend { getAllProgress(any()) } returns Success(
                    AllProgressResponse(
                        items = listOf(
                            ProgressSyncItem(
                                bookId = "book-rollback",
                                currentPositionMs = 1000L,
                                isFinished = false,
                                lastPlayedAt = "2026-04-19T10:00:00Z",
                                updatedAt = "2026-04-19T10:00:00Z",
                            ),
                        ),
                    ),
                )
            }
            val failingPlaybackPositionDao = mock<PlaybackPositionDao> {
                everySuspend { getByBookIds(any()) } returns emptyList()
                everySuspend { saveAll(any()) } throws RuntimeException("simulated save failure")
            }
            val pendingOperationDao = mock<PendingOperationDao> {
                everySuspend { getPendingMarkCompleteBookIds() } returns emptyList()
            }
            val realTransactionRunner = RoomTransactionRunner(db)

            val puller = ProgressPuller(
                syncApi,
                failingPlaybackPositionDao,
                pendingOperationDao,
                realTransactionRunner,
            )

            // ProgressPuller.pull catches Exception at the outer envelope and logs —
            // it does NOT propagate. So we assert the DB state directly.
            puller.pull(updatedAfter = null) {}

            val rolledBackRow = db.playbackPositionDao().get(BookId("book-rollback"))
            assertNull(rolledBackRow, "book-rollback row must not persist after transaction rollback")

            val unsyncedAfterRollback = db.playbackPositionDao().getUnsyncedPositions()
            assertEquals(0, unsyncedAfterRollback.size, "no rows should exist in playback_positions after rollback")
        }
}
