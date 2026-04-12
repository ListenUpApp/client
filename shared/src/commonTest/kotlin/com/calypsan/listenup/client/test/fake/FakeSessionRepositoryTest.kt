package com.calypsan.listenup.client.test.fake

import app.cash.turbine.test
import com.calypsan.listenup.client.domain.model.BookReadersResult
import com.calypsan.listenup.client.domain.model.ReaderInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.calypsan.listenup.client.core.Success

class FakeSessionRepositoryTest {
    private fun reader(userId: String) =
        ReaderInfo(
            userId = userId,
            displayName = userId,
            avatarColor = "#000000",
            isCurrentlyReading = true,
            currentProgress = 0.5,
            startedAt = "2026-04-01T00:00:00Z",
            lastActivityAt = "2026-04-12T00:00:00Z",
            completionCount = 0,
        )

    @Test
    fun seededReadersAreReturnedByGetBookReaders() =
        runTest {
            val seed =
                mapOf(
                    "book-1" to
                        BookReadersResult(
                            yourSessions = emptyList(),
                            otherReaders = listOf(reader("alice"), reader("bob")),
                            totalReaders = 2,
                            totalCompletions = 0,
                        ),
                )
            val repo = FakeSessionRepository(initialReaders = seed)

            val readers = repo.getBookReaders("book-1")

            assertEquals(2, readers.size)
            assertTrue(readers.any { it.userId == "alice" })
        }

    @Test
    fun setReadersUpdatesObservers() =
        runTest {
            val repo = FakeSessionRepository()

            repo.observeBookReaders("book-1").test {
                val empty = awaitItem()
                assertEquals(0, empty.totalReaders)

                repo.setReaders(
                    "book-1",
                    BookReadersResult(
                        yourSessions = emptyList(),
                        otherReaders = listOf(reader("alice")),
                        totalReaders = 1,
                        totalCompletions = 0,
                    ),
                )

                val updated = awaitItem()
                assertEquals(1, updated.totalReaders)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun refreshBookReadersIncrementsRefreshCount() =
        runTest {
            val repo = FakeSessionRepository()

            repo.refreshBookReaders("book-1")
            repo.refreshBookReaders("book-1")

            assertEquals(2, repo.refreshCounts["book-1"])
        }

    @Test
    fun getBookReadersResultReturnsSuccess() =
        runTest {
            val seed =
                mapOf(
                    "book-1" to
                        BookReadersResult(
                            yourSessions = emptyList(),
                            otherReaders = emptyList(),
                            totalReaders = 0,
                            totalCompletions = 0,
                        ),
                )
            val repo = FakeSessionRepository(initialReaders = seed)

            val result = repo.getBookReadersResult("book-1")

            assertTrue(result is Success)
        }
}
