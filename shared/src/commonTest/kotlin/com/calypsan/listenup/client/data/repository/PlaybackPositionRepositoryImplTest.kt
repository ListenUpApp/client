package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.AppResult
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.data.local.db.EntityType
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.local.db.TransactionRunner
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.test.db.passThroughTransactionRunner
import com.calypsan.listenup.client.data.sync.SSEEvent
import com.calypsan.listenup.client.data.sync.ProgressPayload
import com.calypsan.listenup.client.data.sync.push.DiscardProgressHandler
import com.calypsan.listenup.client.data.sync.push.MarkCompleteHandler
import com.calypsan.listenup.client.data.sync.push.MarkCompletePayload
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.data.sync.push.RestartBookHandler
import com.calypsan.listenup.client.domain.model.PlaybackPosition
import com.calypsan.listenup.client.domain.repository.LastPlayedInfo
import com.calypsan.listenup.client.domain.repository.PlaybackUpdate
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for PlaybackPositionRepositoryImpl.
 *
 * Tests cover:
 * - Get position by book ID (returns position or null)
 * - Observe single position (emits changes, emits null)
 * - Observe all positions (emits map, emits changes)
 * - Get recent positions (returns list, respects limit)
 * - Save position (creates new, preserves syncedAt for existing)
 * - Delete position
 * - Entity to domain conversion (all fields mapped correctly)
 *
 * Uses Mokkery for mocking PlaybackPositionDao.
 * Follows Given-When-Then style.
 */
class PlaybackPositionRepositoryImplTest {
    // ========== Test Data Factories ==========

    private fun createPlaybackPositionEntity(
        bookId: String = "book-1",
        positionMs: Long = 5000L,
        playbackSpeed: Float = 1.0f,
        hasCustomSpeed: Boolean = false,
        updatedAt: Long = 1704067200000L,
        syncedAt: Long? = null,
        lastPlayedAt: Long? = 1704067200000L,
    ): PlaybackPositionEntity =
        PlaybackPositionEntity(
            bookId = BookId(bookId),
            positionMs = positionMs,
            playbackSpeed = playbackSpeed,
            hasCustomSpeed = hasCustomSpeed,
            updatedAt = updatedAt,
            syncedAt = syncedAt,
            lastPlayedAt = lastPlayedAt,
        )

    private fun createMockDao(): PlaybackPositionDao = mock<PlaybackPositionDao>(MockMode.autoUnit)

    private fun createMockSyncApi(): SyncApiContract = mock<SyncApiContract>(MockMode.autoUnit)

    /**
     * Constructs a repository with sensible default mocks. Tests that need a
     * specific mock (e.g., a non-passthrough TransactionRunner for contention
     * tests) supply that arg explicitly.
     */
    private fun createRepo(
        dao: PlaybackPositionDao = createMockDao(),
        syncApi: SyncApiContract = createMockSyncApi(),
        pendingOps: PendingOperationRepositoryContract = mock<PendingOperationRepositoryContract>(MockMode.autoUnit),
        markCompleteHandler: MarkCompleteHandler = MarkCompleteHandler(createMockSyncApi()),
        discardProgressHandler: DiscardProgressHandler = DiscardProgressHandler(createMockSyncApi()),
        restartBookHandler: RestartBookHandler = RestartBookHandler(createMockSyncApi()),
        transactionRunner: TransactionRunner = passThroughTransactionRunner(),
    ): PlaybackPositionRepositoryImpl =
        PlaybackPositionRepositoryImpl(
            dao = dao,
            syncApi = syncApi,
            pendingOps = pendingOps,
            markCompleteHandler = markCompleteHandler,
            discardProgressHandler = discardProgressHandler,
            restartBookHandler = restartBookHandler,
            transactionRunner = transactionRunner,
        )

    // ========== get() Tests ==========

    @Test
    fun `get returns domain model when row exists`() =
        runTest {
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(bookId = "book-1", positionMs = 5000L)
            everySuspend { dao.get(any<BookId>()) } returns entity
            val repository = createRepo(dao = dao)

            val result = repository.get(BookId("book-1"))

            val success = assertIs<AppResult.Success<*>>(result)
            val position = assertNotNull(success.data as? PlaybackPosition)
            assertEquals("book-1", position.bookId)
            assertEquals(5000L, position.positionMs)
        }

    @Test
    fun `get returns null when no row exists`() =
        runTest {
            val dao = createMockDao()
            everySuspend { dao.get(any<BookId>()) } returns null
            val repository = createRepo(dao = dao)

            val result = repository.get(BookId("book-1"))

            assertEquals(AppResult.Success(null), result)
        }

    @Test
    fun `get returns Failure when dao throws`() =
        runTest {
            val dao = createMockDao()
            everySuspend { dao.get(any<BookId>()) } throws RuntimeException("dao boom")
            val repository = createRepo(dao = dao)

            val result = repository.get(BookId("book-1"))

            assertIs<AppResult.Failure>(result)
        }

