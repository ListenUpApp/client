@file:OptIn(kotlin.time.ExperimentalTime::class)
@file:Suppress("MagicNumber", "NestedBlockDepth")

package com.calypsan.listenup.client.download

import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.remote.PlaybackApiContract
import com.calypsan.listenup.client.domain.repository.DownloadRepository
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import com.calypsan.listenup.client.playback.AudioCapabilityDetector
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem

private val logger = KotlinLogging.logger {}

private const val PROGRESS_INTERVAL_MS = 500L
private const val BUFFER_SIZE = 8 * 1024
private const val PROGRESS_BYTES_INTERVAL = 256 * 1024L // 256KB — emit progress at least every quarter MB

/**
 * Core download logic for a single audio file, extracted from [DownloadWorker] so it can be
 * driven from commonTest/jvmTest without WorkManager or an Android Context.
 *
 * Features:
 * - Codec negotiation (downloads transcoded variant if needed)
 * - Resume support (Range headers)
 * - Progress updates via [setProgress] lambda
 * - Cancellation handling via [isStopped] lambda
 *
 * Phase C scope: preserves the existing transcode-poll path inside [resolveDownloadUrl].
 * Phase D rewrites that path to use markWaitingForServer + SSE re-enqueue.
 */
@Suppress("CyclomaticComplexMethod", "CognitiveComplexMethod")
internal suspend fun downloadAudioFile(
    audioFileId: String,
    bookId: String,
    filename: String,
    expectedSize: Long,
    httpClient: HttpClient,
    repository: DownloadRepository,
    fileManager: DownloadFileManager,
    playbackApi: PlaybackApiContract,
    playbackPreferences: PlaybackPreferences,
    capabilityDetector: AudioCapabilityDetector,
    isStopped: () -> Boolean = { false },
    setProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
) = withContext(Dispatchers.IO) {
    // Resolve the download URL (relative — Ktor's defaultRequest provides the base).
    // The transcode-poll path inside resolveDownloadUrl is preserved for Phase C; Phase D
    // rewrites it to use markWaitingForServer + SSE re-enqueue.
    val url =
        resolveDownloadUrl(
            bookId = bookId,
            audioFileId = audioFileId,
            playbackApi = playbackApi,
            playbackPreferences = playbackPreferences,
            capabilityDetector = capabilityDetector,
            isStopped = isStopped,
        )

    val destPath = fileManager.getDownloadPath(bookId, audioFileId, filename)
    val tempPath = fileManager.getTempPath(bookId, audioFileId, filename)

    // Resume support: if a partial tempFile exists, send Range header.
    val startByte =
        if (SystemFileSystem.exists(tempPath)) {
            SystemFileSystem.metadataOrNull(tempPath)?.size ?: 0L
        } else {
            0L
        }

    httpClient
        .prepareGet(url) {
            if (startByte > 0) {
                header(HttpHeaders.Range, "bytes=$startByte-")
                logger.debug { "Resuming download from byte $startByte" }
            }
        }.execute { response ->
            // HttpResponseValidator (installed by ApiClientFactory) raises typed exceptions on
            // non-2xx; we only see successful or partial-content responses here. Status code 206
            // is success per RFC 7233; treat it the same as 200.

            val contentLength = response.contentLength() ?: -1L
            val totalSize =
                if (startByte > 0 && response.status == HttpStatusCode.PartialContent) {
                    startByte + contentLength
                } else {
                    contentLength
                }

            // Update total size in DB up front so the UI shows progress against the right denominator.
            if (totalSize > 0) {
                repository.updateProgress(audioFileId, startByte, totalSize)
            }

            // Stream the body into the temp file. Append mode iff we're resuming.
            val channel = response.bodyAsChannel()
            val sink = SystemFileSystem.sink(tempPath, append = startByte > 0).buffered()
            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var totalBytesRead = startByte
                var lastProgressUpdate = 0L
                var lastProgressBytes = startByte

                while (!channel.isClosedForRead) {
                    if (isStopped()) {
                        throw CancellationException("Download stopped")
                    }

                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    sink.write(buffer, 0, read)
                    totalBytesRead += read

                    val now = currentEpochMilliseconds()
                    val sinceLastProgress = totalBytesRead - lastProgressBytes
                    if (now - lastProgressUpdate > PROGRESS_INTERVAL_MS ||
                        sinceLastProgress >= PROGRESS_BYTES_INTERVAL
                    ) {
                        if (totalSize > 0) {
                            repository.updateProgress(audioFileId, totalBytesRead, totalSize)
                        }
                        setProgress(totalBytesRead, totalSize)
                        lastProgressUpdate = now
                        lastProgressBytes = totalBytesRead
                    }
                }
            } finally {
                sink.close()
            }

            // Verify size if known.
            val writtenSize = SystemFileSystem.metadataOrNull(tempPath)?.size ?: 0L
            if (expectedSize > 0 && writtenSize != expectedSize) {
                SystemFileSystem.delete(tempPath)
                throw IOException("Size mismatch: expected $expectedSize, got $writtenSize")
            }

            // Move temp to final destination via FileManager.
            if (!fileManager.moveFile(tempPath, destPath)) {
                throw IOException("Failed to move temp file to destination")
            }

            // Mark complete via repository.
            repository.markCompleted(
                audioFileId = audioFileId,
                localPath = destPath.toString(),
                completedAt = currentEpochMilliseconds(),
            )
        }
}

