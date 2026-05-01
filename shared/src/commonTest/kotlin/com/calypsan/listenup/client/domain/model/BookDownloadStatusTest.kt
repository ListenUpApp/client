package com.calypsan.listenup.client.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookDownloadStatusTest {
    @Test
    fun `NotDownloaded carries only the bookId`() {
        val status = BookDownloadStatus.NotDownloaded(bookId = "book-1")
        assertEquals("book-1", status.bookId)
    }

    @Test
    fun `InProgress progress computes from downloadedBytes and totalBytes`() {
        val status =
            BookDownloadStatus.InProgress(
                bookId = "book-1",
                totalFiles = 4,
                downloadingFiles = 1,
                waitingForServerFiles = 0,
                completedFiles = 2,
                totalBytes = 1000L,
                downloadedBytes = 250L,
            )
        assertEquals(0.25f, status.progress)
    }

    @Test
    fun `InProgress progress is zero when totalBytes is zero`() {
        val status =
            BookDownloadStatus.InProgress(
                bookId = "book-1",
                totalFiles = 1,
                downloadingFiles = 1,
                waitingForServerFiles = 0,
                completedFiles = 0,
                totalBytes = 0L,
                downloadedBytes = 0L,
            )
        assertEquals(0f, status.progress)
    }

    @Test
    fun `Completed carries totalBytes`() {
        val status = BookDownloadStatus.Completed(bookId = "book-1", totalBytes = 5000L)
        assertEquals("book-1", status.bookId)
        assertEquals(5000L, status.totalBytes)
    }

    @Test
    fun `Failed carries error message and partiallyDownloadedFiles count`() {
        val status =
            BookDownloadStatus.Failed(
                bookId = "book-1",
                errorMessage = "Network unreachable",
                partiallyDownloadedFiles = 2,
            )
        assertEquals("Network unreachable", status.errorMessage)
        assertEquals(2, status.partiallyDownloadedFiles)
    }

    @Test
    fun `Paused carries paused file count and progress numbers`() {
        val status =
            BookDownloadStatus.Paused(
                bookId = "book-1",
                pausedFiles = 3,
                downloadedBytes = 100L,
                totalBytes = 500L,
            )
        assertEquals(3, status.pausedFiles)
        assertEquals(100L, status.downloadedBytes)
        assertEquals(500L, status.totalBytes)
    }

    @Test
    fun `sealed hierarchy enables exhaustive when matching`() {
        val status: BookDownloadStatus = BookDownloadStatus.NotDownloaded("book-1")
        val description: String =
            when (status) {
                is BookDownloadStatus.NotDownloaded -> "not downloaded"
                is BookDownloadStatus.InProgress -> "in progress"
                is BookDownloadStatus.Completed -> "completed"
                is BookDownloadStatus.Failed -> "failed"
                is BookDownloadStatus.Paused -> "paused"
            }
        assertTrue(description.isNotEmpty())
    }
}
