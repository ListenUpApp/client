package com.calypsan.listenup.client.core.error

import com.calypsan.listenup.client.checkIs
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for ErrorMapper.
 *
 * Tests cover:
 * - Serialization exception mapping
 * - Unknown exception fallback
 * - AppError property correctness
 *
 * Note: Ktor network exceptions (ConnectTimeoutException, SocketTimeoutException,
 * ResponseException, HttpRequestTimeoutException) have complex constructors that
 * make them difficult to instantiate in unit tests. Those mappings are verified
 * through integration tests.
 */
class ErrorMapperTest {
    // ========== Serialization Error Tests ==========

    @Test
    fun `map SerializationException returns DataError`() {
        val exception = SerializationException("Failed to parse JSON")
        val error = ErrorMapper.map(exception)

        val dataError = assertIs<DataError>(error)
        assertEquals("Invalid data format.", dataError.message)
        assertEquals("DATA_ERROR", dataError.code)
    }

    @Test
    fun `DataError is not retryable`() {
        val exception = SerializationException("Parse error")
        val error = ErrorMapper.map(exception)

        val dataError = assertIs<DataError>(error)
        assertEquals(false, dataError.isRetryable)
    }

    @Test
    fun `map SerializationException includes debug info`() {
        val exception = SerializationException("Unexpected JSON token")
        val error = ErrorMapper.map(exception)

        val dataError = assertIs<DataError>(error)
        assertEquals("Unexpected JSON token", dataError.debugInfo)
    }

    // ========== Unknown Error Tests ==========

    @Test
    fun `map unknown exception returns UnknownError`() {
        val exception = IllegalStateException("Something went wrong")
        val error = ErrorMapper.map(exception)

        val unknownError = assertIs<UnknownError>(error)
        assertTrue(unknownError.message.contains("unexpected error"))
    }

    @Test
    fun `UnknownError is retryable`() {
        val exception = RuntimeException("Random error")
        val error = ErrorMapper.map(exception)

        val unknownError = assertIs<UnknownError>(error)
        assertEquals(true, unknownError.isRetryable)
    }

    @Test
    fun `map unknown exception includes exception message`() {
        val exception = RuntimeException("Custom error message")
        val error = ErrorMapper.map(exception)

        val unknownError = assertIs<UnknownError>(error)
        assertTrue(unknownError.message.contains("Custom error message"))
    }

    @Test
    fun `map unknown exception includes stack trace in debug info`() {
        val exception = IllegalArgumentException("Bad argument")
        val error = ErrorMapper.map(exception)

        val unknownError = assertIs<UnknownError>(error)
        assertTrue(unknownError.debugInfo?.contains("IllegalArgumentException") == true)
    }

    @Test
    fun `UnknownError has correct code`() {
        val exception = UnsupportedOperationException("Not supported")
        val error = ErrorMapper.map(exception)

        val unknownError = assertIs<UnknownError>(error)
        assertEquals("UNKNOWN_ERROR", unknownError.code)
    }

    // ========== Custom Exception Tests ==========

    @Test
    fun `map custom exception returns UnknownError`() {
        class CustomException(
            message: String,
        ) : Exception(message)
        val exception = CustomException("Custom domain error")
        val error = ErrorMapper.map(exception)

        checkIs<UnknownError>(error)
    }

    @Test
    fun `map NullPointerException returns UnknownError`() {
        val exception = NullPointerException("null reference")
        val error = ErrorMapper.map(exception)

        checkIs<UnknownError>(error)
    }

    @Test
    fun `map IndexOutOfBoundsException returns UnknownError`() {
        val exception = IndexOutOfBoundsException("index 5 out of bounds")
        val error = ErrorMapper.map(exception)

        checkIs<UnknownError>(error)
    }
}
