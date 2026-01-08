package com.calypsan.listenup.client.data.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates exclusive access to Room database during sync operations.
 *
 * Prevents race conditions between:
 * - SSE event processing (writes entities)
 * - Push sync flush (reads entities for conflict detection)
 * - Delta sync on reconnection (writes entities)
 *
 * All operations that read or write sync-sensitive entities should
 * acquire this mutex first. Events queue in their respective flows
 * while waiting—none are lost.
 */
class SyncMutex {
    private val mutex = Mutex()

    /**
     * Execute a block while holding the sync lock.
     *
     * Use this to wrap any operation that reads or writes sync-sensitive
     * entities in Room (books, series, contributors, etc.).
     */
    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    /**
     * Check if the mutex is currently locked.
     *
     * Useful for logging and debugging sync coordination.
     */
    val isLocked: Boolean get() = mutex.isLocked
}
