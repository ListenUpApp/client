package com.calypsan.listenup.client.download

import com.calypsan.listenup.client.core.appJson
import com.calypsan.listenup.client.data.local.db.DownloadEntity
import com.calypsan.listenup.client.data.local.db.DownloadState
import com.calypsan.listenup.client.data.repository.FakeDownloadRepository
import com.calypsan.listenup.client.test.http.testMockEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Seam-level tests for the Phase C Ktor-based download path. Uses [testMockEngine] for HTTP
 * responses and [FakeDownloadRepository] for state.
 *
 * Per project memory `feedback_fakes_for_seams.md`: hand-rolled fake + MockEngine, not mokkery.
 *
 * Phase C scope: HTTP migration only. Phase D rewrites the transcode-poll path; Phase C
 * preserves it (scenario "preparePlayback returns !ready" locks current polling behavior as a
 * regression marker).
 *
 * **Phase C placeholder:** the actual call to the worker's download function is deferred to
 * Task 5 once Task 4 has migrated the worker to inject [HttpClient]. This file scaffolds the
 * test fixtures (entity builder, productionLikeClient helper) so Task 5 just fills in bodies.
 */
class DownloadWorkerLogicTest {
    private fun entity(
        audioFileId: String,
        bookId: String = "book-1",
        state: DownloadState = DownloadState.QUEUED,
        totalBytes: Long = 1000L,
        downloadedBytes: Long = 0L,
    ) = DownloadEntity(
        audioFileId = audioFileId,
        bookId = bookId,
        filename = "$audioFileId.mp3",
        fileIndex = 0,
        state = state,
        localPath = null,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        queuedAt = 0L,
        startedAt = null,
        completedAt = null,
        errorMessage = null,
        retryCount = 0,
    )

    private fun productionLikeClient(engine: HttpClientEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) { json(appJson) }
            @Suppress("MagicNumber")
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
            // No Auth plugin in this base setup; tests that need 401-handling install it themselves.
            // No HttpRequestRetry; tests that need retry behavior install it themselves.
        }

    @Test
    fun `placeholder — fixtures compile and FakeDownloadRepository instantiates`() =
        runTest {
            val fakeRepo = FakeDownloadRepository(initial = listOf(entity("file-1")))
            val engine =
                testMockEngine {
                    handle("/api/v1/books/book-1/audio/file-1/stream") { _ ->
                        respond(
                            content = ByteArray(1000) { 0x42 },
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentLength, "1000"),
                        )
                    }
                }
            val httpClient = productionLikeClient(engine)

            // Placeholder assertion — Task 5 replaces this with real worker-invocation + state check.
            assertEquals(1, fakeRepo.entities.size)
            assertEquals(DownloadState.QUEUED, fakeRepo.entities.single().state)
            // Suppress unused-variable warning until Task 5 wires httpClient into the worker call.
            @Suppress("UNUSED_EXPRESSION")
            httpClient
        }
}
