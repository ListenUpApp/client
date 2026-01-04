package com.calypsan.listenup.client.core

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Represents the state of an asynchronous operation in UI.
 *
 * Unlike [Result] which represents operation outcomes, [AsyncState] models
 * the lifecycle of async UI operations where we need to show loading indicators,
 * error messages, or success content.
 *
 * This pattern makes illegal states unrepresentable - you can't have
 * isLoading=true AND error!=null simultaneously.
 *
 * Usage in ViewModels:
 * ```kotlin
 * val state: StateFlow<AsyncState<BookDetail>>
 *     field = MutableStateFlow(AsyncState.Loading)
 *
 * fun load(bookId: String) {
 *     viewModelScope.launch {
 *         state.value = AsyncState.Loading
 *         state.value = try {
 *             AsyncState.Success(repository.getBook(bookId))
 *         } catch (e: Exception) {
 *             AsyncState.Error(e.message ?: "Failed to load book")
 *         }
 *     }
 * }
 * ```
 *
 * Usage in Compose:
 * ```kotlin
 * when (val state = viewModel.state.collectAsState().value) {
 *     is AsyncState.Loading -> LoadingIndicator()
 *     is AsyncState.Error -> ErrorMessage(state.message, onRetry)
 *     is AsyncState.Success -> BookDetail(state.data)
 * }
 * ```
 */
sealed interface AsyncState<out T> {
    /**
     * Initial/loading state before data is available.
     */
    data object Loading : AsyncState<Nothing>

    /**
     * Error state when operation failed.
     *
     * @property message Human-readable error message for UI display
     * @property cause Optional underlying exception for logging
     * @property retryable Whether the operation can be retried
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val retryable: Boolean = true,
    ) : AsyncState<Nothing>

    /**
     * Success state containing the loaded data.
     *
     * @property data The successfully loaded data
     */
    data class Success<T>(
        val data: T,
    ) : AsyncState<T>
}

/**
 * Returns true if this state is Loading.
 */
@OptIn(ExperimentalContracts::class)
fun <T> AsyncState<T>.isLoading(): Boolean {
    contract {
        returns(true) implies (this@isLoading is AsyncState.Loading)
    }
    return this is AsyncState.Loading
}

/**
 * Returns true if this state is Error.
 */
@OptIn(ExperimentalContracts::class)
fun <T> AsyncState<T>.isError(): Boolean {
    contract {
        returns(true) implies (this@isError is AsyncState.Error)
    }
    return this is AsyncState.Error
}

/**
 * Returns true if this state is Success.
 */
@OptIn(ExperimentalContracts::class)
fun <T> AsyncState<T>.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is AsyncState.Success<T>)
    }
    return this is AsyncState.Success
}

/**
 * Returns the data if Success, or null otherwise.
 */
fun <T> AsyncState<T>.getOrNull(): T? =
    when (this) {
        is AsyncState.Loading -> null
        is AsyncState.Error -> null
        is AsyncState.Success -> data
    }

/**
 * Returns the data if Success, or a default value otherwise.
 */
inline fun <T> AsyncState<T>.getOrDefault(defaultValue: () -> T): T =
    when (this) {
        is AsyncState.Loading -> defaultValue()
        is AsyncState.Error -> defaultValue()
        is AsyncState.Success -> data
    }

/**
 * Transform the success data.
 */
inline fun <T, R> AsyncState<T>.map(transform: (T) -> R): AsyncState<R> =
    when (this) {
        is AsyncState.Loading -> AsyncState.Loading
        is AsyncState.Error -> this
        is AsyncState.Success -> AsyncState.Success(transform(data))
    }

/**
 * Execute action only when Success.
 */
inline fun <T> AsyncState<T>.onSuccess(action: (T) -> Unit): AsyncState<T> {
    if (this is AsyncState.Success) action(data)
    return this
}

/**
 * Execute action only when Error.
 */
inline fun <T> AsyncState<T>.onError(action: (AsyncState.Error) -> Unit): AsyncState<T> {
    if (this is AsyncState.Error) action(this)
    return this
}

/**
 * Execute action only when Loading.
 */
inline fun <T> AsyncState<T>.onLoading(action: () -> Unit): AsyncState<T> {
    if (this is AsyncState.Loading) action()
    return this
}

/**
 * Convert a [Result] to [AsyncState].
 *
 * Useful when an async operation returns Result but you need AsyncState for UI:
 * ```kotlin
 * val result = repository.fetchData()
 * state.value = result.toAsyncState()
 * ```
 */
fun <T> Result<T>.toAsyncState(): AsyncState<T> =
    when (this) {
        is Result.Success -> AsyncState.Success(data)
        is Result.Failure -> AsyncState.Error(message, exception)
    }

/**
 * Wrap a suspend block in AsyncState, catching exceptions.
 *
 * ```kotlin
 * state.value = asyncState { repository.fetchBook(id) }
 * ```
 */
suspend inline fun <T> asyncState(crossinline block: suspend () -> T): AsyncState<T> =
    try {
        AsyncState.Success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e // Preserve coroutine cancellation
    } catch (e: Exception) {
        AsyncState.Error(e.message ?: "Unknown error", e)
    }
