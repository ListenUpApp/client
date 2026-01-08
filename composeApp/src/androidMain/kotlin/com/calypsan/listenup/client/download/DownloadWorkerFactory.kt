package com.calypsan.listenup.client.download

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.remote.PlaybackApi
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import com.calypsan.listenup.client.domain.repository.ServerConfig
import com.calypsan.listenup.client.playback.AudioCapabilityDetector
import com.calypsan.listenup.client.playback.AudioTokenProvider

/**
 * WorkerFactory for injecting dependencies into DownloadWorker.
 */
class DownloadWorkerFactory(
    private val downloadDao: DownloadDao,
    private val fileManager: DownloadFileManager,
    private val tokenProvider: AudioTokenProvider,
    private val serverConfig: ServerConfig,
    private val playbackPreferences: PlaybackPreferences,
    private val playbackApi: PlaybackApi,
    private val capabilityDetector: AudioCapabilityDetector,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == DownloadWorker::class.java.name) {
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
        } else {
            null
        }
}
