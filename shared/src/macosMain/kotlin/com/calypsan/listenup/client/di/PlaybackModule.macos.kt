@file:Suppress("StringLiteralDuplication")

package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.download.DownloadFileManager
import com.calypsan.listenup.client.download.DownloadService
import com.calypsan.listenup.client.download.AppleDownloadService
import com.calypsan.listenup.client.data.sync.push.ListeningEventHandler
import com.calypsan.listenup.client.playback.AudioTokenProvider
import com.calypsan.listenup.client.playback.AppleAudioTokenProvider
import com.calypsan.listenup.client.playback.PlaybackManager
import com.calypsan.listenup.client.playback.ProgressTracker
import com.calypsan.listenup.client.playback.SleepTimerManager
import com.calypsan.listenup.client.sync.BackgroundSyncScheduler
import com.calypsan.listenup.client.sync.MacosBackgroundSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUUID
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * macOS playback module.
 *
 * Provides audio playback components for macOS:
 * - AudioTokenProvider for authenticated streaming
 * - DownloadService for offline downloads
 * - PlaybackManager for playback orchestration
 * - ProgressTracker for position persistence
 * - SleepTimerManager for sleep timer functionality
 */
val macosPlaybackModule: Module =
    module {
        // Playback-scoped coroutine scope
        single(qualifier = named("playbackScope")) {
            CoroutineScope(SupervisorJob() + IODispatcher)
        }

        // Device ID for listening events
        // macOS uses a generated UUID persisted via secure storage
        // TODO: Use a more stable identifier when macOS app matures
        single(qualifier = named("deviceId")) {
            val hostName = NSProcessInfo.processInfo.hostName
            "macos-$hostName"
        }

        // File manager for downloads
        single { DownloadFileManager() }

        // Audio token provider
        single<AudioTokenProvider> {
            AppleAudioTokenProvider(
                authSession = get(),
                authApi = get(),
                scope = get(qualifier = named("playbackScope")),
            )
        }

        // Download service
        single<DownloadService> {
            AppleDownloadService(
                downloadDao = get(),
                bookDao = get(),
                serverConfig = get(),
                tokenProvider = get(),
                fileManager = get(),
                scope = get(qualifier = named("playbackScope")),
            )
        }

        // Progress tracker
        single {
            ProgressTracker(
                positionDao = get(),
                downloadDao = get(),
                listeningEventDao = get(),
                syncApi = get(),
                pendingOperationRepository = get(),
                listeningEventHandler = get<ListeningEventHandler>(),
                pushSyncOrchestrator = get(),
                deviceId = get(qualifier = named("deviceId")),
                scope = get(qualifier = named("playbackScope")),
            )
        }

        // Sleep timer manager
        single {
            SleepTimerManager(
                scope = get(qualifier = named("playbackScope")),
            )
        }

        // Playback manager
        single {
            PlaybackManager(
                serverConfig = get(),
                playbackPreferences = get(),
                bookDao = get(),
                chapterDao = get(),
                imageStorage = get(),
                progressTracker = get(),
                tokenProvider = get(),
                downloadService = get(),
                playbackApi = null, // macOS uses native AVPlayer, no transcoding API needed
                capabilityDetector = null, // macOS doesn't need codec detection
                syncApi = get(),
                deviceContext = get(),
                scope = get(qualifier = named("playbackScope")),
            )
        }

        // Background sync scheduler (stub for now)
        single<BackgroundSyncScheduler> { MacosBackgroundSyncScheduler() }
    }
