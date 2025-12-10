package com.calypsan.listenup.client.core

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
        assertIs<Success<String>>(result)
        assertEquals("hello", result.data)
    }

    @Test
    fun `Failure wraps exception correctly`() {
        val exception = IllegalStateException("test error")
        val result: Result<String> = Failure(exception)
        assertIs<Failure>(result)
        assertEquals(exception, result.exception)
        assertEquals("test error", result.message)
    }

    @Test
    fun `Failure uses default message when exception message is null`() {
        val exception = RuntimeException()
        val result = Failure(exception)
        assertEquals("Unknown error", result.message)
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
        val result: Result<Int> = Failure(Exception("error"))
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
        val result: Result<String> = Failure(Exception("error"))
        assertNull(result.getOrNull())
    }

    @Test
    fun `getOrDefault returns value for Success`() {
        val result: Result<Int> = Success(42)
        assertEquals(42, result.getOrDefault { 0 })
    }

    @Test
    fun `getOrDefault returns default for Failure`() {
        val result: Result<Int> = Failure(Exception("error"))
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
        val result: Result<String> = Failure(exception)
        val thrown = assertFailsWith<IllegalArgumentException> {
            result.getOrThrow()
        }
        assertEquals("invalid", thrown.message)
    }

    // ========== map / flatMap ==========

    @Test
    fun `map transforms Success value`() {
        val result: Result<Int> = Success(21)
        val mapped = result.map { it * 2 }
        assertIs<Success<Int>>(mapped)
        assertEquals(42, mapped.data)
    }

    @Test
    fun `map preserves Failure`() {
        val exception = Exception("error")
        val result: Result<Int> = Failure(exception)
        val mapped = result.map { it * 2 }
        assertIs<Failure>(mapped)
        assertEquals(exception, mapped.exception)
    }

    @Test
    fun `flatMap chains successful Results`() {
        val result: Result<Int> = Success(21)
        val chained = result.flatMap { Success(it * 2) }
        assertIs<Success<Int>>(chained)
        assertEquals(42, chained.data)
    }

    @Test
    fun `flatMap short-circuits on initial Failure`() {
        val exception = Exception("first error")
        val result: Result<Int> = Failure(exception)
        var transformCalled = false
        val chained = result.flatMap {
            transformCalled = true
            Success(it * 2)
        }
        assertIs<Failure>(chained)
        assertEquals(exception, chained.exception)
        assertFalse(transformCalled)
    }

    @Test
    fun `flatMap propagates Failure from transform`() {
        val result: Result<Int> = Success(42)
        val secondException = Exception("second error")
        val chained = result.flatMap { Failure(secondException) }
        assertIs<Failure>(chained)
        assertEquals(secondException, chained.exception)
    }

    // ========== onSuccess / onFailure ==========

    @Test
    fun `onSuccess executes action for Success`() {
        var captured: Int? = null
        val result: Result<Int> = Success(42)
        val returned = result.onSuccess { captured = it }
        assertEquals(42, captured)
        assertEquals(result, returned)
    }

    @Test
    fun `onSuccess does not execute action for Failure`() {
        var called = false
        val result: Result<Int> = Failure(Exception("error"))
        result.onSuccess { called = true }
        assertFalse(called)
    }

    @Test
    fun `onFailure executes action for Failure`() {
        var capturedException: Exception? = null
        val exception = Exception("error")
        val result: Result<Int> = Failure(exception)
        val returned = result.onFailure { capturedException = it }
        assertEquals(exception, capturedException)
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
        assertIs<Success<Int>>(recovered)
        assertEquals(42, recovered.data)
    }

    @Test
    fun `recover transforms Failure to Success`() {
        val result: Result<Int> = Failure(Exception("error"))
        val recovered = result.recover { -1 }
        assertIs<Success<Int>>(recovered)
        assertEquals(-1, recovered.data)
    }

    // ========== runCatching (non-suspending) ==========

    @Test
    fun `runCatching returns Success for successful block`() {
        val result = com.calypsan.listenup.client.core.runCatching { 42 }
        assertIs<Success<Int>>(result)
        assertEquals(42, result.data)
    }

    @Test
    fun `runCatching returns Failure for throwing block`() {
        val result = com.calypsan.listenup.client.core.runCatching<Int> {
            throw IllegalStateException("error")
        }
        assertIs<Failure>(result)
        assertIs<IllegalStateException>(result.exception)
    }

    // ========== suspendRunCatching (CRITICAL: CancellationException handling) ==========

    @Test
    fun `suspendRunCatching returns Success for successful suspend block`() =
        runTest {
            val result = suspendRunCatching { "async result" }
            assertIs<Success<String>>(result)
            assertEquals("async result", result.data)
        }

    @Test
    fun `suspendRunCatching returns Failure for throwing suspend block`() =
        runTest {
            val result = suspendRunCatching<Int> {
                throw IllegalArgumentException("async error")
            }
            assertIs<Failure>(result)
            assertIs<IllegalArgumentException>(result.exception)
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

            val job = launch {
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
            val mapped = result.mapSuspend {
                kotlinx.coroutines.delay(1)
                it * 2
            }
            assertIs<Success<Int>>(mapped)
            assertEquals(42, mapped.data)
        }

    @Test
    fun `mapSuspend preserves Failure`() =
        runTest {
            val exception = Exception("error")
            val result: Result<Int> = Failure(exception)
            var transformCalled = false
            val mapped = result.mapSuspend {
                transformCalled = true
                it * 2
            }
            assertIs<Failure>(mapped)
            assertFalse(transformCalled)
        }
}
