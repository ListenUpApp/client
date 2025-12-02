package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.download.DownloadFileManager
import com.calypsan.listenup.client.download.DownloadService
import com.calypsan.listenup.client.download.IosDownloadService
import com.calypsan.listenup.client.playback.AudioTokenProvider
import com.calypsan.listenup.client.playback.IosAudioTokenProvider
import com.calypsan.listenup.client.playback.PlaybackManager
import com.calypsan.listenup.client.playback.ProgressTracker
import com.calypsan.listenup.client.playback.SleepTimerManager
import com.calypsan.listenup.client.sync.BackgroundSyncScheduler
import com.calypsan.listenup.client.sync.IosBackgroundSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * iOS playback module.
 *
 * Provides audio playback components for iOS:
 * - AudioTokenProvider for authenticated streaming
 * - DownloadService for offline downloads
 * - PlaybackManager for playback orchestration
 * - ProgressTracker for position persistence
 * - SleepTimerManager for sleep timer functionality
 */
val iosPlaybackModule: Module = module {
    // Playback-scoped coroutine scope
    single(qualifier = named("playbackScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    // Device ID for listening events
    // iOS uses identifierForVendor which persists across app reinstalls
    // but changes when all apps from vendor are deleted
    single(qualifier = named("deviceId")) {
        platform.UIKit.UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "unknown-device"
    }

    // File manager for downloads
    single { DownloadFileManager() }

    // Audio token provider
    single<AudioTokenProvider> {
        IosAudioTokenProvider(
            settingsRepository = get(),
            authApi = get(),
            scope = get(qualifier = named("playbackScope"))
        )
    }

    // Also expose concrete type for iOS-specific features
    single { get<AudioTokenProvider>() as IosAudioTokenProvider }

    // Download service
    single<DownloadService> {
        IosDownloadService(
            downloadDao = get(),
            bookDao = get(),
            settingsRepository = get(),
            tokenProvider = get(),
            fileManager = get(),
            scope = get(qualifier = named("playbackScope"))
        )
    }

    // Progress tracker
    single {
        ProgressTracker(
            positionDao = get(),
            eventDao = get(),
            downloadDao = get(),
            deviceId = get(qualifier = named("deviceId")),
            scope = get(qualifier = named("playbackScope"))
        )
    }

    // Sleep timer manager
    single {
        SleepTimerManager(
            scope = get(qualifier = named("playbackScope"))
        )
    }

    // Playback manager
    single {
        PlaybackManager(
            settingsRepository = get(),
            bookDao = get(),
            progressTracker = get(),
            tokenProvider = get(),
            downloadService = get(),
            scope = get(qualifier = named("playbackScope"))
        )
    }

    // Background sync scheduler
    single<BackgroundSyncScheduler> { IosBackgroundSyncScheduler() }
}
