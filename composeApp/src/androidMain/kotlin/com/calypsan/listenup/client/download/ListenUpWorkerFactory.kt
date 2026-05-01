package com.calypsan.listenup.client.download

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.calypsan.listenup.client.data.remote.ABSImportApiContract
import com.calypsan.listenup.client.domain.repository.DownloadRepository
import com.calypsan.listenup.client.data.remote.BackupApiContract
import com.calypsan.listenup.client.data.remote.PlaybackApiContract
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import com.calypsan.listenup.client.playback.AudioCapabilityDetector
import com.calypsan.listenup.client.upload.ABSUploadWorker
import io.ktor.client.HttpClient

/**
 * WorkerFactory for injecting dependencies into all ListenUp workers.
 *
 * Handles creation of:
 * - [DownloadWorker] — offline audiobook downloads
 * - [ABSUploadWorker] — Audiobookshelf backup upload and import
 */
class ListenUpWorkerFactory(
    private val downloadRepository: DownloadRepository,
    private val fileManager: DownloadFileManager,
    private val httpClient: HttpClient,
    private val playbackPreferences: PlaybackPreferences,
    private val playbackApi: PlaybackApiContract,
    private val capabilityDetector: AudioCapabilityDetector,
    private val backupApi: BackupApiContract,
    private val absImportApi: ABSImportApiContract,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        when (workerClassName) {
            DownloadWorker::class.java.name -> {
                DownloadWorker(
                    appContext,
                    workerParameters,
                    downloadRepository,
                    fileManager,
                    httpClient,
                    playbackPreferences,
                    playbackApi,
                    capabilityDetector,
                )
            }

            ABSUploadWorker::class.java.name -> {
                ABSUploadWorker(
                    appContext,
                    workerParameters,
                    backupApi,
                    absImportApi,
                )
            }

            else -> {
                null
            }
        }
}
