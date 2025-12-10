package com.calypsan.listenup.client.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * Singleton holder for the MediaController connection.
 *
 * Both PlayerViewModel and NowPlayingViewModel use this shared controller
 * instead of creating their own connections. This eliminates:
 * - Duplicate listeners on the same player
 * - Potential state drift between two controllers
 * - Resource waste from multiple connections
 *
 * The holder manages the lifecycle through reference counting:
 * - Each ViewModel calls acquire() on init
 * - Each ViewModel calls release() on onCleared()
 * - Connection is established on first acquire
 * - Connection is released when refCount hits 0
 */
class MediaControllerHolder(
    private val context: Context,
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var _controller: MediaController? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val refCount = AtomicInteger(0)

    /**
     * Get the current MediaController, or null if not connected.
     */
    val controller: MediaController?
        get() = _controller

    /**
     * Acquire a reference to the controller.
     * Establishes connection on first acquire.
     * Returns immediately; check [isConnected] or use [awaitController] for async access.
     */
    @Synchronized
    fun acquire() {
        val count = refCount.incrementAndGet()
        logger.debug { "MediaControllerHolder.acquire: refCount=$count" }

        if (count == 1) {
            connect()
        }
    }

    /**
     * Release a reference to the controller.
     * Disconnects when refCount reaches 0.
     */
    @Synchronized
    fun release() {
        val count = refCount.decrementAndGet()
        logger.debug { "MediaControllerHolder.release: refCount=$count" }

        if (count <= 0) {
            refCount.set(0) // Prevent negative
            disconnect()
        }
    }

    /**
     * Execute an action with the controller when it becomes available.
     * If already connected, executes immediately on the current thread.
     * If not connected, queues the action for when connection completes.
     */
    fun withController(action: (MediaController) -> Unit) {
        val current = _controller
        if (current != null) {
            action(current)
            return
        }

        // Queue for when connected
        controllerFuture?.addListener({
            _controller?.let(action)
        }, MoreExecutors.directExecutor())
    }

    private fun connect() {
        if (_controller != null || controllerFuture != null) {
            logger.debug { "MediaControllerHolder: already connecting/connected" }
            return
        }

        logger.info { "MediaControllerHolder: establishing connection" }

        val sessionToken =
            SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java),
            )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                _controller = controllerFuture?.get()
                _isConnected.value = true
                logger.info { "MediaControllerHolder: connected" }
            } catch (e: Exception) {
                logger.error(e) { "MediaControllerHolder: connection failed" }
                _isConnected.value = false
            }
        }, MoreExecutors.directExecutor())
    }

    private fun disconnect() {
        logger.info { "MediaControllerHolder: disconnecting" }

        _controller?.release()
        _controller = null

        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null

        _isConnected.value = false
    }
}
