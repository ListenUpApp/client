package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.RoomTransactionRunner
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.TransactionRunner
import com.calypsan.listenup.client.domain.repository.LibrarySync
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves `LibraryResetHelper.clearLibraryData` is atomic — if anything throws while
 * the delete sequence is running, every prior delete must roll back so the DB is
 * never left half-wiped (e.g. books gone, cross-refs kept, which leaves orphan
 * rows once W4.4's foreign keys land).
 *
 * Finding 05 D2 / W4.2. The test simulates a failure at the end of the delete
 * sequence by wrapping [RoomTransactionRunner] with a runner that throws once the
 * inner block completes but before the transaction commits.
 */
class LibraryResetHelperAtomicityTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private class FailAtCommitRunner(
        private val delegate: TransactionRunner,
    ) : TransactionRunner {
        override suspend fun <R> atomically(block: suspend () -> R): R =
            delegate.atomically {
                block()
                throw RuntimeException("boom — simulated commit-time failure")
            }
    }

    @Test
    fun `rollback when reset transaction aborts at commit`() =
        runTest {
            db.bookDao().upsert(
                BookEntity(
                    id = BookId("book-before-reset"),
                    title = "Before Reset",
                    coverUrl = null,
                    totalDuration = 3_600_000L,
                    syncState = SyncState.SYNCED,
                    lastModified = Timestamp.now(),
                    serverVersion = Timestamp.now(),
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now(),
                ),
            )
            assertEquals(1, db.bookDao().count(), "precondition: book seeded")

            val librarySyncContract: LibrarySync = mock()

            val helper =
                LibraryResetHelper(
                    database = db,
                    transactionRunner = FailAtCommitRunner(RoomTransactionRunner(db)),
                    librarySyncContract = librarySyncContract,
                )

            assertFailsWith<RuntimeException> {
                helper.clearLibraryData(discardPendingOperations = true)
            }

            assertEquals(
                1,
                db.bookDao().count(),
                "book row must survive when the reset transaction aborts — the whole reset rolls back",
            )
        }
}
