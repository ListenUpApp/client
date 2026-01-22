package com.calypsan.listenup.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.calypsan.listenup.client.di.platformModule
import com.calypsan.listenup.client.di.sharedModules
import com.calypsan.listenup.desktop.di.desktopAppModule
import com.calypsan.listenup.desktop.window.ListenUpWindow
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.context.startKoin

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "Starting ListenUp Desktop..." }

    // Initialize Koin DI
    startKoin {
        modules(
            sharedModules +      // From :shared module
                platformModule + // From :composeApp desktopMain
                desktopAppModule, // Desktop app specific
        )
    }

    logger.info { "Koin initialized, launching application..." }

    application {
        val windowState =
            rememberWindowState(
                size = DpSize(1280.dp, 800.dp),
            )

        ListenUpWindow(
            state = windowState,
            onCloseRequest = ::exitApplication,
        )
    }
}
