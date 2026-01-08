package com.calypsan.listenup.client.core

import kotlinx.coroutines.CancellationException
import kotlin.MustUseReturnValues
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Typed error codes for business logic failures.
 * Enables consistent error handling across the application.
 */
enum class ErrorCode {
    /** Input validation failed (email format, password length, etc.) */
    VALIDATION_ERROR,

    /** Network is unavailable */
    NETWORK_UNAVAILABLE,

    /** Authentication required or session expired */
    UNAUTHORIZED,

    /** Requested resource not found */
    NOT_FOUND,

    /** Conflict with existing data */
    CONFLICT,

    /** Server returned an error */
    SERVER_ERROR,

    /** Unclassified error */
    UNKNOWN,
}

/**
 * A discriminated union representing either success or failure.
 * Using sealed interface instead of sealed class for maximum flexibility with Kotlin 2.2.
 *
 * Annotated with @MustUseReturnValues to ensure callers handle the result.
 * When the return value is intentionally ignored, use: val _ = functionReturningResult()
 */
@MustUseReturnValues
sealed interface Result<out T> {
    /**
     * Represents a successful result containing data
     */
    data class Success<T>(
        val data: T,
    ) : Result<T>

    /**
     * Represents a failed result containing an exception and/or error information
     */
    data class Failure(
        val exception: Exception? = null,
        val message: String,
        val errorCode: ErrorCode = ErrorCode.UNKNOWN,
    ) : Result<Nothing>
}

// Type aliases for cleaner code
typealias Success<T> = Result.Success<T>
typealias Failure = Result.Failure

// ========== Helper Constructors ==========

/**
 * Create a Failure from an exception, using the exception's message.
 * This is the most common way to create a Failure when catching exceptions.
 */
fun Failure(exception: Exception): Failure =
    Failure(exception = exception, message = exception.message ?: "Unknown error")

/** Create a validation error failure */
fun validationError(message: String): Failure = Failure(message = message, errorCode = ErrorCode.VALIDATION_ERROR)

/** Create a network unavailable failure */
fun networkError(
    message: String = "Network unavailable",
    exception: Exception? = null,
): Failure = Failure(exception = exception, message = message, errorCode = ErrorCode.NETWORK_UNAVAILABLE)

/** Create an unauthorized failure */
fun unauthorizedError(message: String = "Session expired"): Failure =
    Failure(message = message, errorCode = ErrorCode.UNAUTHORIZED)

/** Create a not found failure */
fun notFoundError(message: String = "Resource not found"): Failure =
    Failure(message = message, errorCode = ErrorCode.NOT_FOUND)

/** Create a server error failure */
fun serverError(
    message: String,
    exception: Exception? = null,
): Failure = Failure(exception = exception, message = message, errorCode = ErrorCode.SERVER_ERROR)

/**
 * Get the exception from a Failure, creating one from the message if not present.
 * Useful for throw statements: `throw failure.exceptionOrFromMessage()`
 */
fun Failure.exceptionOrFromMessage(): Exception = exception ?: IllegalStateException(message)

/**
 * Returns true if this result is Success.
 * Uses Kotlin contracts for smart casting.
 */
@OptIn(ExperimentalContracts::class)
fun <T> Result<T>.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is Success<T>)
    }
    return this is Success
}

/**
 * Returns true if this result is Failure.
 * Uses Kotlin contracts for smart casting.
 */
@OptIn(ExperimentalContracts::class)
fun <T> Result<T>.isFailure(): Boolean {
    contract {
        returns(true) implies (this@isFailure is Failure)
    }
    return this is Failure
}

/**
 * Returns the data if Success, or null if Failure
 */
fun <T> Result<T>.getOrNull(): T? =
    when (this) {
        is Success -> data
        is Failure -> null
    }

/**
 * Returns the data if Success, or a default value if Failure
 */
inline fun <T> Result<T>.getOrDefault(defaultValue: () -> T): T =
    when (this) {
        is Success -> data
        is Failure -> defaultValue()
    }

/**
 * Returns the data if Success, or throws the exception if Failure.
 * If the Failure has no exception, throws an IllegalStateException with the message.
 */
fun <T> Result<T>.getOrThrow(): T =
    when (this) {
        is Success -> data
        is Failure -> throw exception ?: IllegalStateException(message)
    }

/**
 * Transform the success value
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

/**
 * Transform the success value with a suspending function
 */
suspend inline fun <T, R> Result<T>.mapSuspend(crossinline transform: suspend (T) -> R): Result<R> =
    when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

/**
 * Flat map for chaining Results (railway-oriented programming)
 */
inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

/**
 * Execute a side effect on success and return the original Result
 */
inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Success) action(data)
    return this
}

/**
 * Execute a side effect on failure and return the original Result.
 * The action receives the Failure object for access to message, errorCode, and optional exception.
 */
inline fun <T> Result<T>.onFailure(action: (Failure) -> Unit): Result<T> {
    if (this is Failure) action(this)
    return this
}

/**
 * Recover from failure by providing a fallback value.
 * The recovery function receives the Failure object for access to message, errorCode, and optional exception.
 */
inline fun <T> Result<T>.recover(recovery: (Failure) -> T): Result<T> =
    when (this) {
        is Success -> this
        is Failure -> Success(recovery(this))
    }

/**
 * Catch exceptions in a suspend block and wrap in Result.
 * Uses Kotlin contracts for better flow analysis.
 *
 * IMPORTANT: Re-throws CancellationException to preserve coroutine cancellation.
 */
@OptIn(ExperimentalContracts::class)
suspend inline fun <T> suspendRunCatching(crossinline block: suspend () -> T): Result<T> {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }
    return try {
        Success(block())
    } catch (e: CancellationException) {
        throw e // Preserve coroutine cancellation
    } catch (e: Exception) {
        Failure(exception = e, message = e.message ?: "Unknown error")
    }
}

/**
 * Non-suspending version of runCatching that wraps exceptions in Result
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> runCatching(block: () -> T): Result<T> {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }
    return try {
        Success(block())
    } catch (e: Exception) {
        Failure(exception = e, message = e.message ?: "Unknown error")
    }
}
