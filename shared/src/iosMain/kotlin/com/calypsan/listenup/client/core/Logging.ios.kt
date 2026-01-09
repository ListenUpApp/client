package com.calypsan.listenup.client.core

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Configures kotlin-logging for iOS.
 *
 * On iOS, kotlin-logging outputs to the console by default,
 * which is captured by Xcode's debug console.
 *
 * Call this once during app initialization to log a startup message.
 */
fun configureLogging() {
    logger.info { "ListenUp iOS logging initialized" }
}
