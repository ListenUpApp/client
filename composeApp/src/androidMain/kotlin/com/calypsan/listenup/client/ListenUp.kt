package com.calypsan.listenup.client

import android.app.Application
import android.content.Context
import android.provider.Settings
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.calypsan.listenup.client.core.ImageLoaderFactory
import com.calypsan.listenup.client.di.sharedModules
import com.calypsan.listenup.client.playback.AudioTokenProvider
import com.calypsan.listenup.client.playback.PlaybackErrorHandler
import com.calypsan.listenup.client.playback.NowPlayingViewModel
import com.calypsan.listenup.client.playback.PlaybackManager
import com.calypsan.listenup.client.playback.PlayerViewModel
import com.calypsan.listenup.client.playback.ProgressTracker
import com.calypsan.listenup.client.playback.SleepTimerManager
import com.calypsan.listenup.client.workers.SyncWorker
import com.calypsan.listenup.client.download.DownloadFileManager
import com.calypsan.listenup.client.download.DownloadManager
import com.calypsan.listenup.client.download.DownloadWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

/**
 * Android-specific dependencies module.
 * Contains ViewModels and other Android-specific components.
 */
val androidModule = module {
    viewModelOf(::InstanceViewModel)
}

/**
 * Playback module for audio streaming.
 * Contains Media3 integration and playback state management.
 */
val playbackModule = module {
    // Device ID for listening events (stable across app reinstalls on Android 8+)
    single {
        val context: Context = get()
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
    }

    // Application-scoped coroutine for progress tracking
    single(createdAtStart = false) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    // Audio token provider for authenticated streaming
    single {
        AudioTokenProvider(
            settingsRepository = get(),
            authApi = get(),
            scope = get()
        )
    }

    // Progress tracker for position persistence and event recording
    single {
        ProgressTracker(
            positionDao = get(),
            eventDao = get(),
            downloadDao = get(),
            deviceId = get(),
            scope = get()
        )
    }

    // Playback error handler
    single {
        PlaybackErrorHandler(
            progressTracker = get(),
            tokenProvider = get()
        )
    }

    // Playback manager - orchestrates playback startup
    single {
        PlaybackManager(
            settingsRepository = get(),
            bookDao = get(),
            progressTracker = get(),
            tokenProvider = get(),
            downloadManager = get(),
            scope = get()
        )
    }

    // Sleep timer manager - handles sleep timer state and countdown
    single {
        SleepTimerManager(scope = get())
    }

    // Player ViewModel - connects UI to MediaController
    viewModel {
        PlayerViewModel(
            context = get(),
            playbackManager = get()
        )
    }

    // Now Playing ViewModel - app-wide mini player and full screen state
    viewModel {
        NowPlayingViewModel(
            context = get(),
            playbackManager = get(),
            bookRepository = get(),
            sleepTimerManager = get()
        )
    }
}

/**
 * Download module for offline audiobook downloads.
 * Contains download management and file storage components.
 */
val downloadModule = module {
    // Download file manager - handles local file operations
    single { DownloadFileManager(androidContext()) }

    // Download manager - coordinates download queue and state
    single {
        DownloadManager(
            downloadDao = get(),
            bookDao = get(),
            settingsRepository = get(),
            workManager = WorkManager.getInstance(androidContext()),
            fileManager = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
    }
}

/**
 * ListenUp Application class.
 *
 * Initializes dependency injection, Coil image loading, and other app-wide concerns.
 */
class ListenUp : Application(), SingletonImageLoader.Factory, KoinComponent {
    override fun onCreate() {
        super.onCreate()

        // Initialize Koin dependency injection
        startKoin {
            // Use Android logger for Koin diagnostic messages
            androidLogger(Level.DEBUG)

            // Provide Android Context to Koin
            androidContext(this@ListenUp)

            // Load all shared and Android-specific modules
            modules(sharedModules + androidModule + playbackModule + downloadModule)
        }

        // Configure WorkManager with custom factory for dependency injection
        val workerFactory = DownloadWorkerFactory(
            downloadDao = get(),
            fileManager = get(),
            tokenProvider = get(),
            settingsRepository = get()
        )

        val workManagerConfig = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

        WorkManager.initialize(this, workManagerConfig)

        // Schedule periodic background sync
        // TODO: Only schedule after user authentication
        SyncWorker.schedule(this)
    }

    /**
     * Create singleton ImageLoader for Coil.
     *
     * Called once by Coil to initialize the app-wide ImageLoader.
     * Configured to load book covers from local file storage.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoaderFactory.create(
            context = this,
            debug = false // TODO: Enable in debug builds when BuildConfig is available
        )
    }
}
