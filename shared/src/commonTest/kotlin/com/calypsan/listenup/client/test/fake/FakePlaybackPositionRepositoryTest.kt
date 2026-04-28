package com.calypsan.listenup.client.test.fake

import app.cash.turbine.test
import com.calypsan.listenup.client.core.AppResult
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Success
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Smoke tests for [FakePlaybackPositionRepository]. Proves the fake's write-then-read
 * semantics hold — this is the core distinction from a Mokkery mock, which has no state
 * to read back from.
 */
class FakePlaybackPositionRepositoryTest {
    @Test
    fun saveAndGet() =
        runTest {
            val repo = FakePlaybackPositionRepository()

            repo.save(bookId = "book-1", positionMs = 5_000L, playbackSpeed = 1.25f, hasCustomSpeed = true)

            val result =
                assertIs<AppResult.Success<com.calypsan.listenup.client.domain.model.PlaybackPosition?>>(
                    repo.get(BookId("book-1")),
                )
            val saved = assertNotNull(result.data)
            assertEquals(5_000L, saved.positionMs)
            assertEquals(1.25f, saved.playbackSpeed)
            assertTrue(saved.hasCustomSpeed)
        }

    @Test
    fun observeEmitsOnWrite() =
        runTest {
            val repo = FakePlaybackPositionRepository()

            repo.observe("book-1").test {
                assertNull(awaitItem(), "initial emission must be null (nothing saved)")
                repo.save("book-1", positionMs = 1_000L, playbackSpeed = 1.0f, hasCustomSpeed = false)
                val after = awaitItem()
                assertNotNull(after)
                assertEquals(1_000L, after.positionMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeAllReflectsAllBooks() =
        runTest {
            val repo = FakePlaybackPositionRepository()
            repo.save("book-1", 100L, 1.0f, false)
            repo.save("book-2", 200L, 1.5f, true)

            repo.observeAll().test {
                val all = awaitItem()
                assertEquals(2, all.size)
                assertEquals(100L, all["book-1"]?.positionMs)
                assertEquals(200L, all["book-2"]?.positionMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun markCompleteSetsIsFinishedAndFinishedAt() =
        runTest {
            val repo = FakePlaybackPositionRepository()
            repo.save("book-1", 1_000L, 1.0f, false)

            val result = repo.markComplete("book-1", startedAt = 100L, finishedAt = 999L)

            assertTrue(result is com.calypsan.listenup.client.core.Success)
            val after =
                assertNotNull(
                    assertIs<AppResult.Success<com.calypsan.listenup.client.domain.model.PlaybackPosition?>>(
                        repo.get(BookId("book-1")),
                    ).data,
                )
            assertTrue(after.isFinished)
            assertEquals(999L, after.finishedAtMs)
            assertEquals(100L, after.startedAtMs)
        }

    @Test
    fun discardProgressRemovesEntry() =
        runTest {
            val repo = FakePlaybackPositionRepository()
            repo.save("book-1", 1_000L, 1.0f, false)

            val result = repo.discardProgress("book-1")

            assertTrue(result is com.calypsan.listenup.client.core.Success)
            val afterDiscard = assertIs<AppResult.Success<*>>(repo.get(BookId("book-1")))
            assertNull(afterDiscard.data)
        }

    @Test
    fun restartBookResetsPositionAndClearsFinished() =
        runTest {
            val repo = FakePlaybackPositionRepository()
            repo.save("book-1", 10_000L, 1.0f, false)
            repo.markComplete("book-1")

            val result = repo.restartBook("book-1")

            assertTrue(result is com.calypsan.listenup.client.core.Success)
            val after =
                assertNotNull(
                    assertIs<AppResult.Success<com.calypsan.listenup.client.domain.model.PlaybackPosition?>>(
                        repo.get(BookId("book-1")),
                    ).data,
                )
            assertEquals(0L, after.positionMs)
            assertEquals(false, after.isFinished)
            assertNull(after.finishedAtMs)
        }

    @Test
    fun getRecentPositionsOrdersByLastPlayedAt() =
        runTest {
            var clock = 1_000L
            val repo = FakePlaybackPositionRepository(nowMs = { clock })
            repo.save("oldest", 100L, 1.0f, false)
            clock = 2_000L
            repo.save("newest", 200L, 1.0f, false)

            val recent = repo.getRecentPositions(limit = 10)

            assertEquals(2, recent.size)
            assertEquals("newest", recent[0].bookId, "most-recently-played first")
            assertEquals("oldest", recent[1].bookId)
        }

    @Test
    fun getLastPlayedBookReturnsMostRecentlyPlayed() =
        runTest {
            var clock = 1_000L
            val repo = FakePlaybackPositionRepository(nowMs = { clock })
            repo.save("oldest", positionMs = 100L, playbackSpeed = 1.0f, hasCustomSpeed = false)
            clock = 2_000L
            repo.save("newest", positionMs = 5_000L, playbackSpeed = 1.5f, hasCustomSpeed = true)

            val result = repo.getLastPlayedBook()

            assertTrue(result is Success)
            val info = result.data
            assertNotNull(info)
            assertEquals("newest", info.bookId.value)
            assertEquals(5_000L, info.positionMs)
            assertEquals(1.5f, info.playbackSpeed)
        }

    @Test
    fun getLastPlayedBookReturnsNullWhenEmpty() =
        runTest {
            val repo = FakePlaybackPositionRepository()

            val result = repo.getLastPlayedBook()

            assertTrue(result is Success)
            assertNull(result.data)
        }
}
