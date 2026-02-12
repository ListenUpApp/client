@file:Suppress("MagicNumber", "TooManyFunctions")

package com.calypsan.listenup.client.playback

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import platform.AVFoundation.AVAudioTimePitchAlgorithmTimeDomain
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerTimeControlStatusPaused
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.AVQueuePlayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.audioTimePitchAlgorithm
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.darwin.sel_registerName

private val logger = KotlinLogging.logger {}

/**
 * Apple platform audio player using AVFoundation.
 *
 * Uses AVQueuePlayer for sequential playback of audio segments (audiobook chapters).
 * Supports authenticated streaming via AVURLAsset HTTP headers,
 * pitch-preserved speed control, and position tracking.
 *
 * Works on both iOS and macOS via the appleMain source set.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class AvFoundationAudioPlayer(
    private val tokenProvider: AudioTokenProvider,
    private val scope: CoroutineScope,
) : AudioPlayer {
    private val _state = MutableStateFlow(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs

    private var player: AVQueuePlayer? = null
    private var segments: List<AudioSegment> = emptyList()
    private var playerItems: List<AVPlayerItem> = emptyList()
    private var currentSegmentIndex: Int = 0
    private var currentSpeed: Float = 1.0f
    private var timeObserver: Any? = null
    private var hasLoaded: Boolean = false

    /** Notification handler for item-did-play-to-end. */
    private var endHandler: ItemEndHandler? = null

    // ── Load ──────────────────────────────────────────────────────────────

    override suspend fun load(segments: List<AudioSegment>) {
        if (segments.isEmpty()) {
            logger.error { "Cannot load empty segment list" }
            _state.value = PlaybackState.Error
            return
        }

        release()

        this.segments = segments
        currentSegmentIndex = 0
        _durationMs.value = segments.sumOf { it.durationMs }
        _state.value = PlaybackState.Buffering

        logger.info { "Loading ${segments.size} segments, total duration: ${_durationMs.value}ms" }

        // Create AVPlayerItems for each segment
        val items = segments.map { segment -> createPlayerItem(segment) }
        playerItems = items

        // Create AVQueuePlayer with all items
        val queuePlayer = AVQueuePlayer(items = items)

        // Set pitch algorithm for speed changes
        items.forEach { item ->
            item.audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmTimeDomain
        }

        player = queuePlayer
        hasLoaded = true

        // Set up observers
        setupTimeObserver(queuePlayer)
        setupNotificationObservers()

        logger.info { "AVQueuePlayer created with ${items.size} items" }
        _state.value = PlaybackState.Paused
    }

    /**
     * Create an AVPlayerItem for a segment.
     * Uses local file path if available, otherwise streams with auth headers.
     */
    private fun createPlayerItem(segment: AudioSegment): AVPlayerItem {
        val localPath = segment.localPath
        if (localPath != null) {
            val fileUrl = NSURL.fileURLWithPath(localPath)
            logger.debug { "Creating item from local file: $localPath" }
            return AVPlayerItem(uRL = fileUrl)
        }

        // Streaming URL with auth headers
        val token = tokenProvider.getToken()
        val headers: Map<Any?, Any> = if (token != null) {
            mapOf("Authorization" to "Bearer $token")
        } else {
            emptyMap()
        }

        // AVURLAssetHTTPHeaderFieldsKey string literal for the option key
        val options: Map<Any?, *> = mapOf("AVURLAssetHTTPHeaderFieldsKey" to headers)
        val url = NSURL(string = segment.url)
        val asset = AVURLAsset(uRL = url, options = options)

        logger.debug { "Creating item from URL: ${segment.url.takeLast(60)}" }
        return AVPlayerItem(asset = asset)
    }

    // ── Playback Controls ────────────────────────────────────────────────

    override fun play() {
        val p = player ?: return
        p.play()
        if (currentSpeed != 1.0f) {
            p.rate = currentSpeed
        }
    }

    override fun pause() {
        player?.pause()
    }

    override fun seekTo(positionMs: Long) {
        val p = player ?: return
        val coercedPosition = positionMs.coerceIn(0, _durationMs.value)
        val (segmentIndex, segmentOffset) = resolvePosition(coercedPosition)

        if (segmentIndex != currentSegmentIndex) {
            // Need to rebuild the queue from the target segment
            rebuildQueue(segmentIndex)
        }

        // Seek within the current item
        val time = CMTimeMakeWithSeconds(
            seconds = segmentOffset.toDouble() / 1000.0,
            preferredTimescale = 1000,
        )
        p.seekToTime(time)
        _positionMs.value = coercedPosition
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed
        val p = player ?: return

        // Update pitch algorithm on all items
        playerItems.forEach { item ->
            item.audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmTimeDomain
        }

        // If currently playing, update the rate
        if (_state.value == PlaybackState.Playing) {
            p.rate = speed
        }
    }

    // ── Release ──────────────────────────────────────────────────────────

    override fun release() {
        removeObservers()
        val p = player
        if (p != null) {
            p.pause()
            // Remove all items from queue
            while (p.items().isNotEmpty()) {
                p.advanceToNextItem()
            }
        }
        player = null
        playerItems = emptyList()
        segments = emptyList()
        currentSegmentIndex = 0
        hasLoaded = false
        _state.value = PlaybackState.Idle
        _positionMs.value = 0L
        _durationMs.value = 0L
        logger.info { "AvFoundationAudioPlayer released" }
    }

    // ── Queue Management ─────────────────────────────────────────────────

    /**
     * Rebuild the AVQueuePlayer queue starting from the given segment index.
     * This is needed when seeking to a different segment, because AVQueuePlayer
     * only plays forward through its queue.
     */
    private fun rebuildQueue(fromSegmentIndex: Int) {
        val p = player ?: return
        val wasPlaying = _state.value == PlaybackState.Playing

        p.pause()

        // Remove all items from queue
        while (p.items().isNotEmpty()) {
            p.advanceToNextItem()
        }

        // Re-create items from the target segment onward
        val newItems = segments.drop(fromSegmentIndex).map { segment ->
            val item = createPlayerItem(segment)
            item.audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmTimeDomain
            item
        }

        // Insert items into the queue
        newItems.forEach { item ->
            if (p.canInsertItem(item, afterItem = null)) {
                p.insertItem(item, afterItem = null)
            }
        }

        // Update tracking
        playerItems = newItems
        currentSegmentIndex = fromSegmentIndex

        // Re-register end notification for the new items
        removeNotificationObservers()
        setupNotificationObservers()

        if (wasPlaying) {
            p.play()
            if (currentSpeed != 1.0f) {
                p.rate = currentSpeed
            }
        }

        logger.debug { "Queue rebuilt from segment $fromSegmentIndex (${newItems.size} items)" }
    }

    // ── Position & State Tracking ────────────────────────────────────────

    /**
     * Set up periodic time observer to update position and state flows.
     * Fires every 0.25 seconds. Also checks player state on each tick
     * to avoid KVO complexity in Kotlin/Native.
     */
    private fun setupTimeObserver(queuePlayer: AVQueuePlayer) {
        val interval = CMTimeMakeWithSeconds(
            seconds = 0.25,
            preferredTimescale = 1000,
        )

        timeObserver = queuePlayer.addPeriodicTimeObserverForInterval(
            interval = interval,
            queue = null, // Main queue
        ) { time ->
            val currentItemTime = CMTimeGetSeconds(time)
            if (currentItemTime.isNaN() || currentItemTime < 0) return@addPeriodicTimeObserverForInterval

            // Determine which segment is currently playing
            val currentItem = queuePlayer.currentItem
            val itemIndex = playerItems.indexOfFirst { it === currentItem }

            // Calculate book-relative position
            // playerItems[0] maps to segments[currentSegmentIndex]
            val actualSegmentIndex = if (itemIndex >= 0) {
                currentSegmentIndex + itemIndex
            } else {
                currentSegmentIndex
            }

            if (actualSegmentIndex < segments.size) {
                val segmentOffset = segments[actualSegmentIndex].offsetMs
                val bookPosition = segmentOffset + (currentItemTime * 1000).toLong()
                scope.launch(Dispatchers.Main) {
                    _positionMs.value = bookPosition.coerceIn(0, _durationMs.value)
                }
            }

            // Update playback state from timeControlStatus (avoids KVO)
            scope.launch(Dispatchers.Main) {
                updateStateFromPlayer(queuePlayer)
            }
        }
    }

    /**
     * Map AVPlayer.timeControlStatus to PlaybackState.
     * Called from the periodic time observer to avoid KVO in Kotlin/Native.
     */
    private fun updateStateFromPlayer(queuePlayer: AVQueuePlayer) {
        // Don't override terminal states
        if (_state.value == PlaybackState.Ended || _state.value == PlaybackState.Idle) return

        // Check for item errors
        val currentItem = queuePlayer.currentItem
        if (currentItem != null && currentItem.status == AVPlayerItemStatusFailed) {
            logger.error { "AVPlayerItem failed: ${currentItem.error?.localizedDescription}" }
            _state.value = PlaybackState.Error
            return
        }

        val newState = when (queuePlayer.timeControlStatus) {
            AVPlayerTimeControlStatusPlaying -> PlaybackState.Playing
            AVPlayerTimeControlStatusPaused -> {
                // Don't override Buffering during initial load
                if (_state.value == PlaybackState.Buffering && currentItem?.status != AVPlayerItemStatusReadyToPlay) {
                    return
                }
                PlaybackState.Paused
            }
            AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate -> PlaybackState.Buffering
            else -> return
        }

        if (_state.value != newState) {
            logger.debug { "State: ${_state.value} → $newState" }
            _state.value = newState
        }
    }

    // ── Notification Observers ───────────────────────────────────────────

    /**
     * Set up NSNotificationCenter observers for playback events.
     */
    private fun setupNotificationObservers() {
        val center = NSNotificationCenter.defaultCenter

        val handler = ItemEndHandler(
            onItemEnd = { notification ->
                scope.launch(Dispatchers.Main) {
                    handleItemDidPlayToEnd(notification)
                }
            },
        )

        // Register for all current player items
        playerItems.forEach { item ->
            center.addObserver(
                observer = handler,
                selector = sel_registerName("onItemDidPlayToEnd:"),
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = item,
            )
        }

        endHandler = handler
    }

    private fun handleItemDidPlayToEnd(notification: NSNotification?) {
        val finishedItem = notification?.`object` as? AVPlayerItem

        // Find which item finished
        val itemIndex = playerItems.indexOfFirst { it === finishedItem }
        if (itemIndex < 0) return

        val finishedSegmentIndex = currentSegmentIndex + itemIndex
        logger.info { "Segment $finishedSegmentIndex finished playing" }

        // Check if this was the last segment
        if (finishedSegmentIndex >= segments.size - 1) {
            logger.info { "All segments finished — playback complete" }
            _positionMs.value = _durationMs.value
            _state.value = PlaybackState.Ended
        } else {
            // AVQueuePlayer auto-advances; update our tracking
            logger.debug { "Auto-advancing to segment ${finishedSegmentIndex + 1}" }
        }
    }

    // ── Observer Cleanup ─────────────────────────────────────────────────

    private fun removeObservers() {
        // Remove time observer
        val tObs = timeObserver
        if (tObs != null) {
            player?.removeTimeObserver(tObs)
            timeObserver = null
        }

        // Remove notification observers
        removeNotificationObservers()
    }

    private fun removeNotificationObservers() {
        val center = NSNotificationCenter.defaultCenter
        endHandler?.let { center.removeObserver(it) }
        endHandler = null
    }

    // ── Position Resolution ──────────────────────────────────────────────

    /**
     * Translate a book-relative position to segment index + offset within segment.
     */
    private fun resolvePosition(bookPositionMs: Long): Pair<Int, Long> {
        var accumulated = 0L
        for ((index, segment) in segments.withIndex()) {
            if (bookPositionMs < accumulated + segment.durationMs) {
                return index to (bookPositionMs - accumulated)
            }
            accumulated += segment.durationMs
        }
        return if (segments.isNotEmpty()) {
            segments.lastIndex to segments.last().durationMs
        } else {
            0 to 0L
        }
    }
}

// ── Notification Handler ─────────────────────────────────────────────────

/**
 * NSObject subclass for handling NSNotification callbacks.
 * Required because @selector targets must be methods on NSObject subclasses.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class ItemEndHandler(
    private val onItemEnd: (NSNotification?) -> Unit,
) : NSObject() {

    @ObjCAction
    fun onItemDidPlayToEnd(notification: NSNotification?) {
        onItemEnd(notification)
    }
}
