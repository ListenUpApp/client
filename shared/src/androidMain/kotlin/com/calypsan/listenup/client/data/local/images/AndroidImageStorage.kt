package com.calypsan.listenup.client.data.local.images

import android.content.Context
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Android implementation of [ImageStorage] using app-private file storage.
 * Stores cover images in {filesDir}/covers/{bookId}.jpg
 * Stores contributor images in {filesDir}/contributors/{contributorId}.jpg
 * Stores series cover images in {filesDir}/covers/series/{seriesId}.jpg
 */
class AndroidImageStorage(
    private val context: Context,
) : ImageStorage {
    private val coversDir: File by lazy {
        File(context.filesDir, COVERS_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private val contributorsDir: File by lazy {
        File(context.filesDir, CONTRIBUTORS_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private val seriesCoversDir: File by lazy {
        File(context.filesDir, SERIES_COVERS_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    // ========== Book Cover Methods ==========

    override suspend fun saveCover(
        bookId: BookId,
        imageData: ByteArray,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getCoverFile(bookId)
                file.writeBytes(imageData)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to save cover for book ${bookId.value}", e))
            }
        }

    override fun getCoverPath(bookId: BookId): String = getCoverFile(bookId).absolutePath

    override fun exists(bookId: BookId): Boolean = getCoverFile(bookId).exists()

    override suspend fun deleteCover(bookId: BookId): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getCoverFile(bookId)
                if (file.exists()) {
                    file.delete()
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to delete cover for book ${bookId.value}", e))
            }
        }

    // ========== Book Cover Staging Methods ==========

    override suspend fun saveCoverStaging(
        bookId: BookId,
        imageData: ByteArray,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getCoverStagingFile(bookId)
                file.writeBytes(imageData)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to save staging cover for book ${bookId.value}", e))
            }
        }

    override fun getCoverStagingPath(bookId: BookId): String = getCoverStagingFile(bookId).absolutePath

    override suspend fun commitCoverStaging(bookId: BookId): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val stagingFile = getCoverStagingFile(bookId)
                val targetFile = getCoverFile(bookId)

                if (!stagingFile.exists()) {
                    return@withContext Result.Failure(
                        IOException("No staging cover to commit for book ${bookId.value}"),
                    )
                }

                // Copy staging to target, then delete staging
                stagingFile.copyTo(targetFile, overwrite = true)
                stagingFile.delete()

                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to commit staging cover for book ${bookId.value}", e))
            }
        }

    override suspend fun deleteCoverStaging(bookId: BookId): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getCoverStagingFile(bookId)
                if (file.exists()) {
                    file.delete()
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to delete staging cover for book ${bookId.value}", e))
            }
        }

    override suspend fun clearAll(): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                var deletedCount = 0
                // Clear covers
                coversDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension == FILE_EXTENSION && file.delete()) {
                        deletedCount++
                    }
                }
                // Clear contributor images
                contributorsDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension == FILE_EXTENSION && file.delete()) {
                        deletedCount++
                    }
                }
                // Clear series covers
                seriesCoversDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension == FILE_EXTENSION && file.delete()) {
                        deletedCount++
                    }
                }
                Result.Success(deletedCount)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to clear image cache", e))
            }
        }

    // ========== Contributor Image Methods ==========

    override suspend fun saveContributorImage(
        contributorId: String,
        imageData: ByteArray,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getContributorFile(contributorId)
                file.writeBytes(imageData)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to save image for contributor $contributorId", e))
            }
        }

    override fun getContributorImagePath(contributorId: String): String = getContributorFile(contributorId).absolutePath

    override fun contributorImageExists(contributorId: String): Boolean = getContributorFile(contributorId).exists()

    override suspend fun deleteContributorImage(contributorId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getContributorFile(contributorId)
                if (file.exists()) {
                    file.delete()
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to delete image for contributor $contributorId", e))
            }
        }

    // ========== Series Cover Methods ==========

    override suspend fun saveSeriesCover(
        seriesId: String,
        imageData: ByteArray,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getSeriesCoverFile(seriesId)
                file.writeBytes(imageData)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to save cover for series $seriesId", e))
            }
        }

    override fun getSeriesCoverPath(seriesId: String): String = getSeriesCoverFile(seriesId).absolutePath

    override fun seriesCoverExists(seriesId: String): Boolean = getSeriesCoverFile(seriesId).exists()

    override suspend fun deleteSeriesCover(seriesId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getSeriesCoverFile(seriesId)
                if (file.exists()) {
                    file.delete()
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to delete cover for series $seriesId", e))
            }
        }

    // ========== Series Cover Staging Methods ==========

    override suspend fun saveSeriesCoverStaging(
        seriesId: String,
        imageData: ByteArray,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getSeriesCoverStagingFile(seriesId)
                file.writeBytes(imageData)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to save staging cover for series $seriesId", e))
            }
        }

    override fun getSeriesCoverStagingPath(seriesId: String): String = getSeriesCoverStagingFile(seriesId).absolutePath

    override suspend fun commitSeriesCoverStaging(seriesId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val stagingFile = getSeriesCoverStagingFile(seriesId)
                val targetFile = getSeriesCoverFile(seriesId)

                if (!stagingFile.exists()) {
                    return@withContext Result.Failure(
                        IOException("No staging cover to commit for series $seriesId"),
                    )
                }

                // Copy staging to target, then delete staging
                stagingFile.copyTo(targetFile, overwrite = true)
                stagingFile.delete()

                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to commit staging cover for series $seriesId", e))
            }
        }

    override suspend fun deleteSeriesCoverStaging(seriesId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getSeriesCoverStagingFile(seriesId)
                if (file.exists()) {
                    file.delete()
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to delete staging cover for series $seriesId", e))
            }
        }

    // ========== Private Helpers ==========

    private fun getCoverFile(bookId: BookId): File = File(coversDir, "${bookId.value}.$FILE_EXTENSION")

    private fun getCoverStagingFile(bookId: BookId): File = File(coversDir, "${bookId.value}_staging.$FILE_EXTENSION")

    private fun getContributorFile(contributorId: String): File =
        File(contributorsDir, "$contributorId.$FILE_EXTENSION")

    private fun getSeriesCoverFile(seriesId: String): File = File(seriesCoversDir, "$seriesId.$FILE_EXTENSION")

    private fun getSeriesCoverStagingFile(seriesId: String): File =
        File(seriesCoversDir, "${seriesId}_staging.$FILE_EXTENSION")

    companion object {
        private const val COVERS_DIR_NAME = "covers"
        private const val CONTRIBUTORS_DIR_NAME = "contributors"
        private const val SERIES_COVERS_DIR_NAME = "covers/series"
        private const val FILE_EXTENSION = "jpg"
    }
}
