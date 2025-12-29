package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.data.remote.ApiClientFactory
import com.calypsan.listenup.client.data.remote.model.SSEBookDeletedEvent
import com.calypsan.listenup.client.data.remote.model.SSEBookEvent
import com.calypsan.listenup.client.data.remote.model.SSEEvent
import com.calypsan.listenup.client.data.remote.model.SSELibraryScanCompletedEvent
import com.calypsan.listenup.client.data.remote.model.SSELibraryScanStartedEvent
import com.calypsan.listenup.client.data.remote.model.SSEUserApprovedEvent
import com.calypsan.listenup.client.data.remote.model.SSEUserData
import com.calypsan.listenup.client.data.remote.model.SSEUserPendingEvent
import com.calypsan.listenup.client.data.repository.SettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Manages Server-Sent Events (SSE) connection for real-time library updates.
 *
 * SSE Connection Lifecycle:
 * 1. connect() opens SSE stream to GET /api/v1/sync/stream
 * 2. Parses incoming SSE events and emits to eventFlow
 * 3. Automatically reconnects on connection loss (exponential backoff)
 * 4. disconnect() closes stream and cancels reconnection
 *
 * Event Flow Architecture:
 * - Events emitted as sealed SSEEventType for type-safe handling
 * - SharedFlow allows multiple collectors (e.g., SyncManager + UI)
 * - Hot flow: Events are broadcast even if no collectors (replay=0)
 *
 * Thread Safety:
 * - All operations are coroutine-safe
 * - Connection job tracked via nullable Job field
 * - Reconnection uses exponential backoff to avoid server overload
 *
 * @property clientFactory Factory for creating authenticated HttpClient
 * @property settingsRepository For retrieving server URL
 * @property scope CoroutineScope for SSE connection lifecycle
 */
