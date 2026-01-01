package com.calypsan.listenup.client.data.sync.push

import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.OperationStatus
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PendingOperationEntity
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.remote.ListeningEventsResponse
import com.calypsan.listenup.client.data.remote.SyncApiContract
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Tests for ListeningEventHandler.
 *
 * Verifies:
 * - Successful event submission marks position as synced
 * - Failed submissions don't mark position as synced
 * - Batch submissions mark all successful positions as synced
 */
class ListeningEventHandlerTest {
    private fun createPayload(
        id: String = "evt-1",
        bookId: String = "book-1",
    ): ListeningEventPayload =
        ListeningEventPayload(
            id = id,
            bookId = bookId,
            startPositionMs = 0,
            endPositionMs = 60000,
            startedAt = 1000L,
            endedAt = 61000L,
            playbackSpeed = 1.0f,
            deviceId = "device-1",
        )

    private fun createOperation(
        id: String = "op-1",
        payload: ListeningEventPayload = createPayload(),
    ): PendingOperationEntity =
        PendingOperationEntity(
            id = id,
            operationType = OperationType.LISTENING_EVENT,
            entityType = null,
            entityId = null,
            payload = """{"id":"${payload.id}","bookId":"${payload.bookId}"}""",
            batchKey = "listening_events",
            status = OperationStatus.PENDING,
            createdAt = 1000L,
            updatedAt = 1000L,
            attemptCount = 0,
            lastError = null,
        )

    private fun createPosition(
        bookId: String = "book-1",
        syncedAt: Long? = null,
    ): PlaybackPositionEntity =
        PlaybackPositionEntity(
            bookId = BookId(bookId),
            positionMs = 60000,
            playbackSpeed = 1.0f,
            hasCustomSpeed = false,
            updatedAt = 1000L,
            syncedAt = syncedAt,
        )

    // ========== Regression Test: markSynced must be called ==========

    @Test
    fun `executeBatch marks position as synced after successful submission`() =
        runTest {
            // Arrange: position exists with syncedAt = null (never synced)
            val api: SyncApiContract = mock()
            val positionDao: PlaybackPositionDao = mock()

            val payload = createPayload(id = "evt-1", bookId = "book-1")
            val operation = createOperation(id = "op-1", payload = payload)

            // API returns success with acknowledged event
            everySuspend { api.submitListeningEvents(any()) } returns
                Success(ListeningEventsResponse(acknowledged = listOf("evt-1")))

            // Position exists
            everySuspend { positionDao.get(BookId("book-1")) } returns createPosition("book-1")
            everySuspend { positionDao.markSynced(any(), any()) } returns Unit

            val handler = ListeningEventHandler(api, positionDao)

            // Act: execute the batch
            val results = handler.executeBatch(listOf(operation to payload))

            // Assert: submission succeeded
            assertIs<Success<Unit>>(results["op-1"])

            // Assert: markSynced was called for the book
            verifySuspend { positionDao.markSynced(BookId("book-1"), any()) }
        }

    @Test
    fun `executeBatch does not mark position as synced when submission fails`() =
        runTest {
            // Arrange
            val api: SyncApiContract = mock()
            val positionDao: PlaybackPositionDao = mock()

            val payload = createPayload(id = "evt-1", bookId = "book-1")
            val operation = createOperation(id = "op-1", payload = payload)

            // API returns success but event not acknowledged
            everySuspend { api.submitListeningEvents(any()) } returns
                Success(ListeningEventsResponse(acknowledged = emptyList()))

            val handler = ListeningEventHandler(api, positionDao)

            // Act
            val results = handler.executeBatch(listOf(operation to payload))

            // Assert: submission failed (not acknowledged)
            assertIs<com.calypsan.listenup.client.core.Failure>(results["op-1"])

            // Assert: markSynced was NOT called
            // (Mokkery will fail verification if it was called)
        }

    @Test
    fun `executeBatch marks multiple positions as synced for batch with multiple books`() =
        runTest {
            // Arrange
            val api: SyncApiContract = mock()
            val positionDao: PlaybackPositionDao = mock()

            val payload1 = createPayload(id = "evt-1", bookId = "book-1")
            val payload2 = createPayload(id = "evt-2", bookId = "book-2")
            val operation1 = createOperation(id = "op-1", payload = payload1)
            val operation2 = createOperation(id = "op-2", payload = payload2)

            // API returns success with both events acknowledged
            everySuspend { api.submitListeningEvents(any()) } returns
                Success(ListeningEventsResponse(acknowledged = listOf("evt-1", "evt-2")))

            everySuspend { positionDao.get(any()) } returns createPosition()
            everySuspend { positionDao.markSynced(any(), any()) } returns Unit

            val handler = ListeningEventHandler(api, positionDao)

            // Act
            val results =
                handler.executeBatch(
                    listOf(
                        operation1 to payload1,
                        operation2 to payload2,
                    ),
                )

            // Assert: both succeeded
            assertIs<Success<Unit>>(results["op-1"])
            assertIs<Success<Unit>>(results["op-2"])

            // Assert: markSynced was called for both books
            verifySuspend { positionDao.markSynced(BookId("book-1"), any()) }
            verifySuspend { positionDao.markSynced(BookId("book-2"), any()) }
        }

    @Test
    fun `execute marks position as synced for single event submission`() =
        runTest {
            // Arrange
            val api: SyncApiContract = mock()
            val positionDao: PlaybackPositionDao = mock()

            val payload = createPayload(id = "evt-1", bookId = "book-1")
            val operation = createOperation(id = "op-1", payload = payload)

            everySuspend { api.submitListeningEvents(any()) } returns
                Success(ListeningEventsResponse(acknowledged = listOf("evt-1")))

            everySuspend { positionDao.get(BookId("book-1")) } returns createPosition("book-1")
            everySuspend { positionDao.markSynced(any(), any()) } returns Unit

            val handler = ListeningEventHandler(api, positionDao)

            // Act
            val result = handler.execute(operation, payload)

            // Assert
            assertIs<Success<Unit>>(result)
            verifySuspend { positionDao.markSynced(BookId("book-1"), any()) }
        }
}
