package com.calypsan.listenup.client.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.remote.ABSImportApiContract
import com.calypsan.listenup.client.data.remote.BackupApiContract
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.util.UUID

private val logger = KotlinLogging.logger {}

private const val CHANNEL_ID = "abs_upload"
private const val CHANNEL_NAME = "ABS Import Upload"
private const val NOTIFICATION_ID = 9001
private const val MAX_RUN_ATTEMPTS = 3

/**
 * Background worker that uploads an Audiobookshelf backup and creates an import.
 *
 * Runs as a foreground service with [FOREGROUND_SERVICE_TYPE_DATA_SYNC][ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC]
 * so the upload survives app backgrounding.
 *
 * Input data:
 * - [KEY_CACHE_FILE_PATH] — absolute path to the cached backup file
 * - [KEY_FILENAME] — original display filename
 * - [KEY_FILE_SIZE] — file size in bytes
 *
 * Output data:
 * - [KEY_IMPORT_ID] — the created import ID on success
 */
class ABSUploadWorker(
    context: Context,
    params: WorkerParameters,
    private val backupApi: BackupApiContract,
    private val absImportApi: ABSImportApiContract,
) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_CACHE_FILE_PATH = "cache_file_path"
        const val KEY_FILENAME = "filename"
        const val KEY_FILE_SIZE = "file_size"
        const val KEY_IMPORT_ID = "import_id"
        const val KEY_ERROR = "error"

        /**
         * Enqueue an upload work request.
         *
         * @param context Android context
         * @param cacheFilePath Absolute path to the cached backup file
         * @param filename Original display filename
         * @param fileSize File size in bytes
         * @return The work request UUID for observation
         */
        fun enqueue(
            context: Context,
            cacheFilePath: String,
            filename: String,
            fileSize: Long,
        ): UUID {
            val data =
                workDataOf(
                    KEY_CACHE_FILE_PATH to cacheFilePath,
                    KEY_FILENAME to filename,
                    KEY_FILE_SIZE to fileSize,
                )

            val request =
                OneTimeWorkRequestBuilder<ABSUploadWorker>()
                    .setInputData(data)
                    .build()

            WorkManager.getInstance(context).enqueue(request)
            return request.id
        }
    }

    override suspend fun doWork(): Result {
        val cacheFilePath =
            inputData.getString(KEY_CACHE_FILE_PATH)
                ?: return Result.failure(workDataOf(KEY_ERROR to "Missing cache file path"))
        val filename =
            inputData.getString(KEY_FILENAME)
                ?: return Result.failure(workDataOf(KEY_ERROR to "Missing filename"))

        val cacheFile = File(cacheFilePath)

        return try {
            setForeground(createForegroundInfo())

            if (!cacheFile.exists()) {
                logger.error { "Cache file does not exist: $cacheFilePath" }
                return Result.failure(workDataOf(KEY_ERROR to "Cache file not found"))
            }

            logger.info { "Starting ABS upload: filename=$filename, size=${cacheFile.length()}" }

            // Upload the backup file
            val fileSource = CachedFileSource(cacheFile, filename)
            val uploadResponse = backupApi.uploadABSBackup(fileSource)
            logger.info { "Upload complete, server path: ${uploadResponse.path}" }

            // Create the import from the uploaded file
            when (val importResult = absImportApi.createImportFromPath(uploadResponse.path, filename)) {
                is Success -> {
                    val importId = importResult.data.id
                    logger.info { "Import created: id=$importId" }
                    Result.success(workDataOf(KEY_IMPORT_ID to importId))
                }

                is Failure -> {
                    val error = importResult.exception?.message ?: "Failed to create import"
                    logger.error { "Import creation failed: $error" }
                    retryOrFail(error)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "ABS upload failed" }
            retryOrFail(e.message ?: "Upload failed")
        } finally {
            // Clean up cache file
            if (cacheFile.exists()) {
                val deleted = cacheFile.delete()
                logger.debug { "Cache file cleanup: deleted=$deleted, path=$cacheFilePath" }
            }
        }
    }

    private fun retryOrFail(errorMessage: String): Result =
        if (runAttemptCount < MAX_RUN_ATTEMPTS) {
            logger.info { "Retrying upload (attempt ${runAttemptCount + 1}/$MAX_RUN_ATTEMPTS)" }
            Result.retry()
        } else {
            Result.failure(workDataOf(KEY_ERROR to errorMessage))
        }

    private fun createForegroundInfo(): ForegroundInfo {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel (required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                )
            notificationManager.createNotificationChannel(channel)
        }

        val notification =
            NotificationCompat
                .Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("Uploading ABS Backup")
                .setContentText("Importing Audiobookshelf backup…")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setProgress(0, 0, true)
                .setOngoing(true)
                .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
