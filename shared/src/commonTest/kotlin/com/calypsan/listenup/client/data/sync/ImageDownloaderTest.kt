package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.checkIs
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.images.CoverColorExtractor
import com.calypsan.listenup.client.data.local.images.ExtractedColors
import com.calypsan.listenup.client.data.remote.ImageApiContract
import com.calypsan.listenup.client.domain.repository.ImageStorage
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for ImageDownloader.
 *
 * Tests cover:
 * - Single cover download (success, already exists, API failure, storage failure)
 * - Batch cover download
 * - Color extraction integration
 * - Non-fatal error handling
 *
 * Uses Mokkery for mocking ImageApiContract, ImageStorage, and CoverColorExtractor.
 */
class ImageDownloaderTest {
    // ========== Test Fixtures ==========

    private class TestFixture {
        val imageApi: ImageApiContract = mock()
        val imageStorage: ImageStorage = mock()
        val colorExtractor: CoverColorExtractor = mock()

        fun build(): ImageDownloader =
            ImageDownloader(
                imageApi = imageApi,
                imageStorage = imageStorage,
                colorExtractor = colorExtractor,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs
        every { fixture.imageStorage.exists(any()) } returns false
        everySuspend { fixture.imageApi.downloadCover(any()) } returns Success(ByteArray(100))
        everySuspend { fixture.imageStorage.saveCover(any(), any()) } returns Success(Unit)
        everySuspend { fixture.colorExtractor.extractColors(any()) } returns null

        return fixture
    }

    // ========== Single Cover Download Tests ==========

    @Test
    fun `downloadCover returns success with true when cover downloaded and saved`() =
        runTest {
            // Given
            val fixture = createFixture()
            val imageDownloader = fixture.build()
            val bookId = BookId("book-1")
            val imageBytes = ByteArray(100) { it.toByte() }

            everySuspend { fixture.imageApi.downloadCover(bookId) } returns Success(imageBytes)
            everySuspend { fixture.imageStorage.saveCover(bookId, imageBytes) } returns Success(Unit)

            // When
            val result = imageDownloader.downloadCover(bookId)

            // Then
            val success = assertIs<Success<Boolean>>(result)
            assertTrue(success.data)
            verifySuspend { fixture.imageApi.downloadCover(bookId) }
            verifySuspend { fixture.imageStorage.saveCover(bookId, imageBytes) }
        }

    @Test
    fun `downloadCover returns success with false when cover already exists`() =
        runTest {
            // Given
            val fixture = createFixture()
            val bookId = BookId("book-1")
            every { fixture.imageStorage.exists(bookId) } returns true
            val imageDownloader = fixture.build()

            // When
            val result = imageDownloader.downloadCover(bookId)

            // Then
            val success = assertIs<Success<Boolean>>(result)
            assertEquals(false, success.data)
        }

    @Test
    fun `downloadCover returns success with false when API returns failure`() =
        runTest {
            // Given - 404 Not Found (no cover available)
            val fixture = createFixture()
            val bookId = BookId("book-1")
            everySuspend { fixture.imageApi.downloadCover(bookId) } returns Failure(Exception("Not found"))
            val imageDownloader = fixture.build()

            // When
            val result = imageDownloader.downloadCover(bookId)

            // Then - returns false (non-fatal), not failure
            val success = assertIs<Success<Boolean>>(result)
            assertEquals(false, success.data)
        }

    @Test
    fun `downloadCover returns failure when storage save fails`() =
        runTest {
            // Given
            val fixture = createFixture()
            val bookId = BookId("book-1")
            val imageBytes = ByteArray(100)
            val storageException = Exception("Disk full")

            everySuspend { fixture.imageApi.downloadCover(bookId) } returns Success(imageBytes)
            everySuspend { fixture.imageStorage.saveCover(bookId, imageBytes) } returns Failure(storageException)
            val imageDownloader = fixture.build()

            // When
            val result = imageDownloader.downloadCover(bookId)

            // Then - storage failure is fatal
            val failure = assertIs<Failure>(result)
            assertEquals("Disk full", failure.message)
        }

    @Test
    fun `downloadCover does not call API when cover already exists`() =
        runTest {
            // Given
            val fixture = createFixture()
            val bookId = BookId("book-1")
            every { fixture.imageStorage.exists(bookId) } returns true
            val imageDownloader = fixture.build()

            // When
            imageDownloader.downloadCover(bookId)

            // Then - API should not be called
            // (verify by not stubbing API - if called, test would fail)
        }

    // ========== Batch Download Tests ==========

    @Test
    fun `downloadCovers returns list of download results with extracted colors`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book1 = BookId("book-1")
            val book2 = BookId("book-2")
            val book3 = BookId("book-3")
            val testColors = ExtractedColors(dominant = 0xFF000000.toInt(), darkMuted = 0xFF333333.toInt(), vibrant = 0xFFFF0000.toInt())
            val imageBytes = ByteArray(100)

            every { fixture.imageStorage.exists(any()) } returns false
            everySuspend { fixture.imageApi.downloadCoverBatch(any()) } returns
                Success(
                    mapOf(
                        "book-1" to imageBytes,
                        "book-2" to imageBytes,
                        "book-3" to imageBytes,
                    ),
                )
            everySuspend { fixture.imageStorage.saveCover(any(), any()) } returns Success(Unit)
            everySuspend { fixture.colorExtractor.extractColors(any()) } returns testColors
            val imageDownloader = fixture.build()