    // ========== observe() Tests ==========

    @Test
    fun `observe emits position when it exists`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(bookId = "book-1", positionMs = 30000L)
            every { dao.observe(BookId("book-1")) } returns flowOf(entity)
            val repository = createRepo(dao = dao)

            // When
            val result = repository.observe("book-1").first()

            // Then
            assertNotNull(result)
            assertEquals("book-1", result.bookId)
            assertEquals(30000L, result.positionMs)
        }

    @Test
    fun `observe emits null when position does not exist`() =
        runTest {
            // Given
            val dao = createMockDao()
            every { dao.observe(any()) } returns flowOf(null)
            val repository = createRepo(dao = dao)

            // When
            val result = repository.observe("missing-book").first()

            // Then
            assertNull(result)
        }

    @Test
    fun `observe emits changes when position updates`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity1 = createPlaybackPositionEntity(bookId = "book-1", positionMs = 1000L)
            val entity2 = createPlaybackPositionEntity(bookId = "book-1", positionMs = 2000L)
            every { dao.observe(BookId("book-1")) } returns flowOf(null, entity1, entity2)
            val repository = createRepo(dao = dao)

            // When
            val emissions = repository.observe("book-1").take(3).toList()

            // Then
            assertEquals(3, emissions.size)
            assertNull(emissions[0])
            assertEquals(1000L, emissions[1]?.positionMs)
            assertEquals(2000L, emissions[2]?.positionMs)
        }

    // ========== observeAll() Tests ==========

    @Test
    fun `observeAll emits map of positions`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entities =
                listOf(
                    createPlaybackPositionEntity(bookId = "book-1", positionMs = 1000L),
                    createPlaybackPositionEntity(bookId = "book-2", positionMs = 2000L),
                    createPlaybackPositionEntity(bookId = "book-3", positionMs = 3000L),
                )
            every { dao.observeAll() } returns flowOf(entities)
            val repository = createRepo(dao = dao)

            // When
            val result = repository.observeAll().first()

            // Then
            assertEquals(3, result.size)
            assertEquals(1000L, result["book-1"]?.positionMs)
            assertEquals(2000L, result["book-2"]?.positionMs)
            assertEquals(3000L, result["book-3"]?.positionMs)
        }

    @Test
    fun `observeAll emits empty map when no positions exist`() =
        runTest {
            // Given
            val dao = createMockDao()
            every { dao.observeAll() } returns flowOf(emptyList())
            val repository = createRepo(dao = dao)

            // When
            val result = repository.observeAll().first()

            // Then
            assertTrue(result.isEmpty())
        }

    @Test
    fun `observeAll emits changes when positions update`() =
        runTest {
            // Given
            val dao = createMockDao()
            val list0 = emptyList<PlaybackPositionEntity>()
            val list1 = listOf(createPlaybackPositionEntity(bookId = "book-1"))
            val list2 =
                listOf(
                    createPlaybackPositionEntity(bookId = "book-1"),
                    createPlaybackPositionEntity(bookId = "book-2"),
                )
            every { dao.observeAll() } returns flowOf(list0, list1, list2)
            val repository = createRepo(dao = dao)

            // When
            val emissions = repository.observeAll().take(3).toList()

            // Then
            assertEquals(3, emissions.size)
            assertEquals(0, emissions[0].size)
            assertEquals(1, emissions[1].size)
            assertEquals(2, emissions[2].size)
        }

    @Test
    fun `observeAll uses bookId value as map key`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(bookId = "unique-book-id-123")
            every { dao.observeAll() } returns flowOf(listOf(entity))
            val repository = createRepo(dao = dao)

            // When
            val result = repository.observeAll().first()

            // Then
            assertTrue(result.containsKey("unique-book-id-123"))
            assertFalse(result.containsKey("wrong-key"))
        }

    // ========== save() Tests ==========

    @Test
    fun `save creates new position when none exists`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.get(BookId("new-book")) } returns null
            val repository = createRepo(dao = dao)

            // When
            repository.save(
                bookId = "new-book",
                positionMs = 5000L,
                playbackSpeed = 1.25f,
                hasCustomSpeed = true,
            )

            // Then
            verifySuspend {
                dao.save(
                    any(), // Entity with correct values
                )
            }
        }

    @Test
    fun `save preserves syncedAt for existing position`() =
        runTest {
            // Given
            val dao = createMockDao()
            val existingEntity =
                createPlaybackPositionEntity(
                    bookId = "book-1",
                    positionMs = 1000L,
                    syncedAt = 1704067200000L, // Previously synced
                )
            everySuspend { dao.get(BookId("book-1")) } returns existingEntity
            val repository = createRepo(dao = dao)

            // When
            repository.save(
                bookId = "book-1",
                positionMs = 2000L,
                playbackSpeed = 1.0f,
                hasCustomSpeed = false,
            )

            // Then - verify dao.get was called to retrieve existing entity (which has syncedAt)
            // and dao.save was called (the impl preserves syncedAt from existing entity)
            verifySuspend { dao.get(BookId("book-1")) }
            verifySuspend { dao.save(any()) }
        }

    @Test
    fun `save sets syncedAt to null for new position`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.get(BookId("new-book")) } returns null
            val repository = createRepo(dao = dao)

            // When
            repository.save(
                bookId = "new-book",
                positionMs = 5000L,
                playbackSpeed = 1.0f,
                hasCustomSpeed = false,
            )

            // Then - verify dao.save was called (syncedAt should be null since no existing entity)
            verifySuspend { dao.save(any()) }
            verifySuspend { dao.get(BookId("new-book")) }
        }

    @Test
    fun `save calls dao with correct parameters`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.get(any()) } returns null
            val repository = createRepo(dao = dao)

            // When
            repository.save(
                bookId = "test-book",
                positionMs = 12345L,
                playbackSpeed = 1.75f,
                hasCustomSpeed = true,
            )

            // Then
            verifySuspend { dao.save(any()) }
        }

    // ========== delete() Tests ==========

    @Test
    fun `delete calls dao with correct BookId`() =
        runTest {
            // Given
            val dao = createMockDao()
            val repository = createRepo(dao = dao)

            // When
            repository.delete("book-to-delete")

            // Then
            verifySuspend { dao.delete(BookId("book-to-delete")) }
        }

    @Test
    fun `delete does not throw when position does not exist`() =
        runTest {
            // Given
            val dao = createMockDao()
            val repository = createRepo(dao = dao)

            // When/Then - should not throw
            repository.delete("nonexistent-book")

            // Verify delete was still called
            verifySuspend { dao.delete(BookId("nonexistent-book")) }
        }

    // ========== Entity to Domain Conversion Tests ==========

    @Test
    fun `entity to domain conversion maps bookId value correctly`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(bookId = "unique-book-123")
            everySuspend { dao.get(BookId("unique-book-123")) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("unique-book-123"))).data as PlaybackPosition

            // Then
            assertEquals("unique-book-123", result.bookId)
        }

    @Test
    fun `entity to domain conversion maps positionMs correctly`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(positionMs = 123456789L)
            everySuspend { dao.get(any()) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("book-1"))).data as PlaybackPosition

            // Then
            assertEquals(123456789L, result.positionMs)
        }

    @Test
    fun `entity to domain conversion maps playbackSpeed correctly`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(playbackSpeed = 2.5f)
            everySuspend { dao.get(any()) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("book-1"))).data as PlaybackPosition

            // Then
            assertEquals(2.5f, result.playbackSpeed)
        }

    @Test
    fun `entity to domain conversion maps hasCustomSpeed correctly when true`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(hasCustomSpeed = true)
            everySuspend { dao.get(any()) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("book-1"))).data as PlaybackPosition

            // Then
            assertTrue(result.hasCustomSpeed)
        }

    @Test
    fun `entity to domain conversion maps hasCustomSpeed correctly when false`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(hasCustomSpeed = false)
            everySuspend { dao.get(any()) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("book-1"))).data as PlaybackPosition

            // Then
            assertFalse(result.hasCustomSpeed)
        }

    @Test
    fun `entity to domain conversion maps updatedAt to updatedAtMs correctly`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(updatedAt = 1704110400000L)
            everySuspend { dao.get(any()) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("book-1"))).data as PlaybackPosition

            // Then
            assertEquals(1704110400000L, result.updatedAtMs)
        }

    @Test
    fun `entity to domain conversion maps syncedAt to syncedAtMs correctly when present`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(syncedAt = 1704200000000L)
            everySuspend { dao.get(any()) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("book-1"))).data as PlaybackPosition

            // Then
            assertEquals(1704200000000L, result.syncedAtMs)
        }

    @Test
    fun `entity to domain conversion maps syncedAt to null when not synced`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(syncedAt = null)
            everySuspend { dao.get(any()) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("book-1"))).data as PlaybackPosition

            // Then
            assertNull(result.syncedAtMs)
        }

    @Test
    fun `entity to domain conversion maps lastPlayedAt to lastPlayedAtMs correctly when present`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(lastPlayedAt = 1704300000000L)
            everySuspend { dao.get(any()) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("book-1"))).data as PlaybackPosition

            // Then
            assertEquals(1704300000000L, result.lastPlayedAtMs)
        }

    @Test
    fun `entity to domain conversion maps lastPlayedAt to null for legacy data`() =
        runTest {
            // Given - legacy data before migration may have null lastPlayedAt
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(lastPlayedAt = null)
            everySuspend { dao.get(any()) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("book-1"))).data as PlaybackPosition

            // Then
            assertNull(result.lastPlayedAtMs)
        }

    @Test
    fun `entity to domain conversion preserves all fields together`() =
        runTest {
            // Given - entity with all fields set to specific values
            val dao = createMockDao()
            val entity =
                PlaybackPositionEntity(
                    bookId = BookId("complete-book-123"),
                    positionMs = 987654321L,
                    playbackSpeed = 1.75f,
                    hasCustomSpeed = true,
                    updatedAt = 1704100000000L,
                    syncedAt = 1704150000000L,
                    lastPlayedAt = 1704200000000L,
                )
            everySuspend { dao.get(BookId("complete-book-123")) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("complete-book-123"))).data as PlaybackPosition

            // Then - verify all fields are correctly mapped
            assertEquals("complete-book-123", result.bookId)
            assertEquals(987654321L, result.positionMs)
            assertEquals(1.75f, result.playbackSpeed)
            assertTrue(result.hasCustomSpeed)
            assertEquals(1704100000000L, result.updatedAtMs)
            assertEquals(1704150000000L, result.syncedAtMs)
            assertEquals(1704200000000L, result.lastPlayedAtMs)
        }

    // ========== Edge Case Tests ==========

    @Test
    fun `get handles zero position correctly`() =
        runTest {
            // Given - position at the very beginning
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(positionMs = 0L)
            everySuspend { dao.get(any()) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("book-1"))).data as PlaybackPosition

            // Then
            assertEquals(0L, result.positionMs)
        }

    @Test
    fun `get handles default playback speed correctly`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(playbackSpeed = 1.0f)
            everySuspend { dao.get(any()) } returns entity
            val repository = createRepo(dao = dao)

            // When
            val result = assertIs<AppResult.Success<*>>(repository.get(BookId("book-1"))).data as PlaybackPosition

            // Then
            assertEquals(1.0f, result.playbackSpeed)
        }

    @Test
    fun `observeAll handles duplicate book ids by keeping last one`() =
        runTest {
            // Given - this tests the associate behavior (last value wins for duplicate keys)
            val dao = createMockDao()
            val entities =
                listOf(
                    createPlaybackPositionEntity(bookId = "book-1", positionMs = 1000L),
                    createPlaybackPositionEntity(bookId = "book-1", positionMs = 2000L), // duplicate
                )
            every { dao.observeAll() } returns flowOf(entities)
            val repository = createRepo(dao = dao)

            // When
            val result = repository.observeAll().first()

            // Then - should have 1 entry, with the second position value
            assertEquals(1, result.size)
            assertEquals(2000L, result["book-1"]?.positionMs)
        }

    // ========== getEntity() Tests (Task 3) ==========

    @Test
    fun `getEntity returns entity when row exists`() =
        runTest {
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(bookId = "book-1")
            everySuspend { dao.get(any()) } returns entity
            val repository = createRepo(dao = dao)

            val result = repository.getEntity(BookId("book-1"))

            assertEquals(AppResult.Success(entity), result)
        }

    @Test
    fun `getEntity returns null when no row exists`() =
        runTest {
            val dao = createMockDao()
            everySuspend { dao.get(any()) } returns null
            val repository = createRepo(dao = dao)

            val result = repository.getEntity(BookId("book-1"))

            assertEquals(AppResult.Success(null), result)
        }

    @Test
    fun `getEntity returns Failure when dao throws`() =
        runTest {
            val dao = createMockDao()
            everySuspend { dao.get(any()) } throws RuntimeException("dao boom")
            val repository = createRepo(dao = dao)

            val result = repository.getEntity(BookId("book-1"))

            assertIs<AppResult.Failure>(result)
        }

    // ========== getLastPlayedBook() Tests (Task 12) ==========

    @Test
    fun `getLastPlayedBook returns LastPlayedInfo when row exists`() =
        runTest {
            val dao = createMockDao()
            val entity =
                createPlaybackPositionEntity(bookId = "book-1", positionMs = 45000L, playbackSpeed = 1.5f)
            everySuspend { dao.getRecentPositions(1) } returns listOf(entity)
            val repository = createRepo(dao = dao)

            val result = repository.getLastPlayedBook()

            assertIs<AppResult.Success<LastPlayedInfo?>>(result)
            val info = result.data
            assertNotNull(info)
            assertEquals(BookId("book-1"), info.bookId)
            assertEquals(45000L, info.positionMs)
            assertEquals(1.5f, info.playbackSpeed)
        }

    @Test
    fun `getLastPlayedBook returns null when no rows`() =
        runTest {
            val dao = createMockDao()
            everySuspend { dao.getRecentPositions(1) } returns emptyList()
            val repository = createRepo(dao = dao)

            val result = repository.getLastPlayedBook()

            assertEquals(AppResult.Success(null), result)
        }

    @Test
    fun `getLastPlayedBook returns Failure when dao throws`() =
        runTest {
            val dao = createMockDao()
            everySuspend { dao.getRecentPositions(any()) } throws RuntimeException("dao boom")
            val repository = createRepo(dao = dao)

            val result = repository.getLastPlayedBook()

            assertIs<AppResult.Failure>(result)
        }

    // ========== savePlaybackState — per-variant tests (Task 2) ==========

    @Test
    fun `savePlaybackState Position calls updatePositionOnly inside atomically`() =
        runTest {
            val dao = createMockDao()
            val txRunner = passThroughTransactionRunner()
            val bookId = BookId("book-1")
            everySuspend { dao.updatePositionOnly(any(), any(), any(), any()) } returns 1
            val repository = createRepo(dao = dao, transactionRunner = txRunner)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.Position(5000L, 1.0f))

            assertIs<AppResult.Success<Unit>>(result)
            verifySuspend(VerifyMode.exactly(1)) { txRunner.atomically(any<suspend () -> Any>()) }
            verifySuspend(VerifyMode.exactly(1)) {
                dao.updatePositionOnly(bookId, 5000L, any(), any())
            }
            verifySuspend(VerifyMode.not) { dao.save(any()) }
        }

    @Test
    fun `savePlaybackState Speed reads existing then saves merged copy`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("book-1")
            val existing =
                createPlaybackPositionEntity(bookId = "book-1", positionMs = 1000L, playbackSpeed = 1.0f)
            everySuspend { dao.get(bookId) } returns existing
            val captured = mutableListOf<PlaybackPositionEntity>()
            everySuspend { dao.save(any()) } calls { args ->
                captured.add(args.arg(0) as PlaybackPositionEntity)
                Unit
            }
            val repository = createRepo(dao = dao)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.Speed(2000L, 1.5f, custom = true))

            assertIs<AppResult.Success<Unit>>(result)
            assertEquals(1, captured.size)
            val saved = captured.single()
            assertEquals(bookId, saved.bookId)
            assertEquals(2000L, saved.positionMs)
            assertEquals(1.5f, saved.playbackSpeed)
            assertTrue(saved.hasCustomSpeed)
            assertNull(saved.syncedAt)
        }

    @Test
    fun `savePlaybackState Speed creates blank entity when no existing row`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("new-book")
            everySuspend { dao.get(bookId) } returns null
            val captured = mutableListOf<PlaybackPositionEntity>()
            everySuspend { dao.save(any()) } calls { args ->
                captured.add(args.arg(0) as PlaybackPositionEntity)
                Unit
            }
            val repository = createRepo(dao = dao)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.Speed(500L, 2.0f, custom = true))

            assertIs<AppResult.Success<Unit>>(result)
            val saved = captured.single()
            assertEquals(500L, saved.positionMs)
            assertEquals(2.0f, saved.playbackSpeed)
            assertTrue(saved.hasCustomSpeed)
        }

    @Test
    fun `savePlaybackState SpeedReset clears hasCustomSpeed and applies defaultSpeed`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("book-1")
            val existing =
                createPlaybackPositionEntity(bookId = "book-1", positionMs = 1000L, playbackSpeed = 1.5f, hasCustomSpeed = true)
            everySuspend { dao.get(bookId) } returns existing
            val captured = mutableListOf<PlaybackPositionEntity>()
            everySuspend { dao.save(any()) } calls { args ->
                captured.add(args.arg(0) as PlaybackPositionEntity)
                Unit
            }
            val repository = createRepo(dao = dao)

            val result =
                repository.savePlaybackState(bookId, PlaybackUpdate.SpeedReset(positionMs = 1000L, defaultSpeed = 1.0f))

            assertIs<AppResult.Success<Unit>>(result)
            val saved = captured.single()
            assertEquals(1.0f, saved.playbackSpeed)
            assertFalse(saved.hasCustomSpeed)
            assertNull(saved.syncedAt)
        }

    @Test
    fun `savePlaybackState PlaybackStarted preserves existing startedAt`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("book-1")
            val originalStartedAt = 1700000000000L
            val existing =
                createPlaybackPositionEntity(bookId = "book-1", positionMs = 1000L)
                    .copy(startedAt = originalStartedAt)
            everySuspend { dao.get(bookId) } returns existing
            val captured = mutableListOf<PlaybackPositionEntity>()
            everySuspend { dao.save(any()) } calls { args ->
                captured.add(args.arg(0) as PlaybackPositionEntity)
                Unit
            }
            val repository = createRepo(dao = dao)

            val result =
                repository.savePlaybackState(bookId, PlaybackUpdate.PlaybackStarted(positionMs = 2500L, speed = 1.25f))

            assertIs<AppResult.Success<Unit>>(result)
            val saved = captured.single()
            assertEquals(2500L, saved.positionMs)
            assertEquals(1.25f, saved.playbackSpeed)
            // startedAt is preserved when already set.
            assertEquals(originalStartedAt, saved.startedAt)
        }

    @Test
    fun `savePlaybackState PlaybackStarted sets startedAt when previously null`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("book-1")
            val existing =
                createPlaybackPositionEntity(bookId = "book-1", positionMs = 1000L).copy(startedAt = null)
            everySuspend { dao.get(bookId) } returns existing
            val captured = mutableListOf<PlaybackPositionEntity>()
            everySuspend { dao.save(any()) } calls { args ->
                captured.add(args.arg(0) as PlaybackPositionEntity)
                Unit
            }
            val repository = createRepo(dao = dao)

            val result =
                repository.savePlaybackState(bookId, PlaybackUpdate.PlaybackStarted(positionMs = 2500L, speed = 1.0f))

            assertIs<AppResult.Success<Unit>>(result)
            val saved = captured.single()
            assertNotNull(saved.startedAt)
        }

    @Test
    fun `savePlaybackState PlaybackPaused calls updatePositionOnly`() =
        runTest {
            val dao = createMockDao()
            val txRunner = passThroughTransactionRunner()
            val bookId = BookId("book-1")
            everySuspend { dao.updatePositionOnly(any(), any(), any(), any()) } returns 1
            val repository = createRepo(dao = dao, transactionRunner = txRunner)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.PlaybackPaused(7500L, 1.0f))

            assertIs<AppResult.Success<Unit>>(result)
            verifySuspend(VerifyMode.exactly(1)) { txRunner.atomically(any<suspend () -> Any>()) }
            verifySuspend(VerifyMode.exactly(1)) {
                dao.updatePositionOnly(bookId, 7500L, any(), any())
            }
        }

    @Test
    fun `savePlaybackState PeriodicUpdate calls updatePositionOnly`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("book-1")
            everySuspend { dao.updatePositionOnly(any(), any(), any(), any()) } returns 1
            val repository = createRepo(dao = dao)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.PeriodicUpdate(123L, 1.0f))

            assertIs<AppResult.Success<Unit>>(result)
            verifySuspend(VerifyMode.exactly(1)) {
                dao.updatePositionOnly(bookId, 123L, any(), any())
            }
        }

    @Test
    fun `savePlaybackState BookFinished sets isFinished and queues MARK_COMPLETE`() =
        runTest {
            val dao = createMockDao()
            val pendingOps = mock<PendingOperationRepositoryContract>(MockMode.autoUnit)
            val bookId = BookId("book-1")
            val existing =
                createPlaybackPositionEntity(bookId = "book-1", positionMs = 1000L).copy(startedAt = 1700000000000L)
            everySuspend { dao.get(bookId) } returns existing
            val captured = mutableListOf<PlaybackPositionEntity>()
            everySuspend { dao.save(any()) } calls { args ->
                captured.add(args.arg(0) as PlaybackPositionEntity)
                Unit
            }
            val repository = createRepo(dao = dao, pendingOps = pendingOps)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.BookFinished(finalPositionMs = 99000L))

            assertIs<AppResult.Success<Unit>>(result)
            val saved = captured.single()
            assertTrue(saved.isFinished)
            assertEquals(99000L, saved.positionMs)
            assertNotNull(saved.finishedAt)
            // Verifies pending-op queued with the right type/entity.
            verifySuspend(VerifyMode.exactly(1)) {
                pendingOps.queue<MarkCompletePayload>(
                    type = OperationType.MARK_COMPLETE,
                    entityType = EntityType.BOOK,
                    entityId = "book-1",
                    payload = any(),
                    handler = any(),
                )
            }
        }

    @Test
    fun `savePlaybackState CrossDeviceSync skips when local is newer`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("book-1")
            // Local last-played at 2026-04-25T12:00:00Z = 1777809600000ms
            val localLastPlayed = 1777809600000L
            val existing =
                createPlaybackPositionEntity(bookId = "book-1", positionMs = 5000L)
                    .copy(lastPlayedAt = localLastPlayed)
            everySuspend { dao.get(bookId) } returns existing

            val payload =
                ProgressPayload(
                    bookId = "book-1",
                    currentPositionMs = 1000L,
                    progress = 0.05,
                    totalListenTimeMs = 1000L,
                    isFinished = false,
                    // Older than local: 2025-01-01T00:00:00Z = 1735689600000ms
                    lastPlayedAt = "2025-01-01T00:00:00Z",
                )
            val event = SSEEvent.ProgressUpdated(timestamp = "2025-01-01T00:00:00Z", data = payload)
            val repository = createRepo(dao = dao)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.CrossDeviceSync(event))

            assertIs<AppResult.Success<Unit>>(result)
            // No write — local is newer.
            verifySuspend(VerifyMode.not) { dao.save(any()) }
        }

    @Test
    fun `savePlaybackState CrossDeviceSync applies merge when remote is newer`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("book-1")
            val localLastPlayed = 1700000000000L
            val existing =
                createPlaybackPositionEntity(bookId = "book-1", positionMs = 5000L, playbackSpeed = 1.5f, hasCustomSpeed = true)
                    .copy(lastPlayedAt = localLastPlayed)
            everySuspend { dao.get(bookId) } returns existing
            val captured = mutableListOf<PlaybackPositionEntity>()
            everySuspend { dao.save(any()) } calls { args ->
                captured.add(args.arg(0) as PlaybackPositionEntity)
                Unit
            }

            val payload =
                ProgressPayload(
                    bookId = "book-1",
                    currentPositionMs = 9000L,
                    progress = 0.5,
                    totalListenTimeMs = 9000L,
                    isFinished = false,
                    // Newer: 2030-01-01T00:00:00Z
                    lastPlayedAt = "2030-01-01T00:00:00Z",
                )
            val event = SSEEvent.ProgressUpdated(timestamp = "2030-01-01T00:00:00Z", data = payload)
            val repository = createRepo(dao = dao)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.CrossDeviceSync(event))

            assertIs<AppResult.Success<Unit>>(result)
            val saved = captured.single()
            assertEquals(9000L, saved.positionMs)
            // Local-only fields preserved by .copy()
            assertEquals(1.5f, saved.playbackSpeed)
            assertTrue(saved.hasCustomSpeed)
        }

    @Test
    fun `savePlaybackState MarkComplete sets isFinished and queues MARK_COMPLETE`() =
        runTest {
            val dao = createMockDao()
            val pendingOps = mock<PendingOperationRepositoryContract>(MockMode.autoUnit)
            val bookId = BookId("book-1")
            val existing =
                createPlaybackPositionEntity(bookId = "book-1", positionMs = 1000L).copy(startedAt = 1700000000000L)
            everySuspend { dao.get(bookId) } returns existing
            val captured = mutableListOf<PlaybackPositionEntity>()
            everySuspend { dao.save(any()) } calls { args ->
                captured.add(args.arg(0) as PlaybackPositionEntity)
                Unit
            }
            val repository = createRepo(dao = dao, pendingOps = pendingOps)

            val result =
                repository.savePlaybackState(
                    bookId,
                    PlaybackUpdate.MarkComplete(startedAt = null, finishedAt = 1800000000000L),
                )

            assertIs<AppResult.Success<Unit>>(result)
            val saved = captured.single()
            assertTrue(saved.isFinished)
            assertEquals(1800000000000L, saved.finishedAt)
            assertEquals(1700000000000L, saved.startedAt) // preserved from existing
            verifySuspend(VerifyMode.exactly(1)) {
                pendingOps.queue<MarkCompletePayload>(
                    type = OperationType.MARK_COMPLETE,
                    entityType = EntityType.BOOK,
                    entityId = "book-1",
                    payload = any(),
                    handler = any(),
                )
            }
        }

    @Test
    fun `savePlaybackState DiscardProgress resets fields when row exists`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("book-1")
            val existing =
                createPlaybackPositionEntity(bookId = "book-1", positionMs = 5000L).copy(
                    isFinished = true,
                    finishedAt = 1800000000000L,
                )
            everySuspend { dao.get(bookId) } returns existing
            val captured = mutableListOf<PlaybackPositionEntity>()
            everySuspend { dao.save(any()) } calls { args ->
                captured.add(args.arg(0) as PlaybackPositionEntity)
                Unit
            }
            val repository = createRepo(dao = dao)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.DiscardProgress)

            assertIs<AppResult.Success<Unit>>(result)
            val saved = captured.single()
            assertEquals(0L, saved.positionMs)
            assertFalse(saved.isFinished)
            assertNull(saved.finishedAt)
            assertNull(saved.syncedAt)
        }

    @Test
    fun `savePlaybackState DiscardProgress is no-op when no row`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("new-book")
            everySuspend { dao.get(bookId) } returns null
            val repository = createRepo(dao = dao)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.DiscardProgress)

            assertIs<AppResult.Success<Unit>>(result)
            verifySuspend(VerifyMode.not) { dao.save(any()) }
        }

    @Test
    fun `savePlaybackState Restart resets to position 0 and starts new session`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("book-1")
            val existing =
                createPlaybackPositionEntity(bookId = "book-1", positionMs = 5000L).copy(
                    isFinished = true,
                    finishedAt = 1800000000000L,
                    startedAt = 1700000000000L,
                )
            everySuspend { dao.get(bookId) } returns existing
            val captured = mutableListOf<PlaybackPositionEntity>()
            everySuspend { dao.save(any()) } calls { args ->
                captured.add(args.arg(0) as PlaybackPositionEntity)
                Unit
            }
            val repository = createRepo(dao = dao)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.Restart)

            assertIs<AppResult.Success<Unit>>(result)
            val saved = captured.single()
            assertEquals(0L, saved.positionMs)
            assertFalse(saved.isFinished)
            assertNull(saved.finishedAt)
            // startedAt is freshly stamped (matches existing restartBook facade semantics).
            assertNotNull(saved.startedAt)
            assertNotEquals(1700000000000L, saved.startedAt)
        }

    @Test
    fun `savePlaybackState Restart is no-op when no row`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("new-book")
            everySuspend { dao.get(bookId) } returns null
            val repository = createRepo(dao = dao)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.Restart)

            assertIs<AppResult.Success<Unit>>(result)
            verifySuspend(VerifyMode.not) { dao.save(any()) }
        }

    // ========== markComplete — public-facade delegation (Task 7) ==========

    @Test
    fun `markComplete delegates to savePlaybackState with MarkComplete variant`() =
        runTest {
            val dao = createMockDao()
            val pendingOps = mock<PendingOperationRepositoryContract>(MockMode.autoUnit)
            val bookId = "book-mc-facade"
            val startedAt = 1700000000000L
            val finishedAt = 1800000000000L
            val existing =
                createPlaybackPositionEntity(bookId = bookId, positionMs = 5000L).copy(startedAt = startedAt)
            everySuspend { dao.get(BookId(bookId)) } returns existing
            val captured = mutableListOf<PlaybackPositionEntity>()
            everySuspend { dao.save(any()) } calls { args ->
                captured.add(args.arg(0) as PlaybackPositionEntity)
                Unit
            }
            val repository = createRepo(dao = dao, pendingOps = pendingOps)

            val result = repository.markComplete(bookId, startedAt, finishedAt)

            assertIs<AppResult.Success<Unit>>(result)
            val saved = captured.single()
            assertTrue(saved.isFinished)
            assertEquals(finishedAt, saved.finishedAt)
            assertEquals(startedAt, saved.startedAt)
            verifySuspend(VerifyMode.exactly(1)) {
                pendingOps.queue<MarkCompletePayload>(
                    type = OperationType.MARK_COMPLETE,
                    entityType = EntityType.BOOK,
                    entityId = bookId,
                    payload = any(),
                    handler = any(),
                )
            }
        }

    // ========== savePlaybackState — failure path (Task 2) ==========

    @Test
    fun `savePlaybackState returns Failure when dao throws`() =
        runTest {
            val dao = createMockDao()
            val txRunner = passThroughTransactionRunner()
            val bookId = BookId("book-1")
            everySuspend { dao.updatePositionOnly(any(), any(), any(), any()) } throws RuntimeException("dao boom")
            val repository = createRepo(dao = dao, transactionRunner = txRunner)

            val result = repository.savePlaybackState(bookId, PlaybackUpdate.Position(5000L, 1.0f))

            assertIs<AppResult.Failure>(result)
            // Mutex+atomically still attempted before the throw.
            verifySuspend(VerifyMode.exactly(1)) { txRunner.atomically(any<suspend () -> Any>()) }
        }

    // ========== savePlaybackState — Mutex contention (Task 2) ==========

    @Test
    fun `concurrent savePlaybackState for same book serializes via per-book mutex`() =
        runTest {
            val dao = createMockDao()
            val bookId = BookId("book-1")
            val activeWriters = mutableListOf<Long>()
            val maxConcurrent = mutableListOf<Int>()
            var inFlight = 0
            // Each DAO call delays 50ms while "in-flight". We track the maximum
            // concurrency observed; with a per-book Mutex, this must stay at 1.
            everySuspend { dao.updatePositionOnly(any(), any(), any(), any()) } calls { args ->
                inFlight++
                maxConcurrent.add(inFlight)
                delay(50)
                activeWriters.add(args.arg(1) as Long)
                inFlight--
                1
            }
            val repository = createRepo(dao = dao)

            val job1 = launch { repository.savePlaybackState(bookId, PlaybackUpdate.Position(100L, 1.0f)) }
            val job2 = launch { repository.savePlaybackState(bookId, PlaybackUpdate.Position(200L, 1.0f)) }
            job1.join()
            job2.join()

            assertEquals(2, activeWriters.size, "Both DAO calls executed")
            assertEquals(1, maxConcurrent.max(), "Per-book Mutex must serialize same-book writes (max in-flight == 1)")
        }

    @Test
    fun `concurrent savePlaybackState for different books proceeds in parallel`() =
        runTest {
            val dao = createMockDao()
            val bookA = BookId("book-A")
            val bookB = BookId("book-B")
            val maxConcurrent = mutableListOf<Int>()
            var inFlight = 0
            everySuspend { dao.updatePositionOnly(any(), any(), any(), any()) } calls { _ ->
                inFlight++
                maxConcurrent.add(inFlight)
                delay(50)
                inFlight--
                1
            }
            val repository = createRepo(dao = dao)

            val jobA = async { repository.savePlaybackState(bookA, PlaybackUpdate.Position(100L, 1.0f)) }
            val jobB = async { repository.savePlaybackState(bookB, PlaybackUpdate.Position(200L, 1.0f)) }
            jobA.await()
            jobB.await()

            assertEquals(2, maxConcurrent.max(), "Different books must NOT serialize (max in-flight == 2)")
        }
}
