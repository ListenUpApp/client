package com.calypsan.listenup.client.core

import com.calypsan.listenup.client.core.error.AppError
import com.calypsan.listenup.client.core.error.AuthError
import com.calypsan.listenup.client.core.error.DataError
import com.calypsan.listenup.client.core.error.NetworkError
import com.calypsan.listenup.client.core.error.ServerError
import com.calypsan.listenup.client.core.error.UnknownError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for [AppResult] — the canonical result type.
 *
 * See Finding 01 D1 for motivation: the codebase previously had three parallel error
 * models ([Result], `AsyncState`, [AppError]) with no conversion path. [AppResult] is
 * the single sealed hierarchy carrying [AppError] directly.
 */
class AppResultTest {
    @Test
    fun successHoldsData() {
        val result: AppResult<String> = AppResult.Success("hello")
        assertIs<AppResult.Success<String>>(result)
        assertEquals("hello", result.data)
    }

    @Test
    fun failureHoldsAppError() {
        val err: AppError = NetworkError(debugInfo = "timeout")
        val result: AppResult<String> = AppResult.Failure(err)
        assertIs<AppResult.Failure>(result)
        assertSame(err, result.error)
    }

    @Test
    fun mapTransformsSuccess() {
        val result: AppResult<Int> = AppResult.Success(5)
        val mapped = result.map { it * 2 }
        assertEquals(AppResult.Success(10), mapped)
    }

    @Test
    fun mapPreservesFailure() {
        val err: AppError = NetworkError()
        val result: AppResult<Int> = AppResult.Failure(err)
        val mapped = result.map { it * 2 }
        assertIs<AppResult.Failure>(mapped)
        assertSame(err, mapped.error)
    }

    @Test
    fun flatMapChainsSuccesses() {
        val result: AppResult<Int> = AppResult.Success(5)
        val chained: AppResult<String> = result.flatMap { AppResult.Success(it.toString()) }
        assertEquals(AppResult.Success("5"), chained)
    }

    @Test
    fun flatMapShortCircuitsOnFailure() {
        val err: AppError = DataError(message = "bad")
        val result: AppResult<Int> = AppResult.Failure(err)
        val chained: AppResult<String> = result.flatMap { error("should not be called") }
        assertIs<AppResult.Failure>(chained)
        assertSame(err, chained.error)
    }

    @Test
    fun flatMapSurfacesInnerFailure() {
        val inner: AppError = AuthError()
        val result: AppResult<Int> = AppResult.Success(5)
        val chained = result.flatMap<Int, String> { AppResult.Failure(inner) }
        assertIs<AppResult.Failure>(chained)
        assertSame(inner, chained.error)
    }

    @Test
    fun foldDispatchesSuccess() {
        val result: AppResult<Int> = AppResult.Success(5)
        val folded = result.fold(onSuccess = { "got $it" }, onFailure = { "err ${it.code}" })
        assertEquals("got 5", folded)
    }

    @Test
    fun foldDispatchesFailure() {
        val result: AppResult<Int> = AppResult.Failure(AuthError())
        val folded = result.fold(onSuccess = { "got $it" }, onFailure = { "err ${it.code}" })
        assertEquals("err AUTH_ERROR", folded)
    }

    @Test
    fun getOrNullReturnsDataOnSuccess() {
        val result: AppResult<Int> = AppResult.Success(5)
        assertEquals(5, result.getOrNull())
    }

    @Test
    fun getOrNullReturnsNullOnFailure() {
        val result: AppResult<Int> = AppResult.Failure(NetworkError())
        assertNull(result.getOrNull())
    }

    @Test
    fun errorOrNullReturnsErrorOnFailure() {
        val err: AppError = NetworkError()
        val result: AppResult<Int> = AppResult.Failure(err)
        assertSame(err, result.errorOrNull())
    }

    @Test
    fun errorOrNullReturnsNullOnSuccess() {
        val result: AppResult<Int> = AppResult.Success(5)
        assertNull(result.errorOrNull())
    }

    @Test
    fun onSuccessRunsOnlyOnSuccess() {
        var ran = false
        val result: AppResult<Int> = AppResult.Success(5)
        result.onSuccess { ran = true }
        assertTrue(ran)
    }

    @Test
    fun onFailureRunsOnlyOnFailure() {
        var captured: AppError? = null
        val err: AppError = AuthError()
        AppResult.Failure(err).onFailure { captured = it }
        assertSame(err, captured)
    }

    @Test
    fun failureFromThrowableMapsViaErrorMapper() {
        val ex = IllegalStateException("boom")
        val failure = Failure(ex)
        assertIs<UnknownError>(failure.error)
    }

    @Test
    fun failureFromAppExceptionPreservesTypedError() {
        val originalError: AppError = AuthError()
        val ex =
            com.calypsan.listenup.client.core.error
                .AppException(originalError)
        val failure = Failure(ex)
        assertSame(originalError, failure.error)
    }

    @Test
    fun validationErrorBuildsDataErrorFailure() {
        val failure = validationError("bad input")
        assertIs<DataError>(failure.error)
        assertEquals("bad input", failure.message)
    }

    @Test
    fun networkErrorHelperBuildsNetworkErrorFailure() {
        val failure = networkError("offline")
        assertIs<NetworkError>(failure.error)
    }

    @Test
    fun unauthorizedHelperBuildsAuthErrorFailure() {
        val failure = unauthorizedError()
        assertIs<AuthError>(failure.error)
    }
}
