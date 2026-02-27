package com.calypsan.listenup.client.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Global event bus to signal when playback progress has been saved.
 *
 * Used to trigger immediate refresh of Continue Listening shelf after
 * pause/stop saves progress to the database. Room's reactive queries
 * should handle this automatically, but this provides a reliable fallback
 * to ensure the UI always reflects the latest progress.
 *
 * This is intentionally a singleton object (like ErrorBus) because
 * progress updates need to work from any context without DI.
 *
 * Usage from ProgressTracker:
 * ```kotlin
 * positionDao.save(position)
 * ProgressRefreshBus.emit()
 * ```
 *
 * Usage from HomeRepositoryImpl:
 * ```kotlin
 * playbackPositionDao.observeAll()
 *     .combine(ProgressRefreshBus.refreshTrigger.onStart { emit(Unit) }) { positions, _ -> positions }
 *     .mapLatest { ... }
 * ```
 */
object ProgressRefreshBus {
    private val _refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Stream of refresh signals emitted when progress is saved. */
    val refreshTrigger: SharedFlow<Unit> = _refreshTrigger

    /**
     * Signal that progress has been saved and UI should refresh.
     *
     * Non-suspending — safe to call from any context.
     */
    fun emit() {
        _refreshTrigger.tryEmit(Unit)
    }
}
