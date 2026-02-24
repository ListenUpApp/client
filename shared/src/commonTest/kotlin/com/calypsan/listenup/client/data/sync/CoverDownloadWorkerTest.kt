package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.CoverDownloadDao
import com.calypsan.listenup.client.data.local.db.CoverDownloadStatus
import com.calypsan.listenup.client.data.local.db.CoverDownloadTaskEntity
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentiallyReturns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CoverDownloadWorkerTest {
    private fun createTask(id: String) =
        CoverDownloadTaskEntity(
            bookId = BookId(id),
            status = CoverDownloadStatus.PENDING,
            attempts = 0,
            createdAt = Timestamp.now(),
        )

    @Test
    fun `processQueue delays between each download`() =
        runTest {
            val dao: CoverDownloadDao = mock()
            val downloader: ImageDownloaderContract = mock()

            val tasks = listOf(createTask("a"), createTask("b"), createTask("c"))

            // First call returns tasks, second returns empty to stop loop
            everySuspend { dao.getNextBatch(limit = any()) } sequentiallyReturns listOf(tasks, emptyList())
            everySuspend { dao.markInProgress(any()) } returns Unit
            everySuspend { dao.markCompleted(any()) } returns Unit
            every { dao.observeRemainingCount() } returns flowOf(0)
            every { dao.observeCompletedCount() } returns flowOf(0)
            every { dao.observeTotalCount() } returns flowOf(0)
            everySuspend { downloader.downloadCover(any()) } returns Success(true)

            val worker = CoverDownloadWorker(dao, downloader)

            val startTime = currentTime
            worker.processQueue()
            val elapsed = currentTime - startTime

            // 3 tasks * 500ms delay = 1500ms minimum
            val expectedMinimum = 3 * 500L
            assertTrue(
                elapsed >= expectedMinimum,
                "Expected at least ${expectedMinimum}ms but took ${elapsed}ms",
            )
        }
}
