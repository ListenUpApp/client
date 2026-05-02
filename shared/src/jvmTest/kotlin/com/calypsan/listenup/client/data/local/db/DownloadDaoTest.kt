package com.calypsan.listenup.client.data.local.db

import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Regression tests for [DownloadDao] queries added in W8 Phase D.
 *
 * Specifically guards C1: the three new queries must compare against the TEXT value
 * 'WAITING_FOR_SERVER' (as stored by [Converters.fromDownloadState]), not against an
 * integer ordinal. Running against a real in-memory [ListenUpDatabase] ensures Room's
 * generated SQL and the type-converter round-trip are both exercised.
 */
class DownloadDaoTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()
    private val dao: DownloadDao = db.downloadDao()

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun entity(
        audioFileId: String,
        bookId: String = "book-1",
        state: DownloadState = DownloadState.QUEUED,
        startedAt: Long? = null,
        transcodeJobId: String? = null,
    ) = DownloadEntity(
        audioFileId = audioFileId,
        bookId = bookId,
        filename = "$audioFileId.mp3",
        fileIndex = 0,
        state = state,
        localPath = null,
        totalBytes = 1000L,
        downloadedBytes = 0L,
        queuedAt = 0L,
        startedAt = startedAt,
        completedAt = null,
        errorMessage = null,
        retryCount = 0,
        transcodeJobId = transcodeJobId,
    )

    @Test
    fun `getWaitingForServer returns only WAITING_FOR_SERVER rows`() =
        runTest {
            dao.insertAll(
                listOf(
                    entity("file-1", state = DownloadState.WAITING_FOR_SERVER, transcodeJobId = "job-1"),
                    entity("file-2", state = DownloadState.DOWNLOADING),
                    entity("file-3", state = DownloadState.WAITING_FOR_SERVER, transcodeJobId = "job-3"),
                ),
            )

            val waiting = dao.getWaitingForServer()

            assertEquals(2, waiting.size)
            assertEquals(setOf("file-1", "file-3"), waiting.map { it.audioFileId }.toSet())
        }

    @Test
    fun `markWaitingForServer transitions state and persists transcodeJobId`() =
        runTest {
            dao.insert(entity("file-1", state = DownloadState.DOWNLOADING))
            dao.markWaitingForServer("file-1", "job-abc")

            val updated = dao.getByAudioFileId("file-1")
            assertNotNull(updated)
            assertEquals(DownloadState.WAITING_FOR_SERVER, updated.state)
            assertEquals("job-abc", updated.transcodeJobId)
        }

    @Test
    fun `getOldWaitingForServer returns only rows older than threshold`() =
        runTest {
            dao.insertAll(
                listOf(
                    entity("old", state = DownloadState.WAITING_FOR_SERVER, startedAt = 100L, transcodeJobId = "job-old"),
                    entity("recent", state = DownloadState.WAITING_FOR_SERVER, startedAt = 500L, transcodeJobId = "job-recent"),
                    entity("downloading", state = DownloadState.DOWNLOADING, startedAt = 50L),
                ),
            )

            val stale = dao.getOldWaitingForServer(thresholdMs = 200L)

            assertEquals(1, stale.size)
            assertEquals("old", stale.single().audioFileId)
        }
}
