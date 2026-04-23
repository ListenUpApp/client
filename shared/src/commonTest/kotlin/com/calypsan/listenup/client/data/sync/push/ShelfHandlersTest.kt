package com.calypsan.listenup.client.data.sync.push

import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.data.local.db.OperationStatus
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PendingOperationEntity
import com.calypsan.listenup.client.data.remote.ShelfApiContract
import com.calypsan.listenup.client.data.remote.ShelfOwnerResponse
import com.calypsan.listenup.client.data.remote.ShelfResponse
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Tests for the 5 Shelf OperationHandlers.
 *
 * Each handler:
 * - Success path: verifies the correct API method is called and Success(Unit) is returned.
 * - Failure path: verifies that a thrown exception is converted to Failure.
 */
class ShelfHandlersTest {
    private val owner =
        ShelfOwnerResponse(id = "user-1", displayName = "Alice", avatarColor = "#abc")

    private val shelfResponse =
        ShelfResponse(
            id = "shelf-1",
            name = "My Shelf",
            description = "",
            owner = owner,
            bookCount = 0,
            totalDuration = 0L,
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
        )

    private fun pendingOp(type: OperationType): PendingOperationEntity =
        PendingOperationEntity(
            id = "op-1",
            operationType = type,
            entityType = null,
            entityId = null,
            payload = "{}",
            batchKey = null,
            status = OperationStatus.PENDING,
            createdAt = 1000L,
            updatedAt = 1000L,
            attemptCount = 0,
            lastError = null,
        )

    // ========== CreateShelfHandler ==========

    @Test
    fun `CreateShelfHandler execute - success - calls createShelf and returns Success`() =
        runTest {
            val api: ShelfApiContract = mock()
            everySuspend { api.createShelf("My Shelf", "A description") } returns shelfResponse

            val handler = CreateShelfHandler(api)
            val payload = CreateShelfPayload(localId = "local-1", name = "My Shelf", description = "A description")
            val result = handler.execute(pendingOp(OperationType.CREATE_SHELF), payload)

            assertIs<Success<Unit>>(result)
            verifySuspend { api.createShelf("My Shelf", "A description") }
        }

    @Test
    fun `CreateShelfHandler execute - failure - returns Failure`() =
        runTest {
            val api: ShelfApiContract = mock()
            everySuspend { api.createShelf("My Shelf", null) } throws RuntimeException("network error")

            val handler = CreateShelfHandler(api)
            val payload = CreateShelfPayload(localId = "local-1", name = "My Shelf", description = null)
            val result = handler.execute(pendingOp(OperationType.CREATE_SHELF), payload)

            assertIs<Failure>(result)
        }

    // ========== UpdateShelfHandler ==========

    @Test
    fun `UpdateShelfHandler execute - success - calls updateShelf and returns Success`() =
        runTest {
            val api: ShelfApiContract = mock()
            everySuspend { api.updateShelf("shelf-1", "Renamed", null) } returns shelfResponse

            val handler = UpdateShelfHandler(api)
            val payload = UpdateShelfPayload(shelfId = "shelf-1", name = "Renamed", description = null)
            val result = handler.execute(pendingOp(OperationType.UPDATE_SHELF), payload)

            assertIs<Success<Unit>>(result)
            verifySuspend { api.updateShelf("shelf-1", "Renamed", null) }
        }

    @Test
    fun `UpdateShelfHandler execute - failure - returns Failure`() =
        runTest {
            val api: ShelfApiContract = mock()
            everySuspend { api.updateShelf("shelf-1", "Renamed", null) } throws RuntimeException("network error")

            val handler = UpdateShelfHandler(api)
            val payload = UpdateShelfPayload(shelfId = "shelf-1", name = "Renamed", description = null)
            val result = handler.execute(pendingOp(OperationType.UPDATE_SHELF), payload)

            assertIs<Failure>(result)
        }

    // ========== DeleteShelfHandler ==========

    @Test
    fun `DeleteShelfHandler execute - success - calls deleteShelf and returns Success`() =
        runTest {
            val api: ShelfApiContract = mock()
            everySuspend { api.deleteShelf("shelf-1") } returns Unit

            val handler = DeleteShelfHandler(api)
            val payload = DeleteShelfPayload(shelfId = "shelf-1")
            val result = handler.execute(pendingOp(OperationType.DELETE_SHELF), payload)

            assertIs<Success<Unit>>(result)
            verifySuspend { api.deleteShelf("shelf-1") }
        }

    @Test
    fun `DeleteShelfHandler execute - failure - returns Failure`() =
        runTest {
            val api: ShelfApiContract = mock()
            everySuspend { api.deleteShelf("shelf-1") } throws RuntimeException("network error")

            val handler = DeleteShelfHandler(api)
            val payload = DeleteShelfPayload(shelfId = "shelf-1")
            val result = handler.execute(pendingOp(OperationType.DELETE_SHELF), payload)

            assertIs<Failure>(result)
        }

    // ========== AddBooksToShelfHandler ==========

    @Test
    fun `AddBooksToShelfHandler execute - success - calls addBooks and returns Success`() =
        runTest {
            val api: ShelfApiContract = mock()
            val bookIds = listOf("book-1", "book-2")
            everySuspend { api.addBooks("shelf-1", bookIds) } returns Unit

            val handler = AddBooksToShelfHandler(api)
            val payload = AddBooksToShelfPayload(shelfId = "shelf-1", bookIds = bookIds)
            val result = handler.execute(pendingOp(OperationType.ADD_BOOKS_TO_SHELF), payload)

            assertIs<Success<Unit>>(result)
            verifySuspend { api.addBooks("shelf-1", bookIds) }
        }

    @Test
    fun `AddBooksToShelfHandler execute - failure - returns Failure`() =
        runTest {
            val api: ShelfApiContract = mock()
            val bookIds = listOf("book-1")
            everySuspend { api.addBooks("shelf-1", bookIds) } throws RuntimeException("network error")

            val handler = AddBooksToShelfHandler(api)
            val payload = AddBooksToShelfPayload(shelfId = "shelf-1", bookIds = bookIds)
            val result = handler.execute(pendingOp(OperationType.ADD_BOOKS_TO_SHELF), payload)

            assertIs<Failure>(result)
        }

    // ========== RemoveBookFromShelfHandler ==========

    @Test
    fun `RemoveBookFromShelfHandler execute - success - calls removeBook and returns Success`() =
        runTest {
            val api: ShelfApiContract = mock()
            everySuspend { api.removeBook("shelf-1", "book-1") } returns Unit

            val handler = RemoveBookFromShelfHandler(api)
            val payload = RemoveBookFromShelfPayload(shelfId = "shelf-1", bookId = "book-1")
            val result = handler.execute(pendingOp(OperationType.REMOVE_BOOK_FROM_SHELF), payload)

            assertIs<Success<Unit>>(result)
            verifySuspend { api.removeBook("shelf-1", "book-1") }
        }

    @Test
    fun `RemoveBookFromShelfHandler execute - failure - returns Failure`() =
        runTest {
            val api: ShelfApiContract = mock()
            everySuspend { api.removeBook("shelf-1", "book-1") } throws RuntimeException("network error")

            val handler = RemoveBookFromShelfHandler(api)
            val payload = RemoveBookFromShelfPayload(shelfId = "shelf-1", bookId = "book-1")
            val result = handler.execute(pendingOp(OperationType.REMOVE_BOOK_FROM_SHELF), payload)

            assertIs<Failure>(result)
        }
}
