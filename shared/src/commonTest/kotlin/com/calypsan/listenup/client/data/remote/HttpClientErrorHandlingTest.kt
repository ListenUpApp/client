package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.error.AppException
import com.calypsan.listenup.client.core.error.AuthError
import com.calypsan.listenup.client.core.error.ServerError
import com.calypsan.listenup.client.test.http.testMockEngine
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration tests for [installListenUpErrorHandling], the response-validation
 * extension installed on every [HttpClient]. Verifies Ktor HTTP responses flow
 * through [ErrorMapper][com.calypsan.listenup.client.core.error.ErrorMapper]
 * into [AppException]-wrapped [AppError][com.calypsan.listenup.client.core.error.AppError]
 * at a single boundary.
 *
 * See Finding 01 D6 for motivation: `expectSuccess` is off today, so a 500 returning
 * HTML is caught by `kotlinx.serialization` and misclassified as a data error rather
 * than a server error.
 */
class HttpClientErrorHandlingTest {
    private fun client(block: com.calypsan.listenup.client.test.http.TestMockEngineBuilder.() -> Unit): HttpClient =
        HttpClient(testMockEngine(block)) {
            installListenUpErrorHandling()
        }

    @Test
    fun successfulResponsePassesThrough() =
        runTest {
            val c = client { respondJson("/ok") { """{"status":"ok"}""" } }

            val response = c.get("http://unit.test/ok")
            assertTrue(response.status.isSuccess())
            assertEquals("""{"status":"ok"}""", response.bodyAsText())
        }

    @Test
    fun serverErrorResponseThrowsAppExceptionWithServerError500() =
        runTest {
            val c = client { respondStatus("/boom", HttpStatusCode.InternalServerError) }

            val thrown = assertFailsWith<AppException> { c.get("http://unit.test/boom") }
            val err = thrown.error
            assertIs<ServerError>(err)
            assertEquals(500, err.statusCode)
        }

    @Test
    fun unauthorizedResponseThrowsAppExceptionWithServerError401() =
        runTest {
            // ErrorMapper maps 401 to ServerError with statusCode=401 (not AuthError — auth
            // is a higher-level concept: 401 on an unauthenticated endpoint isn't "auth
            // expired," so the boundary stays neutral). Auth-specific handling upgrades
            // this to AuthError at the repository layer.
            val c = client { respondStatus("/secret", HttpStatusCode.Unauthorized) }

            val thrown = assertFailsWith<AppException> { c.get("http://unit.test/secret") }
            val err = thrown.error
            assertIs<ServerError>(err)
            assertEquals(401, err.statusCode)
        }

    @Test
    fun notFoundResponseThrowsAppExceptionWithServerError404() =
        runTest {
            val c = client { respondStatus("/missing", HttpStatusCode.NotFound) }

            val thrown = assertFailsWith<AppException> { c.get("http://unit.test/missing") }
            val err = thrown.error
            assertIs<ServerError>(err)
            assertEquals(404, err.statusCode)
        }

    @Test
    fun appExceptionPreservesErrorThroughCausalChain() =
        runTest {
            // The AppException wraps ErrorMapper's output without losing the typed AppError.
            // Downstream catch sites read exception.error to get the already-categorised AppError.
            val c = client { respondStatus("/forbidden", HttpStatusCode.Forbidden) }

            val thrown = assertFailsWith<AppException> { c.get("http://unit.test/forbidden") }
            // The shape is "caught AppException → read .error → switch on AppError subtype"
            when (val e = thrown.error) {
                is ServerError -> assertEquals(403, e.statusCode)
                is AuthError -> error("403 must map to ServerError(403), not AuthError")
                else -> error("unexpected error variant: $e")
            }
        }
}
