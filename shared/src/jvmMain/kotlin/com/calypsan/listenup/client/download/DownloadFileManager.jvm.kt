package com.calypsan.listenup.client.download

import com.calypsan.listenup.client.data.local.images.JvmStoragePaths
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.io.File

/**
 * JVM desktop implementation of DownloadFileManager.
 *
 * Downloads stored at platform-appropriate location:
 * - Windows: %APPDATA%/ListenUp/audiobooks/{bookId}/
 * - Linux: ~/.local/share/listenup/audiobooks/{bookId}/
 *
 * Uses kotlinx-io for most file operations, with java.io.File only for:
 * - Recursive deletion (no kotlinx-io equivalent)
 * - Storage calculation with walkTopDown (no kotlinx-io equivalent)
 * - Available space query (platform-specific)
 */
actual class DownloadFileManager(
    private val storagePaths: JvmStoragePaths,
) {
    private val downloadDir: Path
        get() {
            val dir = Path(storagePaths.filesDir.toString(), "audiobooks")
            // Ensure directory exists using kotlinx-io
            if (!SystemFileSystem.exists(dir)) {
                SystemFileSystem.createDirectories(dir)
            }
            return dir
        }

    actual fun getDownloadPath(
        bookId: String,
        audioFileId: String,
        filename: String,
    ): Path {
        val bookDir = Path(downloadDir.toString(), bookId)
        // Ensure book directory exists using kotlinx-io
        if (!SystemFileSystem.exists(bookDir)) {
            SystemFileSystem.createDirectories(bookDir)
        }
        return Path(bookDir.toString(), "${audioFileId}_$filename")
    }

    actual fun getTempPath(
        bookId: String,
        audioFileId: String,
        filename: String,
    ): Path {
        val destFile = getDownloadPath(bookId, audioFileId, filename)
        return Path(destFile.parent!!.toString(), "${destFile.name}.tmp")
    }

    actual fun deleteBookFiles(bookId: String) {
        // java.io.File needed for recursive deletion
        val bookDir = File(downloadDir.toString(), bookId)
        if (bookDir.exists()) {
            bookDir.deleteRecursively()
        }
    }

    actual fun deleteAllFiles() {
        // java.io.File needed for recursive deletion
        val dir = File(downloadDir.toString())
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    actual fun calculateStorageUsed(): Long {
        // java.io.File needed for walkTopDown
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

    actual fun fileExists(path: String): Boolean = SystemFileSystem.exists(Path(path))

    actual fun getFileSize(path: String): Long {
        val filePath = Path(path)
        return SystemFileSystem.metadataOrNull(filePath)?.size ?: 0L
    }

    actual fun moveFile(
        source: Path,
        destination: Path,
    ): Boolean =
        try {
            SystemFileSystem.atomicMove(source, destination)
            true
        } catch (_: Exception) {
            false
        }

    actual fun getAvailableSpace(): Long {
        val dir = File(downloadDir.toString())
        return dir.usableSpace
    }
}
