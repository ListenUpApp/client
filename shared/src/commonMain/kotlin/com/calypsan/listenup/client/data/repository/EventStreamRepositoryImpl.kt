package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.sync.SSEChannelMessage
import com.calypsan.listenup.client.data.sync.SSEEvent
import com.calypsan.listenup.client.data.sync.SSEManagerContract
import com.calypsan.listenup.client.domain.model.AdminEvent
import com.calypsan.listenup.client.domain.model.AdminUserInfo
import com.calypsan.listenup.client.domain.model.BookEvent
import com.calypsan.listenup.client.domain.repository.EventStreamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * Implementation of EventStreamRepository that bridges SSE events to domain events.
 *
 * Maps data layer SSE event types to domain event types, allowing the presentation
 * layer to observe real-time events without depending on data layer types.
 *
 * @property sseManager The underlying SSE manager that provides raw events
 */
class EventStreamRepositoryImpl(
    private val sseManager: SSEManagerContract,
) : EventStreamRepository {
    /**
     * Maps SSE admin events to domain AdminEvent types.
     *
     * Filters the SSE event stream to only admin-related events
     * and transforms them to use domain models.
     */
    override val adminEvents: Flow<AdminEvent> =
        sseManager.eventFlow.wireEvents().mapNotNull { event ->
            when (event) {
                is SSEEvent.UserPending -> {
                    AdminEvent.UserPending(
                        user = event.data.user.toDomain(),
                    )
                }

                is SSEEvent.UserApproved -> {
                    AdminEvent.UserApproved(
                        user = event.data.user.toDomain(),
                    )
                }

                is SSEEvent.InboxBookAdded -> {
                    AdminEvent.InboxBookAdded(
                        bookId = event.data.book.id,
                        title = event.data.book.title,
                    )
                }

                is SSEEvent.InboxBookReleased -> {
                    AdminEvent.InboxBookReleased(
                        bookId = event.data.bookId,
                    )
                }

                else -> {
                    null
                }
            }
        }

    /**
     * Maps SSE book events to domain BookEvent types.
     *
     * Filters the SSE event stream to only book-related events
     * that ViewModels need.
     */
    override val bookEvents: Flow<BookEvent> =
        sseManager.eventFlow
            .wireEvents()
            .filterIsInstance<SSEEvent.ReadingSessionUpdated>()
            .map { event ->
                val payload = event.data
                BookEvent.ReadingSessionUpdated(
                    sessionId = payload.sessionId,
                    bookId = payload.bookId,
                    isCompleted = payload.isCompleted,
                    listenTimeMs = payload.listenTimeMs,
                    finishedAt = payload.finishedAt,
                )
            }
}

/**
 * Unwraps [SSEChannelMessage.Wire] entries to their payload [SSEEvent], dropping
 * synthetic signals (e.g. [SSEChannelMessage.Reconnected]) that domain flows don't consume.
 */
private fun Flow<SSEChannelMessage>.wireEvents(): Flow<SSEEvent> = mapNotNull { (it as? SSEChannelMessage.Wire)?.event }

/**
 * Convert SSE user data to domain AdminUserInfo model.
 */
private fun com.calypsan.listenup.client.data.remote.model.SSEUserData.toDomain(): AdminUserInfo =
    AdminUserInfo(
        id = id,
        email = email,
        displayName = displayName,
        firstName = firstName,
        lastName = lastName,
        isRoot = isRoot,
        role = role,
        status = status,
        createdAt = createdAt,
    )
