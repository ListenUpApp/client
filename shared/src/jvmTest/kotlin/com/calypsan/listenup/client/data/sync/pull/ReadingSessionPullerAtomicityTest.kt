package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.ReaderSessionCacheDao
import com.calypsan.listenup.client.data.local.db.RoomTransactionRunner
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.UserReadingSessionEntity
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.SyncReadingSessionReaderResponse
import com.calypsan.listenup.client.data.remote.SyncReadingSessionsResponse
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves [ReadingSessionPuller.pull] is atomic — when the cache insert throws,
 * the prior `deleteAll` on both tables must roll back so pre-existing state
 * (from a previous sync) survives the failed refresh rather than being wiped.
 *
 * Uses a real in-memory [ListenUpDatabase] so transaction semantics are
 * exercised end-to-end. The cache DAO is mocked to throw on insert, forcing
 * the failure after the deleteAll steps have landed.
 *
 * Note: [ReadingSessionPuller.pull] swallows exceptions (reading sessions are
 * non-critical for sync), so the test asserts on post-call DB state rather
 * than on a thrown exception.
 */
class ReadingSessionPullerAtomicityTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `rollback when cache insert throws`() =
        runTest {
            seedBookAndPreExistingRows()

            val syncApi: SyncApiContract = mock()
            val authSession: AuthSession = mock()
            val failingCacheDao: ReaderSessionCacheDao = mock()

            everySuspend { authSession.getUserId() } returns "me"
            everySuspend { syncApi.getReadingSessions() } returns
                Success(
                    SyncReadingSessionsResponse(
                        readers =
                            listOf(
                                apiReader(bookId = "b1", userId = "u1"),
                                apiReader(bookId = "b1", userId = "u2"),
                            ),
                    ),
                )
            everySuspend { failingCacheDao.deleteAll() } returns Unit
            everySuspend { failingCacheDao.upsertAll(any()) } throws
                RuntimeException("boom — cache insert failed")

            val puller =
                ReadingSessionPuller(
                    syncApi = syncApi,
                    userReadingSessionDao = db.userReadingSessionDao(),
                    readerSessionCacheDao = failingCacheDao,
                    transactionRunner = RoomTransactionRunner(db),
                    authSession = authSession,
                )

            // Puller swallows exceptions internally — we assert on DB state instead.
            puller.pull(null) {}

            // Pre-existing user_reading_sessions row must survive the failed refresh.
            assertEquals(
                1,
                db.userReadingSessionDao().getForBook(bookId = "b1", userId = "me").size,
                "user_reading_sessions delete must roll back when cache insert throws",
            )
        }

    private suspend fun seedBookAndPreExistingRows() {
        db.bookDao().upsert(
            BookEntity(
                id = BookId("b1"),
                title = "Pre-existing",
                sortTitle = "Pre-existing",
                subtitle = null,
                coverUrl = null,
                coverBlurHash = null,
                dominantColor = null,
                darkMutedColor = null,
                vibrantColor = null,
                totalDuration = 0L,
                description = null,
                publishYear = null,
                publisher = null,
                language = null,
                isbn = null,
                asin = null,
                abridged = false,
                syncState = SyncState.SYNCED,
                lastModified = Timestamp(1L),
                serverVersion = Timestamp(1L),
                createdAt = Timestamp(1L),
                updatedAt = Timestamp(1L),
            ),
        )
        db.userReadingSessionDao().upsertAll(
            listOf(
                UserReadingSessionEntity(
                    id = "pre-existing-session",
                    bookId = "b1",
                    userId = "me",
                    startedAt = 1_000L,
                    finishedAt = null,
                    isCompleted = false,
                    listenTimeMs = 0L,
                    updatedAt = 1L,
                ),
            ),
        )
    }

    private fun apiReader(
        bookId: String,
        userId: String,
    ): SyncReadingSessionReaderResponse =
        SyncReadingSessionReaderResponse(
            bookId = bookId,
            userId = userId,
            displayName = "User $userId",
            avatarType = "auto",
            avatarValue = null,
            avatarColor = "#FF0000",
            isCurrentlyReading = true,
            currentProgress = 0.5,
            startedAt = "2024-01-01T00:00:00Z",
            finishedAt = null,
            lastActivityAt = "2024-01-02T00:00:00Z",
            completionCount = 0,
        )
}