/**
 * Resolve the correct download URL via the prepare endpoint.
 *
 * Returns a relative URL (Ktor's defaultRequest provides the base server URL via the
 * authenticated HttpClient). Phase C preserves the existing 30-minute polling loop;
 * Phase D rewrites this path to use markWaitingForServer + SSE re-enqueue.
 */
private suspend fun resolveDownloadUrl(
    bookId: String,
    audioFileId: String,
    playbackApi: PlaybackApiContract,
    playbackPreferences: PlaybackPreferences,
    capabilityDetector: AudioCapabilityDetector,
    isStopped: () -> Boolean,
): String {
    val capabilities = capabilityDetector.getSupportedCodecs()
    val spatial = playbackPreferences.getSpatialPlayback()
    val result = playbackApi.preparePlayback(bookId, audioFileId, capabilities, spatial)

    if (result !is Success) {
        logger.warn { "Prepare call failed, using original URL" }
        return "/api/v1/books/$bookId/audio/$audioFileId"
    }

    val response = result.data

    // Phase C preserves the existing transcode-poll path. Phase D rewrites this.
    if (!response.ready && response.transcodeJobId != null) {
        logger.info {
            "Transcoding in progress for $audioFileId, waiting... " +
                "(jobId=${response.transcodeJobId}, progress=${response.progress}%)"
        }

        val maxWaitMs = 30 * 60 * 1000L
        val startTime = currentEpochMilliseconds()
        var lastProgress = response.progress

        while (currentEpochMilliseconds() - startTime < maxWaitMs) {
            if (isStopped()) {
                throw CancellationException("Download cancelled while waiting for transcode")
            }

            delay(5000)

            val checkResult = playbackApi.preparePlayback(bookId, audioFileId, capabilities, spatial)
            if (checkResult is Success) {
                val checkResponse = checkResult.data
                if (checkResponse.ready) {
                    logger.info { "Transcode completed for $audioFileId" }
                    return relativizeUrl(checkResponse.streamUrl)
                }
                if (checkResponse.progress > lastProgress) {
                    logger.debug { "Transcode progress: ${checkResponse.progress}%" }
                    lastProgress = checkResponse.progress
                }
            }
        }

        logger.warn { "Transcode timeout for $audioFileId, using original URL" }
        return "/api/v1/books/$bookId/audio/$audioFileId"
    }

    logger.debug {
        "Using ${response.variant} variant for $audioFileId (codec: ${response.codec})"
    }
    return relativizeUrl(response.streamUrl)
}

/** Strip an absolute server URL prefix off [streamUrl] so we always pass a relative URL to Ktor. */
internal fun relativizeUrl(streamUrl: String): String =
    when {
        streamUrl.startsWith("/") -> {
            streamUrl
        }

        streamUrl.startsWith("http") -> {
            val pathStart = streamUrl.indexOf('/', startIndex = "https://".length)
            if (pathStart > 0) streamUrl.substring(pathStart) else "/$streamUrl"
        }

        else -> {
            "/$streamUrl"
        }
    }
