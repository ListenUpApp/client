package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.data.sync.push.ListeningEventHandler
import com.calypsan.listenup.client.download.DownloadFileManager
import com.calypsan.listenup.client.download.DownloadService
import com.calypsan.listenup.client.platform.DesktopAudioTokenProvider
import com.calypsan.listenup.client.platform.StubBackgroundSyncScheduler
import com.calypsan.listenup.client.platform.StubDownloadService
import com.calypsan.listenup.client.playback.AudioTokenProvider
import com.calypsan.listenup.client.playback.PlaybackManager
import com.calypsan.listenup.client.playback.ProgressTracker
import com.calypsan.listenup.client.playback.SleepTimerManager
import com.calypsan.listenup.client.sync.BackgroundSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.UUID

/**
 * Desktop platform module for Koin.
 *
 * Provides desktop-specific implementations of platform abstractions:
 * - AudioTokenProvider for authenticated streaming
 * - DownloadService (stub - downloads not yet implemented)
 * - BackgroundSyncScheduler (stub - relies on SSE while running)
 * - PlaybackManager for playback orchestration
 * - ProgressTracker for position persistence
 * - SleepTimerManager for sleep timer functionality
 *
 * Full audio playback via VLCJ will be implemented in a future phase.
 */
val platformModule: Module =
    module {
        // Playback-scoped coroutine scope
        single(qualifier = named("playbackScope")) {
            CoroutineScope(SupervisorJob() + IODispatcher)
        }

        // Device ID for listening events
        // Desktop uses a persistent UUID stored in secure storage
        single(qualifier = named("deviceId")) {
            // Generate a stable device ID based on machine characteristics
            val hostname = System.getenv("HOSTNAME")
                ?: System.getenv("COMPUTERNAME")
                ?: "desktop"
            val username = System.getProperty("user.name", "user")
            UUID.nameUUIDFromBytes("$hostname:$username".toByteArray()).toString()
        }

        // File manager for downloads
        single { DownloadFileManager(storagePaths = get()) }

        // Audio token provider
        single<AudioTokenProvider> {
            DesktopAudioTokenProvider(authSession = get())
        }

        // Download service (stub)
        single<DownloadService> { StubDownloadService() }

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
                playbackApi = null, // Desktop will use VLCJ, no transcoding API needed yet
                capabilityDetector = null, // Desktop doesn't need codec detection
                syncApi = get(),
                scope = get(qualifier = named("playbackScope")),
            )
        }

        // Background sync scheduler (stub)
        single<BackgroundSyncScheduler> { StubBackgroundSyncScheduler() }
    }
