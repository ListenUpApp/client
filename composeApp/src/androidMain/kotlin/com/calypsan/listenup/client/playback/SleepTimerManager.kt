package com.calypsan.listenup.client.playback

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Manages the sleep timer for audiobook playback.
 *
 * Features:
 * - Duration-based timers (15, 30, 45, 60, 120 minutes)
 * - End of chapter mode (pauses when chapter ends)
 * - Extend timer while active
 *
 * The actual fade-out and pause is performed by the consumer (NowPlayingViewModel)
 * which has access to MediaController.
 */
class SleepTimerManager(
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Inactive)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    // Event emitted when timer fires - consumer performs fade and pause
    private val _sleepEvent = MutableSharedFlow<Unit>(replay = 0)
    val sleepEvent: SharedFlow<Unit> = _sleepEvent.asSharedFlow()

    private var timerJob: Job? = null
    private var endOfChapterJob: Job? = null

    // Current chapter index for end-of-chapter mode
    private var lastKnownChapterIndex: Int = -1

    companion object {
        private const val TICK_INTERVAL_MS = 1000L
    }

    /**
     * Start a sleep timer with the specified mode.
     */
    fun setTimer(mode: SleepTimerMode) {
        logger.info { "Setting sleep timer: $mode" }
        cancelTimer()

        when (mode) {
            is SleepTimerMode.Duration -> startDurationTimer(mode.minutes)
            is SleepTimerMode.EndOfChapter -> startEndOfChapterTimer()
        }
    }

    /**
     * Cancel the active timer.
     */
    fun cancelTimer() {
        logger.info { "Canceling sleep timer" }
        timerJob?.cancel()
        timerJob = null
        endOfChapterJob?.cancel()
        endOfChapterJob = null
        lastKnownChapterIndex = -1
        _state.value = SleepTimerState.Inactive
    }

    /**
     * Add time to an active duration timer.
     */
    fun extendTimer(additionalMinutes: Int) {
        val current = _state.value
        if (current is SleepTimerState.Active && current.mode is SleepTimerMode.Duration) {
            val additionalMs = additionalMinutes * 60_000L
            val newRemaining = current.remainingMs + additionalMs
            val newTotal = current.totalMs + additionalMs

            logger.info { "Extending timer by $additionalMinutes min, new remaining: ${newRemaining / 60000} min" }

            _state.value = current.copy(
                remainingMs = newRemaining,
                totalMs = newTotal
            )
        }
    }

    /**
     * Called by NowPlayingViewModel when chapter changes.
     * Used for end-of-chapter mode detection.
     */
    fun onChapterChanged(newChapterIndex: Int) {
        val current = _state.value
        if (current is SleepTimerState.Active && current.mode is SleepTimerMode.EndOfChapter) {
            // Chapter moved forward - previous chapter ended naturally
            if (lastKnownChapterIndex >= 0 && newChapterIndex > lastKnownChapterIndex) {
                logger.info { "Chapter ended ($lastKnownChapterIndex -> $newChapterIndex), triggering sleep" }
                triggerSleep()
            }
        }
        lastKnownChapterIndex = newChapterIndex
    }

    /**
     * Called by NowPlayingViewModel after fade completes.
     * Resets state to Inactive.
     */
    fun onFadeCompleted() {
        _state.value = SleepTimerState.Inactive
        timerJob = null
        endOfChapterJob = null
        lastKnownChapterIndex = -1
        logger.info { "Sleep timer completed" }
    }

    private fun startDurationTimer(minutes: Int) {
        val totalMs = minutes * 60_000L
        val startedAt = System.currentTimeMillis()

        _state.value = SleepTimerState.Active(
            mode = SleepTimerMode.Duration(minutes),
            remainingMs = totalMs,
            totalMs = totalMs,
            startedAt = startedAt
        )

        timerJob = scope.launch {
            logger.debug { "Starting $minutes minute timer" }

            while (isActive) {
                delay(TICK_INTERVAL_MS)

                val current = _state.value
                if (current !is SleepTimerState.Active) break

                val elapsed = System.currentTimeMillis() - current.startedAt
                val remaining = (current.totalMs - elapsed).coerceAtLeast(0)

                _state.value = current.copy(remainingMs = remaining)

                if (remaining <= 0) {
                    logger.info { "Duration timer completed" }
                    triggerSleep()
                    break
                }
            }
        }
    }

    private fun startEndOfChapterTimer() {
        _state.value = SleepTimerState.Active(
            mode = SleepTimerMode.EndOfChapter,
            remainingMs = 0,
            totalMs = 0,
            startedAt = System.currentTimeMillis()
        )
        logger.debug { "Started end-of-chapter timer, waiting for chapter change" }
    }

    private fun triggerSleep() {
        _state.value = SleepTimerState.FadingOut
        scope.launch {
            _sleepEvent.emit(Unit)
        }
    }
}