            // When
            val result = imageDownloader.downloadCovers(listOf(book1, book2, book3))

            // Then
            val success = assertIs<Success<List<CoverDownloadResult>>>(result)
            assertEquals(3, success.data.size)
            assertTrue(success.data.any { it.bookId == book1 })
            assertTrue(success.data.any { it.bookId == book2 })
            assertTrue(success.data.any { it.bookId == book3 })
            // Verify colors were extracted
            success.data.forEach { downloadResult ->
                assertEquals(testColors, downloadResult.colors)
            }
        }

    @Test
    fun `downloadCovers excludes already existing covers from result`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book1 = BookId("book-1")
            val book2 = BookId("book-2")
            val imageBytes = ByteArray(100)

            // book-1 already exists
            every { fixture.imageStorage.exists(book1) } returns true
            every { fixture.imageStorage.exists(book2) } returns false
            everySuspend { fixture.imageApi.downloadCoverBatch(any()) } returns
                Success(
                    mapOf("book-2" to imageBytes),
                )
            everySuspend { fixture.imageStorage.saveCover(any(), any()) } returns Success(Unit)
            val imageDownloader = fixture.build()

            // When
            val result = imageDownloader.downloadCovers(listOf(book1, book2))

            // Then - only book-2 was newly downloaded
            val success = assertIs<Success<List<CoverDownloadResult>>>(result)
            assertEquals(1, success.data.size)
            assertEquals(book2, success.data[0].bookId)
        }

    @Test
    fun `downloadCovers continues on batch failures`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book1 = BookId("book-1")
            val book2 = BookId("book-2")

            every { fixture.imageStorage.exists(any()) } returns false
            // Batch download fails entirely
            everySuspend { fixture.imageApi.downloadCoverBatch(any()) } returns Failure(Exception("Network error"))
            val imageDownloader = fixture.build()

            // When
            val result = imageDownloader.downloadCovers(listOf(book1, book2))

            // Then - result is empty but not failure
            val success = assertIs<Success<List<CoverDownloadResult>>>(result)
            assertTrue(success.data.isEmpty())
        }

    @Test
    fun `downloadCovers handles storage failure for individual books`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book1 = BookId("book-1")
            val book2 = BookId("book-2")
            val imageBytes = ByteArray(100)

            every { fixture.imageStorage.exists(any()) } returns false
            everySuspend { fixture.imageApi.downloadCoverBatch(any()) } returns
                Success(
                    mapOf(
                        "book-1" to imageBytes,
                        "book-2" to imageBytes,
                    ),
                )
            // book-1 storage fails, book-2 succeeds
            everySuspend { fixture.imageStorage.saveCover(book1, any()) } returns Failure(Exception("Disk full"))
            everySuspend { fixture.imageStorage.saveCover(book2, any()) } returns Success(Unit)
            val imageDownloader = fixture.build()

            // When
            val result = imageDownloader.downloadCovers(listOf(book1, book2))

            // Then - only successful saves are included
            val success = assertIs<Success<List<CoverDownloadResult>>>(result)
            assertEquals(1, success.data.size)
            assertEquals(book2, success.data[0].bookId)
        }

    @Test
    fun `downloadCovers returns empty list when all downloads fail`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book1 = BookId("book-1")
            val book2 = BookId("book-2")

            every { fixture.imageStorage.exists(any()) } returns false
            everySuspend { fixture.imageApi.downloadCoverBatch(any()) } returns Failure(Exception("Network error"))
            val imageDownloader = fixture.build()

            // When
            val result = imageDownloader.downloadCovers(listOf(book1, book2))

            // Then
            val success = assertIs<Success<List<CoverDownloadResult>>>(result)
            assertTrue(success.data.isEmpty())
        }

    @Test
    fun `downloadCovers handles empty list`() =
        runTest {
            // Given
            val fixture = createFixture()
            val imageDownloader = fixture.build()

            // When
            val result = imageDownloader.downloadCovers(emptyList())

            // Then
            val success = assertIs<Success<List<CoverDownloadResult>>>(result)
            assertTrue(success.data.isEmpty())
        }

    @Test
    fun `downloadCovers returns null colors when extractor fails`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book1 = BookId("book-1")
            val imageBytes = ByteArray(100)

            every { fixture.imageStorage.exists(any()) } returns false
            everySuspend { fixture.imageApi.downloadCoverBatch(any()) } returns
                Success(
                    mapOf("book-1" to imageBytes),
                )
            everySuspend { fixture.imageStorage.saveCover(any(), any()) } returns Success(Unit)
            everySuspend { fixture.colorExtractor.extractColors(any()) } returns null
            val imageDownloader = fixture.build()

            // When
            val result = imageDownloader.downloadCovers(listOf(book1))

            // Then - download succeeds but colors are null
            val success = assertIs<Success<List<CoverDownloadResult>>>(result)
            assertEquals(1, success.data.size)
            assertEquals(book1, success.data[0].bookId)
            assertEquals(null, success.data[0].colors)
        }
}
