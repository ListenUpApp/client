package com.calypsan.listenup.client.download

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.remote.ABSImportApiContract
import com.calypsan.listenup.client.data.remote.BackupApiContract
import com.calypsan.listenup.client.data.remote.PlaybackApi
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import com.calypsan.listenup.client.domain.repository.ServerConfig
import com.calypsan.listenup.client.playback.AudioCapabilityDetector
import com.calypsan.listenup.client.playback.AudioTokenProvider
import com.calypsan.listenup.client.upload.ABSUploadWorker

/**
 * WorkerFactory for injecting dependencies into all ListenUp workers.
 *
 * Handles creation of:
 * - [DownloadWorker] — offline audiobook downloads
 * - [ABSUploadWorker] — Audiobookshelf backup upload and import
 */
class ListenUpWorkerFactory(
    private val downloadDao: DownloadDao,
    private val fileManager: DownloadFileManager,
    private val tokenProvider: AudioTokenProvider,
    private val serverConfig: ServerConfig,
    private val playbackPreferences: PlaybackPreferences,
    private val playbackApi: PlaybackApi,
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
                    downloadDao,
                    fileManager,
                    tokenProvider,
                    serverConfig,
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
