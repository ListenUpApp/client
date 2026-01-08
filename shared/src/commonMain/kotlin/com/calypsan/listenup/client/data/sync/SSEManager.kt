package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.data.remote.ApiClientFactory
import com.calypsan.listenup.client.data.remote.model.SSEActivityCreatedEvent
import com.calypsan.listenup.client.data.remote.model.SSEBookDeletedEvent
import com.calypsan.listenup.client.data.remote.model.SSEBookEvent
import com.calypsan.listenup.client.data.remote.model.SSEBookTagAddedEvent
import com.calypsan.listenup.client.data.remote.model.SSEBookTagRemovedEvent
import com.calypsan.listenup.client.data.remote.model.SSECollectionBookAddedEvent
import com.calypsan.listenup.client.data.remote.model.SSECollectionBookRemovedEvent
import com.calypsan.listenup.client.data.remote.model.SSECollectionCreatedEvent
import com.calypsan.listenup.client.data.remote.model.SSECollectionDeletedEvent
import com.calypsan.listenup.client.data.remote.model.SSECollectionUpdatedEvent
import com.calypsan.listenup.client.data.remote.model.SSEEvent
import com.calypsan.listenup.client.data.remote.model.SSEInboxBookAddedEvent
import com.calypsan.listenup.client.data.remote.model.SSEInboxBookReleasedEvent
import com.calypsan.listenup.client.data.remote.model.SSELensBookAddedEvent
import com.calypsan.listenup.client.data.remote.model.SSELensBookRemovedEvent
import com.calypsan.listenup.client.data.remote.model.SSELensCreatedEvent
import com.calypsan.listenup.client.data.remote.model.SSELensDeletedEvent
import com.calypsan.listenup.client.data.remote.model.SSELensUpdatedEvent
import com.calypsan.listenup.client.data.remote.model.SSELibraryScanCompletedEvent
import com.calypsan.listenup.client.data.remote.model.SSELibraryScanStartedEvent
import com.calypsan.listenup.client.data.remote.model.SSEListeningEventCreatedEvent
import com.calypsan.listenup.client.data.remote.model.SSEProfileUpdatedEvent
import com.calypsan.listenup.client.data.remote.model.SSEProgressUpdatedEvent
import com.calypsan.listenup.client.data.remote.model.SSEReadingSessionUpdatedEvent
import com.calypsan.listenup.client.data.remote.model.SSESessionEndedEvent
import com.calypsan.listenup.client.data.remote.model.SSESessionStartedEvent
import com.calypsan.listenup.client.data.remote.model.SSETagCreatedEvent
import com.calypsan.listenup.client.data.remote.model.SSEUserApprovedEvent
import com.calypsan.listenup.client.data.remote.model.SSEUserData
import com.calypsan.listenup.client.data.remote.model.SSEUserDeletedEvent
import com.calypsan.listenup.client.data.remote.model.SSEUserPendingEvent
import com.calypsan.listenup.client.data.remote.model.SSEUserStatsUpdatedEvent
import com.calypsan.listenup.client.domain.repository.ServerConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Manages Server-Sent Events (SSE) connection for real-time library updates.
 *
 * SSE Connection Lifecycle:
 * 1. connect() opens SSE stream to GET /api/v1/sync/events
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
    private val serverConfig: ServerConfig,
    private val scope: CoroutineScope,
) : SSEManagerContract {
    private val _eventFlow = MutableSharedFlow<SSEEventType>(replay = 0, extraBufferCapacity = 64)
    override val eventFlow: SharedFlow<SSEEventType> = _eventFlow.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)

    /** Observable connection state for UI/monitoring. Thread-safe. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var connectionJob: Job? = null

    /** Tracks if we've been connected before - used to detect reconnection vs initial connection. */
    private var hasBeenConnected = false

    // JSON parser for manually parsing SSE event data field
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    companion object {
        private const val SSE_ENDPOINT = "/api/v1/sync/events"
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
                        val serverUrl = serverConfig.getServerUrl()
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
                        _isConnected.value = false

                        // Check for authentication errors - don't retry on 401/403
                        if (e is ResponseException) {
                            val statusCode = e.response.status.value
                            if (statusCode == 401 || statusCode == 403) {
                                logger.warn {
                                    "SSE connection failed with auth error ($statusCode), not retrying"
                                }
                                break // Don't reconnect - auth is invalid
                            }
                        }

                        logger.warn(e) { "Connection error" }

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
        _isConnected.value = false
    }

    /**
     * Opens SSE stream and parses events.
     * Throws exception on connection failure (caught by connect() for reconnection).
     */
    private suspend fun streamEvents() {
        logger.trace { "Getting server URL..." }
        val serverUrl =
            serverConfig.getServerUrl()
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
                // Check if this is a reconnection (we were previously connected)
                val isReconnection = hasBeenConnected

                _isConnected.value = true
                hasBeenConnected = true

                // Emit reconnection event so SyncManager can trigger delta sync
                if (isReconnection) {
                    logger.info { "SSE reconnected - emitting Reconnected event for delta sync" }
                    _eventFlow.emit(SSEEventType.Reconnected)
                }

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

                    "user.deleted" -> {
                        val userEvent = json.decodeFromJsonElement(SSEUserDeletedEvent.serializer(), sseEvent.data)
                        SSEEventType.UserDeleted(
                            userId = userEvent.userId,
                            reason = userEvent.reason,
                        )
                    }

                    "collection.created" -> {
                        val collectionEvent =
                            json.decodeFromJsonElement(SSECollectionCreatedEvent.serializer(), sseEvent.data)
                        SSEEventType.CollectionCreated(
                            id = collectionEvent.id,
                            name = collectionEvent.name,
                            bookCount = collectionEvent.bookCount,
                        )
                    }

                    "collection.updated" -> {
                        val collectionEvent =
                            json.decodeFromJsonElement(SSECollectionUpdatedEvent.serializer(), sseEvent.data)
                        SSEEventType.CollectionUpdated(
                            id = collectionEvent.id,
                            name = collectionEvent.name,
                            bookCount = collectionEvent.bookCount,
                        )
                    }

                    "collection.deleted" -> {
                        val collectionEvent =
                            json.decodeFromJsonElement(SSECollectionDeletedEvent.serializer(), sseEvent.data)
                        SSEEventType.CollectionDeleted(
                            id = collectionEvent.id,
                            name = collectionEvent.name,
                        )
                    }

                    "collection.book_added" -> {
                        val collectionEvent =
                            json.decodeFromJsonElement(SSECollectionBookAddedEvent.serializer(), sseEvent.data)
                        SSEEventType.CollectionBookAdded(
                            collectionId = collectionEvent.collectionId,
                            collectionName = collectionEvent.collectionName,
                            bookId = collectionEvent.bookId,
                        )
                    }

                    "collection.book_removed" -> {
                        val collectionEvent =
                            json.decodeFromJsonElement(SSECollectionBookRemovedEvent.serializer(), sseEvent.data)
                        SSEEventType.CollectionBookRemoved(
                            collectionId = collectionEvent.collectionId,
                            collectionName = collectionEvent.collectionName,
                            bookId = collectionEvent.bookId,
                        )
                    }

                    "lens.created" -> {
                        val lensEvent = json.decodeFromJsonElement(SSELensCreatedEvent.serializer(), sseEvent.data)
                        SSEEventType.LensCreated(
                            id = lensEvent.id,
                            ownerId = lensEvent.ownerId,
                            name = lensEvent.name,
                            description = lensEvent.description,
                            bookCount = lensEvent.bookCount,
                            ownerDisplayName = lensEvent.ownerDisplayName,
                            ownerAvatarColor = lensEvent.ownerAvatarColor,
                            createdAt = lensEvent.createdAt,
                            updatedAt = lensEvent.updatedAt,
                        )
                    }

                    "lens.updated" -> {
                        val lensEvent = json.decodeFromJsonElement(SSELensUpdatedEvent.serializer(), sseEvent.data)
                        SSEEventType.LensUpdated(
                            id = lensEvent.id,
                            ownerId = lensEvent.ownerId,
                            name = lensEvent.name,
                            description = lensEvent.description,
                            bookCount = lensEvent.bookCount,
                            ownerDisplayName = lensEvent.ownerDisplayName,
                            ownerAvatarColor = lensEvent.ownerAvatarColor,
                            createdAt = lensEvent.createdAt,
                            updatedAt = lensEvent.updatedAt,
                        )
                    }

                    "lens.deleted" -> {
                        val lensEvent = json.decodeFromJsonElement(SSELensDeletedEvent.serializer(), sseEvent.data)
                        SSEEventType.LensDeleted(
                            id = lensEvent.id,
                            ownerId = lensEvent.ownerId,
                        )
                    }

                    "lens.book_added" -> {
                        val lensEvent = json.decodeFromJsonElement(SSELensBookAddedEvent.serializer(), sseEvent.data)
                        SSEEventType.LensBookAdded(
                            lensId = lensEvent.lensId,
                            ownerId = lensEvent.ownerId,
                            bookId = lensEvent.bookId,
                            bookCount = lensEvent.bookCount,
                        )
                    }

                    "lens.book_removed" -> {
                        val lensEvent = json.decodeFromJsonElement(SSELensBookRemovedEvent.serializer(), sseEvent.data)
                        SSEEventType.LensBookRemoved(
                            lensId = lensEvent.lensId,
                            ownerId = lensEvent.ownerId,
                            bookId = lensEvent.bookId,
                            bookCount = lensEvent.bookCount,
                        )
                    }

                    "tag.created" -> {
                        val tagEvent = json.decodeFromJsonElement(SSETagCreatedEvent.serializer(), sseEvent.data)
                        SSEEventType.TagCreated(
                            id = tagEvent.id,
                            slug = tagEvent.slug,
                            bookCount = tagEvent.bookCount,
                        )
                    }

                    "book.tag_added" -> {
                        val tagEvent = json.decodeFromJsonElement(SSEBookTagAddedEvent.serializer(), sseEvent.data)
                        SSEEventType.BookTagAdded(
                            bookId = tagEvent.bookId,
                            tagId = tagEvent.tag.id,
                            tagSlug = tagEvent.tag.slug,
                            tagBookCount = tagEvent.tag.bookCount,
                        )
                    }

                    "book.tag_removed" -> {
                        val tagEvent = json.decodeFromJsonElement(SSEBookTagRemovedEvent.serializer(), sseEvent.data)
                        SSEEventType.BookTagRemoved(
                            bookId = tagEvent.bookId,
                            tagId = tagEvent.tag.id,
                            tagSlug = tagEvent.tag.slug,
                            tagBookCount = tagEvent.tag.bookCount,
                        )
                    }

                    "inbox.book_added" -> {
                        val inboxEvent = json.decodeFromJsonElement(SSEInboxBookAddedEvent.serializer(), sseEvent.data)
                        SSEEventType.InboxBookAdded(
                            bookId = inboxEvent.book.id,
                            title = inboxEvent.book.title,
                        )
                    }

                    "inbox.book_released" -> {
                        val inboxEvent =
                            json.decodeFromJsonElement(
                                SSEInboxBookReleasedEvent.serializer(),
                                sseEvent.data,
                            )
                        SSEEventType.InboxBookReleased(
                            bookId = inboxEvent.bookId,
                        )
                    }

                    "listening.progress_updated" -> {
                        val progressEvent =
                            json.decodeFromJsonElement(
                                SSEProgressUpdatedEvent.serializer(),
                                sseEvent.data,
                            )
                        SSEEventType.ProgressUpdated(
                            bookId = progressEvent.bookId,
                            currentPositionMs = progressEvent.currentPositionMs,
                            progress = progressEvent.progress,
                            totalListenTimeMs = progressEvent.totalListenTimeMs,
                            isFinished = progressEvent.isFinished,
                            lastPlayedAt = progressEvent.lastPlayedAt,
                        )
                    }

                    "reading_session.updated" -> {
                        val sessionEvent =
                            json.decodeFromJsonElement(
                                SSEReadingSessionUpdatedEvent.serializer(),
                                sseEvent.data,
                            )
                        SSEEventType.ReadingSessionUpdated(
                            sessionId = sessionEvent.sessionId,
                            bookId = sessionEvent.bookId,
                            isCompleted = sessionEvent.isCompleted,
                            listenTimeMs = sessionEvent.listenTimeMs,
                            finishedAt = sessionEvent.finishedAt,
                        )
                    }

                    "listening.event_created" -> {
                        val listeningEvent =
                            json.decodeFromJsonElement(
                                SSEListeningEventCreatedEvent.serializer(),
                                sseEvent.data,
                            )
                        SSEEventType.ListeningEventCreated(
                            id = listeningEvent.id,
                            bookId = listeningEvent.bookId,
                            startPositionMs = listeningEvent.startPositionMs,
                            endPositionMs = listeningEvent.endPositionMs,
                            startedAt = listeningEvent.startedAt,
                            endedAt = listeningEvent.endedAt,
                            playbackSpeed = listeningEvent.playbackSpeed,
                            deviceId = listeningEvent.deviceId,
                            createdAt = listeningEvent.createdAt,
                        )
                    }

                    "activity.created" -> {
                        val activityEvent =
                            json.decodeFromJsonElement(
                                SSEActivityCreatedEvent.serializer(),
                                sseEvent.data,
                            )
                        SSEEventType.ActivityCreated(
                            id = activityEvent.id,
                            userId = activityEvent.userId,
                            type = activityEvent.type,
                            createdAt = activityEvent.createdAt,
                            userDisplayName = activityEvent.userDisplayName,
                            userAvatarColor = activityEvent.userAvatarColor,
                            userAvatarType = activityEvent.userAvatarType,
                            userAvatarValue = activityEvent.userAvatarValue,
                            bookId = activityEvent.bookId,
                            bookTitle = activityEvent.bookTitle,
                            bookAuthorName = activityEvent.bookAuthorName,
                            bookCoverPath = activityEvent.bookCoverPath,
                            isReread = activityEvent.isReread,
                            durationMs = activityEvent.durationMs,
                            milestoneValue = activityEvent.milestoneValue,
                            milestoneUnit = activityEvent.milestoneUnit,
                            lensId = activityEvent.lensId,
                            lensName = activityEvent.lensName,
                        )
                    }

                    "profile.updated" -> {
                        val profileEvent =
                            json.decodeFromJsonElement(
                                SSEProfileUpdatedEvent.serializer(),
                                sseEvent.data,
                            )
                        SSEEventType.ProfileUpdated(
                            userId = profileEvent.userId,
                            firstName = profileEvent.firstName,
                            lastName = profileEvent.lastName,
                            avatarType = profileEvent.avatarType,
                            avatarValue = profileEvent.avatarValue,
                            avatarColor = profileEvent.avatarColor,
                            tagline = profileEvent.tagline,
                        )
                    }

                    "session.started" -> {
                        val sessionEvent =
                            json.decodeFromJsonElement(
                                SSESessionStartedEvent.serializer(),
                                sseEvent.data,
                            )
                        SSEEventType.SessionStarted(
                            sessionId = sessionEvent.sessionId,
                            userId = sessionEvent.userId,
                            bookId = sessionEvent.bookId,
                            startedAt = sessionEvent.startedAt,
                        )
                    }

                    "session.ended" -> {
                        val sessionEvent =
                            json.decodeFromJsonElement(
                                SSESessionEndedEvent.serializer(),
                                sseEvent.data,
                            )
                        SSEEventType.SessionEnded(
                            sessionId = sessionEvent.sessionId,
                        )
                    }

                    "user_stats.updated" -> {
                        val statsEvent =
                            json.decodeFromJsonElement(
                                SSEUserStatsUpdatedEvent.serializer(),
                                sseEvent.data,
                            )
                        SSEEventType.UserStatsUpdated(
                            userId = statsEvent.userId,
                            displayName = statsEvent.displayName,
                            avatarType = statsEvent.avatarType,
                            avatarValue = statsEvent.avatarValue,
                            avatarColor = statsEvent.avatarColor,
                            totalTimeMs = statsEvent.totalTimeMs,
                            totalBooks = statsEvent.totalBooks,
                            currentStreak = statsEvent.currentStreak,
                        )
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
 * Sealed interface for type-safe SSE event handling.
 * Each event type corresponds to a server event defined in sse/events.go.
 */
sealed interface SSEEventType {
    data class BookCreated(
        val book: com.calypsan.listenup.client.data.remote.model.BookResponse,
    ) : SSEEventType

    data class BookUpdated(
        val book: com.calypsan.listenup.client.data.remote.model.BookResponse,
    ) : SSEEventType

    data class BookDeleted(
        val bookId: String,
        val deletedAt: String,
    ) : SSEEventType

    data class ScanStarted(
        val libraryId: String,
        val startedAt: String,
    ) : SSEEventType

    data class ScanCompleted(
        val libraryId: String,
        val booksAdded: Int,
        val booksUpdated: Int,
        val booksRemoved: Int,
    ) : SSEEventType

    data object Heartbeat : SSEEventType

    /**
     * SSE connection was re-established after a disconnect.
     * Triggers delta sync to catch up on missed events.
     */
    data object Reconnected : SSEEventType

    /**
     * Admin-only: New user registered and is pending approval.
     */
    data class UserPending(
        val user: SSEUserData,
    ) : SSEEventType

    /**
     * Admin-only: Pending user was approved.
     */
    data class UserApproved(
        val user: SSEUserData,
    ) : SSEEventType

    /**
     * Current user's account was deleted.
     * Client should clear auth state and navigate to login.
     */
    data class UserDeleted(
        val userId: String,
        val reason: String?,
    ) : SSEEventType

    // Collection events (admin-only)

    /**
     * Admin-only: New collection was created.
     */
    data class CollectionCreated(
        val id: String,
        val name: String,
        val bookCount: Int,
    ) : SSEEventType

    /**
     * Admin-only: Collection was updated.
     */
    data class CollectionUpdated(
        val id: String,
        val name: String,
        val bookCount: Int,
    ) : SSEEventType

    /**
     * Admin-only: Collection was deleted.
     */
    data class CollectionDeleted(
        val id: String,
        val name: String,
    ) : SSEEventType

    /**
     * Admin-only: Book was added to a collection.
     */
    data class CollectionBookAdded(
        val collectionId: String,
        val collectionName: String,
        val bookId: String,
    ) : SSEEventType

    /**
     * Admin-only: Book was removed from a collection.
     */
    data class CollectionBookRemoved(
        val collectionId: String,
        val collectionName: String,
        val bookId: String,
    ) : SSEEventType

    // Lens events

    /**
     * A new lens was created.
     */
    data class LensCreated(
        val id: String,
        val ownerId: String,
        val name: String,
        val description: String?,
        val bookCount: Int,
        val ownerDisplayName: String,
        val ownerAvatarColor: String,
        val createdAt: String,
        val updatedAt: String,
    ) : SSEEventType

    /**
     * An existing lens was updated.
     */
    data class LensUpdated(
        val id: String,
        val ownerId: String,
        val name: String,
        val description: String?,
        val bookCount: Int,
        val ownerDisplayName: String,
        val ownerAvatarColor: String,
        val createdAt: String,
        val updatedAt: String,
    ) : SSEEventType

    /**
     * A lens was deleted.
     */
    data class LensDeleted(
        val id: String,
        val ownerId: String,
    ) : SSEEventType

    /**
     * A book was added to a lens.
     */
    data class LensBookAdded(
        val lensId: String,
        val ownerId: String,
        val bookId: String,
        val bookCount: Int,
    ) : SSEEventType

    /**
     * A book was removed from a lens.
     */
    data class LensBookRemoved(
        val lensId: String,
        val ownerId: String,
        val bookId: String,
        val bookCount: Int,
    ) : SSEEventType

    // Tag events

    /**
     * A new tag was created globally.
     */
    data class TagCreated(
        val id: String,
        val slug: String,
        val bookCount: Int,
    ) : SSEEventType

    /**
     * A tag was added to a book.
     */
    data class BookTagAdded(
        val bookId: String,
        val tagId: String,
        val tagSlug: String,
        val tagBookCount: Int,
    ) : SSEEventType

    /**
     * A tag was removed from a book.
     */
    data class BookTagRemoved(
        val bookId: String,
        val tagId: String,
        val tagSlug: String,
        val tagBookCount: Int,
    ) : SSEEventType

    // Inbox events (admin-only)

    /**
     * Admin-only: A book was added to the inbox.
     */
    data class InboxBookAdded(
        val bookId: String,
        val title: String,
    ) : SSEEventType

    /**
     * Admin-only: A book was released from the inbox.
     */
    data class InboxBookReleased(
        val bookId: String,
    ) : SSEEventType

    // Listening events

    /**
     * User's playback progress was updated.
     * Used to refresh stats and continue listening.
     */
    data class ProgressUpdated(
        val bookId: String,
        val currentPositionMs: Long,
        val progress: Double,
        val totalListenTimeMs: Long,
        val isFinished: Boolean,
        val lastPlayedAt: String,
    ) : SSEEventType

    /**
     * A reading session was created or updated.
     * Used to refresh book readers list.
     */
    data class ReadingSessionUpdated(
        val sessionId: String,
        val bookId: String,
        val isCompleted: Boolean,
        val listenTimeMs: Long,
        val finishedAt: String?,
    ) : SSEEventType

    /**
     * A listening event was created on another device.
     * Used to sync events for offline stats computation.
     */
    data class ListeningEventCreated(
        val id: String,
        val bookId: String,
        val startPositionMs: Long,
        val endPositionMs: Long,
        val startedAt: String,
        val endedAt: String,
        val playbackSpeed: Float,
        val deviceId: String,
        val createdAt: String,
    ) : SSEEventType

    // Activity events

    /**
     * A new activity was created (started book, finished book, milestone, listening session, lens created, etc.).
     * Used for real-time activity feed updates.
     */
    data class ActivityCreated(
        val id: String,
        val userId: String,
        val type: String,
        val createdAt: String,
        val userDisplayName: String,
        val userAvatarColor: String,
        val userAvatarType: String = "auto",
        val userAvatarValue: String? = null,
        val bookId: String? = null,
        val bookTitle: String? = null,
        val bookAuthorName: String? = null,
        val bookCoverPath: String? = null,
        val isReread: Boolean = false,
        val durationMs: Long = 0,
        val milestoneValue: Int = 0,
        val milestoneUnit: String? = null,
        val lensId: String? = null,
        val lensName: String? = null,
    ) : SSEEventType

    // Profile events

    /**
     * A user's profile was updated.
     * Used to refresh profile data in UI.
     */
    data class ProfileUpdated(
        val userId: String,
        val firstName: String,
        val lastName: String,
        val avatarType: String,
        val avatarValue: String?,
        val avatarColor: String,
        val tagline: String?,
    ) : SSEEventType {
        val displayName: String get() = "$firstName $lastName".trim()
    }

    // Active session events (for "What Others Are Listening To")

    /**
     * Another user started a reading session.
     * Broadcast to all users for the "What Others Are Listening To" feature.
     */
    data class SessionStarted(
        val sessionId: String,
        val userId: String,
        val bookId: String,
        val startedAt: String,
    ) : SSEEventType

    /**
     * A reading session ended.
     * Broadcast to all users to remove from "What Others Are Listening To".
     */
    data class SessionEnded(
        val sessionId: String,
    ) : SSEEventType

    /**
     * A user's all-time stats were updated.
     * Broadcast to all users for leaderboard caching.
     */
    data class UserStatsUpdated(
        val userId: String,
        val displayName: String,
        val avatarType: String,
        val avatarValue: String?,
        val avatarColor: String,
        val totalTimeMs: Long,
        val totalBooks: Int,
        val currentStreak: Int,
    ) : SSEEventType
}
