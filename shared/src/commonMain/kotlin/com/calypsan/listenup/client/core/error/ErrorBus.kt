package com.calypsan.listenup.client.core.error

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Global error bus for surfacing errors from any layer to the UI.
 *
 * Any component (repository, sync engine, background task) can emit errors here.
 * The UI subscribes once in AppShell to display them via Snackbar.
 *
 * This is intentionally a singleton object rather than a DI-provided instance
 * because error reporting needs to work from static contexts (e.g., global
 * exception handlers) where DI isn't available.
 *
 * Usage from data/domain layer:
 * ```kotlin
 * } catch (e: Exception) {
 *     val error = ErrorMapper.map(e)
 *     ErrorBus.emit(error)
 * }
 * ```
 *
 * Usage from UI layer (handled automatically by GlobalErrorSnackbar):
 * ```kotlin
 * LaunchedEffect(Unit) {
 *     ErrorBus.errors.collect { error ->
 *         snackbarHostState.showSnackbar(error.message)
 *     }
 * }
 * ```
 */
object ErrorBus {
    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 16)

    /** Stream of errors emitted from anywhere in the app. */
    val errors: SharedFlow<AppError> = _errors

    /**
     * Emit an error to be displayed in the UI.
     *
     * Non-suspending — safe to call from any context.
     * Drops the error silently if the buffer is full (unlikely with 16 slots).
     */
    fun emit(error: AppError) {
        _errors.tryEmit(error)
    }

    /**
     * Convenience: map an exception to AppError and emit it.
     */
    fun emit(exception: Throwable) {
        _errors.tryEmit(ErrorMapper.map(exception))
    }
}
