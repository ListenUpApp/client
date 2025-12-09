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
 */
class AndroidImageStorage(
    private val context: Context
) : ImageStorage {

    private val coversDir: File by lazy {
        File(context.filesDir, COVERS_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    override suspend fun saveCover(bookId: BookId, imageData: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = getCoverFile(bookId)
            file.writeBytes(imageData)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(IOException("Failed to save cover for book ${bookId.value}", e))
        }
    }

    override fun getCoverPath(bookId: BookId): String {
        return getCoverFile(bookId).absolutePath
    }

    override fun exists(bookId: BookId): Boolean {
        return getCoverFile(bookId).exists()
    }

    override suspend fun deleteCover(bookId: BookId): Result<Unit> = withContext(Dispatchers.IO) {
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

    override suspend fun clearAll(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val files = coversDir.listFiles() ?: emptyArray()
            var deletedCount = 0
            files.forEach { file ->
                if (file.isFile && file.extension == FILE_EXTENSION) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
            Result.Success(deletedCount)
        } catch (e: Exception) {
            Result.Failure(IOException("Failed to clear cover cache", e))
        }
    }

    private fun getCoverFile(bookId: BookId): File {
        return File(coversDir, "${bookId.value}.$FILE_EXTENSION")
    }

    companion object {
        private const val COVERS_DIR_NAME = "covers"
        private const val FILE_EXTENSION = "jpg"
    }
}
