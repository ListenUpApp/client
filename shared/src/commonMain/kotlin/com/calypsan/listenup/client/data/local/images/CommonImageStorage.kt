package com.calypsan.listenup.client.data.local.images

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write

/**
 * Shared implementation of [ImageStorage] using kotlinx-io.
 *
 * This implementation works across all platforms (Android, iOS, Desktop).
 * Platform-specific code only needs to provide [StoragePaths] with the base directory.
 *
 * Storage structure:
 * - {filesDir}/covers/{bookId}.jpg - Book cover images
 * - {filesDir}/covers/{bookId}_staging.jpg - Staging covers for edit preview
 * - {filesDir}/contributors/{contributorId}.jpg - Contributor profile images
 * - {filesDir}/covers/series/{seriesId}.jpg - Series cover images
 * - {filesDir}/covers/series/{seriesId}_staging.jpg - Staging series covers
 * - {filesDir}/avatars/{userId}.jpg - User profile avatar images
 */
class CommonImageStorage(
    storagePaths: StoragePaths,
) : ImageStorage {
    private val filesDir: Path = storagePaths.filesDir
    private val coversDir: Path = Path(filesDir.toString(), COVERS_DIR_NAME)
    private val contributorsDir: Path = Path(filesDir.toString(), CONTRIBUTORS_DIR_NAME)
    private val seriesCoversDir: Path = Path(filesDir.toString(), SERIES_COVERS_DIR_NAME)
    private val avatarsDir: Path = Path(filesDir.toString(), AVATARS_DIR_NAME)

    // ========== Book Cover Methods ==========

    override suspend fun saveCover(
        bookId: BookId,
        imageData: ByteArray,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getCoverFile(bookId)
                writeBytes(file, imageData)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to save cover for book ${bookId.value}", e))
            }
        }

    override fun getCoverPath(bookId: BookId): String = getCoverFile(bookId).toString()

    override fun exists(bookId: BookId): Boolean = SystemFileSystem.exists(getCoverFile(bookId))

    override suspend fun deleteCover(bookId: BookId): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getCoverFile(bookId)
                deleteIfExists(file)
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
                writeBytes(file, imageData)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to save staging cover for book ${bookId.value}", e))
            }
        }

    override fun getCoverStagingPath(bookId: BookId): String = getCoverStagingFile(bookId).toString()

    override suspend fun commitCoverStaging(bookId: BookId): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val stagingFile = getCoverStagingFile(bookId)
                val targetFile = getCoverFile(bookId)

                if (!SystemFileSystem.exists(stagingFile)) {
                    return@withContext Result.Failure(
                        IOException("No staging cover to commit for book ${bookId.value}"),
                    )
                }

                // Read staging, write to target, delete staging
                val data = readBytes(stagingFile)
                writeBytes(targetFile, data)
                SystemFileSystem.delete(stagingFile)

                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to commit staging cover for book ${bookId.value}", e))
            }
        }

    override suspend fun deleteCoverStaging(bookId: BookId): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                deleteIfExists(getCoverStagingFile(bookId))
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
                deletedCount += clearDirectory(coversDir)
                // Clear contributor images
                deletedCount += clearDirectory(contributorsDir)
                // Clear series covers
                deletedCount += clearDirectory(seriesCoversDir)
                // Clear user avatars
                deletedCount += clearDirectory(avatarsDir)

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
                writeBytes(file, imageData)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to save image for contributor $contributorId", e))
            }
        }

    override fun getContributorImagePath(contributorId: String): String = getContributorFile(contributorId).toString()

    override fun contributorImageExists(contributorId: String): Boolean =
        SystemFileSystem.exists(getContributorFile(contributorId))

    override suspend fun deleteContributorImage(contributorId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                deleteIfExists(getContributorFile(contributorId))
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
                writeBytes(file, imageData)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to save cover for series $seriesId", e))
            }
        }

    override fun getSeriesCoverPath(seriesId: String): String = getSeriesCoverFile(seriesId).toString()

    override fun seriesCoverExists(seriesId: String): Boolean = SystemFileSystem.exists(getSeriesCoverFile(seriesId))

    override suspend fun deleteSeriesCover(seriesId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                deleteIfExists(getSeriesCoverFile(seriesId))
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
                writeBytes(file, imageData)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to save staging cover for series $seriesId", e))
            }
        }

    override fun getSeriesCoverStagingPath(seriesId: String): String = getSeriesCoverStagingFile(seriesId).toString()

    override suspend fun commitSeriesCoverStaging(seriesId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val stagingFile = getSeriesCoverStagingFile(seriesId)
                val targetFile = getSeriesCoverFile(seriesId)

                if (!SystemFileSystem.exists(stagingFile)) {
                    return@withContext Result.Failure(
                        IOException("No staging cover to commit for series $seriesId"),
                    )
                }

                // Read staging, write to target, delete staging
                val data = readBytes(stagingFile)
                writeBytes(targetFile, data)
                SystemFileSystem.delete(stagingFile)

                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to commit staging cover for series $seriesId", e))
            }
        }

    override suspend fun deleteSeriesCoverStaging(seriesId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                deleteIfExists(getSeriesCoverStagingFile(seriesId))
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to delete staging cover for series $seriesId", e))
            }
        }

    // ========== User Avatar Methods ==========

    override suspend fun saveUserAvatar(
        userId: String,
        imageData: ByteArray,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = getUserAvatarFile(userId)
                writeBytes(file, imageData)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to save avatar for user $userId", e))
            }
        }

    override fun getUserAvatarPath(userId: String): String = getUserAvatarFile(userId).toString()

    override fun userAvatarExists(userId: String): Boolean = SystemFileSystem.exists(getUserAvatarFile(userId))

    override suspend fun deleteUserAvatar(userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                deleteIfExists(getUserAvatarFile(userId))
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(IOException("Failed to delete avatar for user $userId", e))
            }
        }

    // ========== Private Helpers ==========

    private fun getCoverFile(bookId: BookId): Path = Path(coversDir.toString(), "${bookId.value}.$FILE_EXTENSION")

    private fun getCoverStagingFile(bookId: BookId): Path =
        Path(coversDir.toString(), "${bookId.value}_staging.$FILE_EXTENSION")

    private fun getContributorFile(contributorId: String): Path =
        Path(contributorsDir.toString(), "$contributorId.$FILE_EXTENSION")

    private fun getSeriesCoverFile(seriesId: String): Path =
        Path(seriesCoversDir.toString(), "$seriesId.$FILE_EXTENSION")

    private fun getSeriesCoverStagingFile(seriesId: String): Path =
        Path(seriesCoversDir.toString(), "${seriesId}_staging.$FILE_EXTENSION")

    private fun getUserAvatarFile(userId: String): Path = Path(avatarsDir.toString(), "$userId.$FILE_EXTENSION")

    /**
     * Write bytes to a file, creating parent directories if needed.
     */
    private fun writeBytes(
        path: Path,
        data: ByteArray,
    ) {
        // Ensure parent directory exists
        val parent = path.parent
        if (parent != null && !SystemFileSystem.exists(parent)) {
            SystemFileSystem.createDirectories(parent)
        }
        SystemFileSystem.sink(path).buffered().use { sink ->
            sink.write(data)
        }
    }

    /**
     * Read all bytes from a file.
     */
    private fun readBytes(path: Path): ByteArray =
        SystemFileSystem.source(path).buffered().use { source ->
            source.readByteArray()
        }

    /**
     * Delete a file if it exists.
     */
    private fun deleteIfExists(path: Path) {
        if (SystemFileSystem.exists(path)) {
            SystemFileSystem.delete(path)
        }
    }

    /**
     * Delete all .jpg files in a directory.
     */
    private fun clearDirectory(dir: Path): Int {
        if (!SystemFileSystem.exists(dir)) return 0

        var count = 0
        SystemFileSystem.list(dir).forEach { path ->
            if (path.name.endsWith(".$FILE_EXTENSION")) {
                SystemFileSystem.delete(path)
                count++
            }
        }
        return count
    }

    companion object {
        private const val COVERS_DIR_NAME = "covers"
        private const val CONTRIBUTORS_DIR_NAME = "contributors"
        private const val SERIES_COVERS_DIR_NAME = "covers/series"
        private const val AVATARS_DIR_NAME = "avatars"
        private const val FILE_EXTENSION = "jpg"
    }
}