class SSEManager(
    private val clientFactory: ApiClientFactory,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) : SSEManagerContract {
    private val _eventFlow = MutableSharedFlow<SSEEventType>(replay = 0, extraBufferCapacity = 64)
    override val eventFlow: SharedFlow<SSEEventType> = _eventFlow.asSharedFlow()

    private var connectionJob: Job? = null
    private var isConnected = false

    // JSON parser for manually parsing SSE event data field
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    companion object {
        private const val SSE_ENDPOINT = "/api/v1/sync/stream"
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
        private const val RECONNECT_BACKOFF_MULTIPLIER = 2.0
    }

    /**
     * Connects to SSE stream and begins emitting events.
     * Safe to call multiple times - will not create duplicate connections.
     *
     * Prerequisites:
     * - Server URL must be configured
     * - User must be authenticated (has tokens)
     *
     * Call this after successful login or sync to start receiving real-time updates.
     */
    override fun connect() {
        if (connectionJob?.isActive == true) {
            return // Already connected
        }

        connectionJob =
            scope.launch {
                var reconnectDelay = INITIAL_RECONNECT_DELAY_MS

                while (isActive) {
                    try {
                        // Check if server URL is configured before attempting connection
                        val serverUrl = settingsRepository.getServerUrl()
                        if (serverUrl == null) {
                            logger.debug { "Server URL not configured, skipping SSE connection" }
                            break // Don't reconnect if not configured
                        }

                        logger.info { "Connecting to SSE stream..." }
                        streamEvents()

                        // If we get here, connection ended gracefully
                        logger.debug { "Connection ended gracefully" }
                        break
                    } catch (e: Exception) {
                        logger.warn(e) { "Connection error" }
                        isConnected = false

                        if (!isActive) {
                            break // Don't reconnect if job was cancelled
                        }

                        // Exponential backoff
                        logger.debug { "Reconnecting in ${reconnectDelay}ms..." }
                        delay(reconnectDelay)
                        reconnectDelay =
                            (reconnectDelay * RECONNECT_BACKOFF_MULTIPLIER)
                                .toLong()
                                .coerceAtMost(MAX_RECONNECT_DELAY_MS)
                    }
                }
            }
    }

    /**
     * Disconnects from SSE stream and stops emitting events.
     */
    override fun disconnect() {
        logger.debug { "Disconnecting..." }
        connectionJob?.cancel()
        connectionJob = null
        isConnected = false
    }

    /**
     * Opens SSE stream and parses events.
     * Throws exception on connection failure (caught by connect() for reconnection).
     */
    private suspend fun streamEvents() {
        logger.trace { "Getting server URL..." }
        val serverUrl =
            settingsRepository.getServerUrl()
                ?: error("Server URL not configured")
        logger.trace { "Server URL: $serverUrl" }

        logger.trace { "Getting streaming HTTP client (no timeouts)..." }
        val httpClient = clientFactory.getStreamingClient()
        logger.trace { "Streaming HTTP client ready" }

        val url = "$serverUrl$SSE_ENDPOINT"
        logger.debug { "Opening streaming connection to: $url" }

        // Use prepareGet + execute for streaming responses that never complete
        httpClient.prepareGet(url).execute { response ->
            logger.debug { "Got HTTP response: ${response.status}" }
            logger.trace { "Response Content-Type: ${response.headers["Content-Type"]}" }

            val channel = response.bodyAsChannel()
            logger.trace { "Got body channel, starting to read events..." }

            try {
                isConnected = true
                parseSSEStream(channel)
                logger.debug { "Stream reading ended gracefully" }
            } finally {
                logger.trace { "Cleaning up channel..." }
                channel.cancel(null)
            }
        }
    }

    /**
     * Parses SSE event stream format.
     *
     * SSE Format (from server):
     * ```
     * event: book.created
     * data: {"timestamp":"...","type":"book.created","data":"{...}"}
     *
     * event: heartbeat
     * data: {"timestamp":"...","type":"heartbeat","data":"{...}"}
     *
     * ```
     *
     * Each event consists of "event:" and "data:" lines, followed by double newline.
     */
    private suspend fun parseSSEStream(channel: ByteReadChannel) {
        var currentEventData = StringBuilder()
        var currentEventType: String? = null

        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break

            when {
                line.isEmpty() -> {
                    // Empty line marks end of event
                    if (currentEventData.isNotEmpty()) {
                        processEvent(currentEventData.toString())
                        currentEventData = StringBuilder()
                        currentEventType = null
                    }
                }

                line.startsWith("event: ") -> {
                    // SSE event type line (we don't use this, data JSON has type field)
                    currentEventType = line.removePrefix("event: ")
                }

                line.startsWith("data: ") -> {
                    // SSE data line
                    currentEventData.append(line.removePrefix("data: "))
                }

                line.startsWith(":") -> {
                    // SSE comment line, ignore
                }

                line.startsWith("id: ") || line.startsWith("retry: ") -> {
                    // SSE id/retry lines, ignore
                }
            }
        }
    }

    /**
     * Processes a complete SSE event JSON string.
     */
    private suspend fun processEvent(eventJson: String) {
        try {
            logger.trace { "Processing event: $eventJson" }

            // Parse the SSE event envelope
            val sseEvent =
                try {
                    json.decodeFromString<SSEEvent>(eventJson)
                } catch (e: Exception) {
                    // Skip non-standard events (like initial "connected" message)
                    logger.trace { "Skipping non-standard event: ${e.message}" }
                    return
                }

            val eventType =
                when (sseEvent.type) {
                    "book.created", "book.updated" -> {
                        val bookEvent = json.decodeFromJsonElement(SSEBookEvent.serializer(), sseEvent.data)
                        if (sseEvent.type == "book.created") {
                            SSEEventType.BookCreated(bookEvent.book)
                        } else {
                            SSEEventType.BookUpdated(bookEvent.book)
                        }
                    }

                    "book.deleted" -> {
                        val deleteEvent = json.decodeFromJsonElement(SSEBookDeletedEvent.serializer(), sseEvent.data)
                        SSEEventType.BookDeleted(deleteEvent.bookId, deleteEvent.deletedAt)
                    }

                    "library.scan_started" -> {
                        val scanEvent =
                            json.decodeFromJsonElement(
                                SSELibraryScanStartedEvent.serializer(),
                                sseEvent.data,
                            )
                        SSEEventType.ScanStarted(scanEvent.libraryId, scanEvent.startedAt)
                    }

                    "library.scan_completed" -> {
                        val scanEvent =
                            json.decodeFromJsonElement(
                                SSELibraryScanCompletedEvent.serializer(),
                                sseEvent.data,
                            )
                        SSEEventType.ScanCompleted(
                            libraryId = scanEvent.libraryId,
                            booksAdded = scanEvent.booksAdded,
                            booksUpdated = scanEvent.booksUpdated,
                            booksRemoved = scanEvent.booksRemoved,
                        )
                    }

                    "heartbeat" -> {
                        // Heartbeat keeps connection alive, no action needed
                        SSEEventType.Heartbeat
                    }

                    "user.pending" -> {
                        val userEvent = json.decodeFromJsonElement(SSEUserPendingEvent.serializer(), sseEvent.data)
                        SSEEventType.UserPending(userEvent.user)
                    }

                    "user.approved" -> {
                        val userEvent = json.decodeFromJsonElement(SSEUserApprovedEvent.serializer(), sseEvent.data)
                        SSEEventType.UserApproved(userEvent.user)
                    }

                    else -> {
                        logger.debug { "Unknown event type: ${sseEvent.type}" }
                        return
                    }
                }

            _eventFlow.emit(eventType)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse event" }
        }
    }
}

/**
 * Sealed class for type-safe SSE event handling.
 * Each event type corresponds to a server event defined in sse/events.go.
 */
sealed class SSEEventType {
    data class BookCreated(
        val book: com.calypsan.listenup.client.data.remote.model.BookResponse,
    ) : SSEEventType()

    data class BookUpdated(
        val book: com.calypsan.listenup.client.data.remote.model.BookResponse,
    ) : SSEEventType()

    data class BookDeleted(
        val bookId: String,
        val deletedAt: String,
    ) : SSEEventType()

    data class ScanStarted(
        val libraryId: String,
        val startedAt: String,
    ) : SSEEventType()

    data class ScanCompleted(
        val libraryId: String,
        val booksAdded: Int,
        val booksUpdated: Int,
        val booksRemoved: Int,
    ) : SSEEventType()

    data object Heartbeat : SSEEventType()

    /**
     * Admin-only: New user registered and is pending approval.
     */
    data class UserPending(
        val user: SSEUserData,
    ) : SSEEventType()

    /**
     * Admin-only: Pending user was approved.
     */
    data class UserApproved(
        val user: SSEUserData,
    ) : SSEEventType()
}
