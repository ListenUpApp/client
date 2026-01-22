package com.calypsan.listenup.desktop.di

import org.koin.dsl.module

/**
 * Desktop application-specific Koin module.
 *
 * Provides desktop-only dependencies that don't belong in :composeApp's platformModule.
 * Examples:
 * - Window state management
 * - System tray controller
 * - Desktop preferences (window size/position)
 *
 * Most platform dependencies are provided by:
 * - :shared module (repositories, use cases, data layer)
 * - :composeApp platformModule (playback, downloads, background sync)
 */
val desktopAppModule =
    module {
        // Desktop-specific bindings will go here
        // For now, empty - composeApp's platformModule provides the core dependencies

        // TODO: Add window state persistence
        // single { WindowStateManager(storagePaths = get()) }

        // TODO: Add system tray controller with playback integration
        // single { TrayController(playbackManager = get()) }
    }
