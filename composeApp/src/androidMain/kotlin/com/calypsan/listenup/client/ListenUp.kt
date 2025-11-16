package com.calypsan.listenup.client

import android.app.Application
import com.calypsan.listenup.client.di.sharedModules
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
 * Initializes dependency injection and other app-wide concerns.
 */
class ListenUp : Application() {
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
    }
}
