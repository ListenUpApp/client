package com.calypsan.listenup.client

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.calypsan.listenup.client.core.ImageLoaderFactory
import com.calypsan.listenup.client.di.sharedModules
import com.calypsan.listenup.client.workers.SyncWorker
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
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
 * ListenUp Application class.
 *
 * Initializes dependency injection, Coil image loading, and other app-wide concerns.
 */
class ListenUp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        // Initialize Koin dependency injection
        startKoin {
            // Use Android logger for Koin diagnostic messages
            androidLogger(Level.DEBUG)

            // Provide Android Context to Koin
            androidContext(this@ListenUp)

            // Load all shared and Android-specific modules
            modules(sharedModules + androidModule)
        }

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
