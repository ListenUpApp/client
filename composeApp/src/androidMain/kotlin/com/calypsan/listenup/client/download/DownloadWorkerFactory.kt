package com.calypsan.listenup.client.download

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.playback.AudioTokenProvider

/**
 * WorkerFactory for injecting dependencies into DownloadWorker.
 */
class DownloadWorkerFactory(
    private val downloadDao: DownloadDao,
    private val fileManager: DownloadFileManager,
    private val tokenProvider: AudioTokenProvider,
    private val settingsRepository: SettingsRepository
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            DownloadWorker::class.java.name -> DownloadWorker(
                appContext,
                workerParameters,
                downloadDao,
                fileManager,
                tokenProvider,
                settingsRepository
            )
            else -> null
        }
    }
}
