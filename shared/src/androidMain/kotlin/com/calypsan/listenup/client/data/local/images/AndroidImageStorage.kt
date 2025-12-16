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

    // ========== Private Helpers ==========

    private fun getCoverFile(bookId: BookId): File = File(coversDir, "${bookId.value}.$FILE_EXTENSION")

    private fun getCoverStagingFile(bookId: BookId): File = File(coversDir, "${bookId.value}_staging.$FILE_EXTENSION")

    private fun getContributorFile(contributorId: String): File =
        File(contributorsDir, "$contributorId.$FILE_EXTENSION")

    companion object {
        private const val COVERS_DIR_NAME = "covers"
        private const val CONTRIBUTORS_DIR_NAME = "contributors"
        private const val FILE_EXTENSION = "jpg"
    }
}
