package com.calypsan.listenup.client.download

import android.content.Context
import kotlinx.io.files.Path
import java.io.File

/**
 * Android implementation of DownloadFileManager.
 * Uses Context.filesDir for app-private storage.
 */
actual class DownloadFileManager(
    private val context: Context,
) {
    private val downloadDir: Path
        get() {
            val dir = Path(context.filesDir.absolutePath, "audiobooks")
            // Ensure directory exists
            val file = File(dir.toString())
            if (!file.exists()) {
                file.mkdirs()
            }
            return dir
        }

    actual fun getDownloadPath(
        bookId: String,
        audioFileId: String,
        filename: String,
    ): Path {
        val bookDir = Path(downloadDir, bookId)
        // Ensure book directory exists
        val file = File(bookDir.toString())
        if (!file.exists()) {
            file.mkdirs()
        }
        return Path(bookDir, "${audioFileId}_$filename")
    }

    actual fun getTempPath(
        bookId: String,
        audioFileId: String,
        filename: String,
    ): Path {
        val destFile = getDownloadPath(bookId, audioFileId, filename)
        return Path(destFile.parent!!, "${destFile.name}.tmp")
    }

    actual fun deleteBookFiles(bookId: String) {
        val bookDir = File(downloadDir.toString(), bookId)
        if (bookDir.exists()) {
            bookDir.deleteRecursively()
        }
    }

    actual fun deleteAllFiles() {
        val dir = File(downloadDir.toString())
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    actual fun calculateStorageUsed(): Long {
        val dir = File(downloadDir.toString())
        return if (dir.exists()) {
            dir
                .walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
        } else {
            0L
        }
    }

    actual fun fileExists(path: String): Boolean = File(path).exists()

    actual fun getFileSize(path: String): Long {
        val file = File(path)
        return if (file.exists()) file.length() else 0L
    }

    actual fun moveFile(
        source: Path,
        destination: Path,
    ): Boolean {
        val sourceFile = File(source.toString())
        val destFile = File(destination.toString())
        return sourceFile.renameTo(destFile)
    }

    actual fun getAvailableSpace(): Long = context.filesDir.usableSpace
}
