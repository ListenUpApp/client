package com.calypsan.listenup.client.core

import com.calypsan.listenup.client.core.error.AppError
import com.calypsan.listenup.client.core.error.AuthError
import com.calypsan.listenup.client.core.error.DataError
import com.calypsan.listenup.client.core.error.NetworkError
import com.calypsan.listenup.client.core.error.ServerError
import com.calypsan.listenup.client.core.error.UnknownError

/**
 * The canonical result type for every fallible suspend function in the codebase.
 *
 * One sealed hierarchy, two variants:
 * - [Success] — carries the produced value.
 * - [Failure] — carries an [AppError], already categorised and user-message-ready.
 *
 * Replaces the three-way split Finding 01 D1 diagnosed ([Result] + [AsyncState] +
 * [AppError] with no conversion path). The name avoids shadowing [kotlin.Result],
 * which must not be used as a public API return type.
 *
 * Source: Android Architecture Guide "Define Result Class for Network Responses" +
 * Kotlin sealed-class API pattern.
 */
sealed interface AppResult<out T> {
    data class Success<T>(
        val data: T,
    ) : AppResult<T>

    data class Failure(
        val error: AppError,
    ) : AppResult<Nothing>
}

/** Transform the success value; failures pass through untouched. */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(data))
        is AppResult.Failure -> this
    }

/** Chain another fallible step; short-circuits on the first failure (railway-oriented). */
inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> =
    when (this) {
        is AppResult.Success -> transform(data)
        is AppResult.Failure -> this
    }

/** Collapse both branches into a single value. */
inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (AppError) -> R,
): R =
    when (this) {
        is AppResult.Success -> onSuccess(data)
        is AppResult.Failure -> onFailure(error)
    }

/** Returns the data on success, or `null` on failure. */
fun <T> AppResult<T>.getOrNull(): T? =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> null
    }

/** Returns the [AppError] on failure, or `null` on success. */
fun <T> AppResult<T>.errorOrNull(): AppError? =
    when (this) {
        is AppResult.Success -> null
        is AppResult.Failure -> error
    }

/** Side-effect on success; returns `this` unchanged. */
inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

/** Side-effect on failure; returns `this` unchanged. */
inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

/**
 * Bridge from the legacy [Result] type to [AppResult]. Used during the incremental
 * migration from `core.Result<T>` to `AppResult<T>` (W2b); new code should return
 * [AppResult] directly and skip this conversion.
 *
 * Mapping preserves each failure's message; the legacy [ErrorCode] routes to the
 * corresponding [AppError] variant:
 * - [ErrorCode.NETWORK_UNAVAILABLE] → [NetworkError]
 * - [ErrorCode.UNAUTHORIZED] → [AuthError]
 * - [ErrorCode.VALIDATION_ERROR] / [ErrorCode.NOT_FOUND] / [ErrorCode.CONFLICT] → [DataError]
 * - [ErrorCode.SERVER_ERROR] → [ServerError] with `statusCode = 0` (legacy sentinel)
 * - [ErrorCode.UNKNOWN] → [UnknownError]
 */
fun <T> Result<T>.toAppResult(): AppResult<T> =
    when (this) {
        is Success -> {
            AppResult.Success(data)
        }

        is Failure -> {
            AppResult.Failure(
                when (errorCode) {
                    ErrorCode.NETWORK_UNAVAILABLE -> {
                        NetworkError(message = message, debugInfo = exception?.message)
                    }

                    ErrorCode.UNAUTHORIZED -> {
                        AuthError(message = message, debugInfo = exception?.message)
                    }

                    ErrorCode.VALIDATION_ERROR,
                    ErrorCode.NOT_FOUND,
                    ErrorCode.CONFLICT,
                    -> {
                        DataError(message = message, debugInfo = exception?.message)
                    }

                    ErrorCode.SERVER_ERROR -> {
                        ServerError(statusCode = 0, message = message, debugInfo = exception?.message)
                    }

                    ErrorCode.UNKNOWN -> {
                        UnknownError(message = message, debugInfo = exception?.message)
                    }
                },
            )
        }
    }
