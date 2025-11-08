package com.calypsan.listenup.client.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * iOS-specific Koin initialization.
 *
 * Starts Koin with shared modules plus any iOS-specific modules.
 * Should be called from the iOS app's initialization code (typically in App struct).
 *
 * @param additionalModules iOS-specific modules to include
 */
actual fun initializeKoin(additionalModules: List<Module>) {
    startKoin {
        modules(sharedModules + additionalModules)
    }
}
