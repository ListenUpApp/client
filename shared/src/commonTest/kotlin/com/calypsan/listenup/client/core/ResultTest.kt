package com.calypsan.listenup.client.core

import com.calypsan.listenup.client.checkIs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for Result type and its extension functions.
 *
 * Critical: Tests CancellationException handling in suspendRunCatching
 * to ensure coroutine cancellation is properly preserved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultTest {
    // ========== Basic Result Creation ==========

    @Test
    fun `Success wraps value correctly`() {
        val result: Result<String> = Success("hello")
        val success = assertIs<Success<String>>(result)
        assertEquals("hello", success.data)
    }

    @Test
    fun `Failure wraps exception correctly`() {
        val exception = IllegalStateException("test error")
        val result: Result<String> = Failure(exception = exception, message = "test error")
        val failure = assertIs<Failure>(result)
        assertEquals(exception, failure.exception)
        assertEquals("test error", failure.message)
    }

    @Test
    fun `Failure can be created with just message`() {
        val result = Failure(message = "Error without exception")
        assertNull(result.exception)
        assertEquals("Error without exception", result.message)
        assertEquals(ErrorCode.UNKNOWN, result.errorCode)
    }

    @Test
    fun `Failure captures error code correctly`() {
        val result = validationError("Invalid email")
        assertEquals("Invalid email", result.message)
        assertEquals(ErrorCode.VALIDATION_ERROR, result.errorCode)
        assertNull(result.exception)
    }

    // ========== isSuccess / isFailure ==========

    @Test
    fun `isSuccess returns true for Success`() {
        val result: Result<Int> = Success(42)
        assertTrue(result.isSuccess())
        assertFalse(result.isFailure())
    }

    @Test
    fun `isFailure returns true for Failure`() {
        val result: Result<Int> = Failure(message = "error")
        assertTrue(result.isFailure())
        assertFalse(result.isSuccess())
    }

    // ========== getOrNull / getOrDefault / getOrThrow ==========

    @Test
    fun `getOrNull returns value for Success`() {
        val result: Result<String> = Success("value")
        assertEquals("value", result.getOrNull())
    }

    @Test
    fun `getOrNull returns null for Failure`() {
        val result: Result<String> = Failure(message = "error")
        assertNull(result.getOrNull())
    }

    @Test
    fun `getOrDefault returns value for Success`() {
        val result: Result<Int> = Success(42)
        assertEquals(42, result.getOrDefault { 0 })
    }

    @Test
    fun `getOrDefault returns default for Failure`() {
        val result: Result<Int> = Failure(message = "error")
        assertEquals(0, result.getOrDefault { 0 })
    }

    @Test
    fun `getOrThrow returns value for Success`() {
        val result: Result<String> = Success("value")
        assertEquals("value", result.getOrThrow())
    }

    @Test
    fun `getOrThrow throws exception for Failure`() {
        val exception = IllegalArgumentException("invalid")
        val result: Result<String> = Failure(exception = exception, message = "invalid")
        val thrown =
            assertFailsWith<IllegalArgumentException> {
                result.getOrThrow()
            }
        assertEquals("invalid", thrown.message)
    }

    @Test
    fun `getOrThrow throws IllegalStateException when no exception in Failure`() {
        val result: Result<String> = Failure(message = "message-only failure")
        val thrown =
            assertFailsWith<IllegalStateException> {
                result.getOrThrow()
            }
        assertEquals("message-only failure", thrown.message)
    }

    // ========== map / flatMap ==========

    @Test
    fun `map transforms Success value`() {
        val result: Result<Int> = Success(21)
        val mapped = result.map { it * 2 }
        val success = assertIs<Success<Int>>(mapped)
        assertEquals(42, success.data)
    }

    @Test
    fun `map preserves Failure`() {
        val exception = Exception("error")
        val result: Result<Int> = Failure(exception = exception, message = "error")
        val mapped = result.map { it * 2 }
        val failure = assertIs<Failure>(mapped)
        assertEquals(exception, failure.exception)
    }

    @Test
    fun `flatMap chains successful Results`() {
        val result: Result<Int> = Success(21)
        val chained = result.flatMap { Success(it * 2) }
        val success = assertIs<Success<Int>>(chained)
        assertEquals(42, success.data)
    }

    @Test
    fun `flatMap short-circuits on initial Failure`() {
        val exception = Exception("first error")
        val result: Result<Int> = Failure(exception = exception, message = "first error")
        var transformCalled = false
        val chained =
            result.flatMap {
                transformCalled = true
                Success(it * 2)
            }
        val failure = assertIs<Failure>(chained)
        assertEquals(exception, failure.exception)
        assertFalse(transformCalled)
    }

    @Test
    fun `flatMap propagates Failure from transform`() {
        val result: Result<Int> = Success(42)
        val secondException = Exception("second error")
        val chained = result.flatMap { Failure(exception = secondException, message = "second error") }
        val failure = assertIs<Failure>(chained)
        assertEquals(secondException, failure.exception)
    }

    // ========== onSuccess / onFailure ==========

    @Test
    fun `onSuccess executes action for Success`() {
        var captured: Int? = null
        val result: Result<Int> = Success(42)
        val returned = result.onSuccess { captured = it }
        assertEquals(42, captured)
        checkIs<Success<Int>>(result)
        assertEquals(result, returned)
    }

    @Test
    fun `onSuccess does not execute action for Failure`() {
        var called = false
        val result: Result<Int> = Failure(message = "error")
        result.onSuccess { called = true }
        assertFalse(called)
    }

    @Test
    fun `onFailure executes action for Failure`() {
        var capturedFailure: Failure? = null
        val exception = Exception("error")
        val result: Result<Int> = Failure(exception = exception, message = "error")
        val returned = result.onFailure { capturedFailure = it }
        assertEquals(exception, capturedFailure?.exception)
        assertEquals("error", capturedFailure?.message)
        checkIs<Failure>(result)
        assertEquals(result, returned)
    }

    @Test
    fun `onFailure does not execute action for Success`() {
        var called = false
        val result: Result<Int> = Success(42)
        result.onFailure { called = true }
        assertFalse(called)
    }

    // ========== recover ==========

    @Test
    fun `recover returns original Success`() {
        val result: Result<Int> = Success(42)
        val recovered = result.recover { 0 }
        val success = assertIs<Success<Int>>(recovered)
        assertEquals(42, success.data)
    }

    @Test
    fun `recover transforms Failure to Success`() {
        val result: Result<Int> = Failure(message = "error")
        val recovered = result.recover { -1 }
        val success = assertIs<Success<Int>>(recovered)
        assertEquals(-1, success.data)
    }

    @Test
    fun `recover provides access to Failure details`() {
        val result: Result<Int> = Failure(message = "validation failed", errorCode = ErrorCode.VALIDATION_ERROR)
        val recovered =
            result.recover { failure ->
                if (failure.errorCode == ErrorCode.VALIDATION_ERROR) 0 else -1
            }
        val success = assertIs<Success<Int>>(recovered)
        assertEquals(0, success.data)
    }

    // ========== runCatching (non-suspending) ==========

    @Test
    fun `runCatching returns Success for successful block`() {
        val result =
            com.calypsan.listenup.client.core
                .runCatching { 42 }
        val success = assertIs<Success<Int>>(result)
        assertEquals(42, success.data)
    }

    @Test
    fun `runCatching returns Failure for throwing block`() {
        val result =
            com.calypsan.listenup.client.core.runCatching<Int> {
                throw IllegalStateException("error")
            }
        val failure = assertIs<Failure>(result)
        checkIs<IllegalStateException>(failure.exception!!)
        assertEquals("error", failure.message)
    }

    // ========== suspendRunCatching (CRITICAL: CancellationException handling) ==========

    @Test
    fun `suspendRunCatching returns Success for successful suspend block`() =
        runTest {
            val result = suspendRunCatching { "async result" }
            val success = assertIs<Success<String>>(result)
            assertEquals("async result", success.data)
        }

    @Test
    fun `suspendRunCatching returns Failure for throwing suspend block`() =
        runTest {
            val result =
                suspendRunCatching<Int> {
                    throw IllegalArgumentException("async error")
                }
            val failure = assertIs<Failure>(result)
            checkIs<IllegalArgumentException>(failure.exception!!)
            assertEquals("async error", failure.message)
        }

    @Test
    fun `suspendRunCatching re-throws CancellationException`() =
        runTest {
            // This is the CRITICAL test - CancellationException must not be caught
            assertFailsWith<CancellationException> {
                suspendRunCatching<Int> {
                    throw CancellationException("coroutine cancelled")
                }
            }
        }

    @Test
    fun `suspendRunCatching preserves cancellation during actual coroutine cancel`() =
        runTest {
            var exceptionCaught = false
            var resultReturned = false

            val job =
                launch {
                    try {
                        suspendRunCatching {
                            // Simulate long-running work
                            kotlinx.coroutines.delay(10_000)
                            "should not reach"
                        }
                        resultReturned = true
                    } catch (e: CancellationException) {
                        exceptionCaught = true
                        throw e
                    }
                }

            // Let the coroutine start
            testScheduler.advanceTimeBy(100)

            // Cancel the job
            job.cancel()

            // Advance to let cancellation propagate
            testScheduler.advanceUntilIdle()

            // CancellationException should propagate, not be wrapped in Result
            assertTrue(exceptionCaught, "CancellationException should propagate")
            assertFalse(resultReturned, "Block should not complete after cancellation")
        }

    // ========== mapSuspend ==========

    @Test
    fun `mapSuspend transforms Success value with suspend function`() =
        runTest {
            val result: Result<Int> = Success(21)
            val mapped =
                result.mapSuspend {
                    kotlinx.coroutines.delay(1)
                    it * 2
                }
            val success = assertIs<Success<Int>>(mapped)
            assertEquals(42, success.data)
        }

    @Test
    fun `mapSuspend preserves Failure`() =
        runTest {
            val exception = Exception("error")
            val result: Result<Int> = Failure(exception = exception, message = "error")
            var transformCalled = false
            val mapped =
                result.mapSuspend {
                    transformCalled = true
                    it * 2
                }
            checkIs<Failure>(mapped)
            assertFalse(transformCalled)
        }

    // ========== Helper Constructors ==========

    @Test
    fun `networkError creates failure with correct code and default message`() {
        val result = networkError()
        assertEquals("Network unavailable", result.message)
        assertEquals(ErrorCode.NETWORK_UNAVAILABLE, result.errorCode)
        assertNull(result.exception)
    }

    @Test
    fun `networkError creates failure with custom message and exception`() {
        val exception = RuntimeException("Connection refused")
        val result = networkError(message = "No internet connection", exception = exception)
        assertEquals("No internet connection", result.message)
        assertEquals(ErrorCode.NETWORK_UNAVAILABLE, result.errorCode)
        assertEquals(exception, result.exception)
    }

    @Test
    fun `unauthorizedError creates failure with correct code`() {
        val result = unauthorizedError()
        assertEquals("Session expired", result.message)
        assertEquals(ErrorCode.UNAUTHORIZED, result.errorCode)
    }

    @Test
    fun `notFoundError creates failure with correct code`() {
        val result = notFoundError()
        assertEquals("Resource not found", result.message)
        assertEquals(ErrorCode.NOT_FOUND, result.errorCode)
    }

    @Test
    fun `serverError creates failure with correct code`() {
        val exception = RuntimeException("500 Internal Server Error")
        val result = serverError(message = "Server error", exception = exception)
        assertEquals("Server error", result.message)
        assertEquals(ErrorCode.SERVER_ERROR, result.errorCode)
        assertEquals(exception, result.exception)
    }
}
