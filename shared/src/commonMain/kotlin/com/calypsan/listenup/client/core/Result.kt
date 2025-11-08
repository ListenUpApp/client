package com.calypsan.listenup.client.core

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A discriminated union representing either success or failure.
 * Using sealed interface instead of sealed class for maximum flexibility with Kotlin 2.2.
 */
sealed interface Result<out T> {
    /**
     * Represents a successful result containing data
     */
    data class Success<T>(val data: T) : Result<T>

    /**
     * Represents a failed result containing an exception
     */
    data class Failure(
        val exception: Exception,
        val message: String = exception.message ?: "Unknown error"
    ) : Result<Nothing>
}

// Type aliases for cleaner code
typealias Success<T> = Result.Success<T>
typealias Failure = Result.Failure

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
fun <T> Result<T>.getOrNull(): T? = when (this) {
    is Success -> data
    is Failure -> null
}

/**
 * Returns the data if Success, or a default value if Failure
 */
inline fun <T> Result<T>.getOrDefault(defaultValue: () -> T): T = when (this) {
    is Success -> data
    is Failure -> defaultValue()
}

/**
 * Returns the data if Success, or throws the exception if Failure
 */
fun <T> Result<T>.getOrThrow(): T = when (this) {
    is Success -> data
    is Failure -> throw exception
}

/**
 * Transform the success value
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Success -> Success(transform(data))
    is Failure -> this
}

/**
 * Transform the success value with a suspending function
 */
suspend inline fun <T, R> Result<T>.mapSuspend(crossinline transform: suspend (T) -> R): Result<R> = when (this) {
    is Success -> Success(transform(data))
    is Failure -> this
}

/**
 * Flat map for chaining Results (railway-oriented programming)
 */
inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
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
 * Execute a side effect on failure and return the original Result
 */
inline fun <T> Result<T>.onFailure(action: (Exception) -> Unit): Result<T> {
    if (this is Failure) action(exception)
    return this
}

/**
 * Recover from failure by providing a fallback value
 */
inline fun <T> Result<T>.recover(recovery: (Exception) -> T): Result<T> = when (this) {
    is Success -> this
    is Failure -> Success(recovery(exception))
}

/**
 * Catch exceptions in a suspend block and wrap in Result.
 * Uses Kotlin contracts for better flow analysis.
 */
@OptIn(ExperimentalContracts::class)
suspend inline fun <T> suspendRunCatching(crossinline block: suspend () -> T): Result<T> {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return try {
        Success(block())
    } catch (e: Exception) {
        Failure(e)
    }
}

/**
 * Non-suspending version of runCatching that wraps exceptions in Result
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> runCatching(block: () -> T): Result<T> {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return try {
        Success(block())
    } catch (e: Exception) {
        Failure(e)
    }
}
