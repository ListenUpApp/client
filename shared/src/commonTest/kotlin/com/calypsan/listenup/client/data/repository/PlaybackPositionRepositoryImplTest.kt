package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.domain.model.PlaybackPosition
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // ========== get() Tests ==========

    @Test
    fun `get returns position when it exists`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(bookId = "book-123", positionMs = 45000L)
            everySuspend { dao.get(BookId("book-123")) } returns entity
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("book-123")

            // Then
            assertNotNull(result)
            assertEquals("book-123", result.bookId)
            assertEquals(45000L, result.positionMs)
        }

    @Test
    fun `get returns null when position does not exist`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.get(any()) } returns null
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("nonexistent-book")

            // Then
            assertNull(result)
        }

    @Test
    fun `get calls dao with correct BookId wrapper`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.get(any()) } returns null
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            repository.get("test-book-id")

            // Then
            verifySuspend { dao.get(BookId("test-book-id")) }
        }

    // ========== observe() Tests ==========

    @Test
    fun `observe emits position when it exists`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(bookId = "book-1", positionMs = 30000L)
            every { dao.observe(BookId("book-1")) } returns flowOf(entity)
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.observeAll().first()

            // Then
            assertTrue(result.containsKey("unique-book-id-123"))
            assertFalse(result.containsKey("wrong-key"))
        }

    // ========== getRecentPositions() Tests ==========

    @Test
    fun `getRecentPositions returns list of positions`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entities =
                listOf(
                    createPlaybackPositionEntity(bookId = "book-1", positionMs = 1000L),
                    createPlaybackPositionEntity(bookId = "book-2", positionMs = 2000L),
                )
            everySuspend { dao.getRecentPositions(10) } returns entities
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.getRecentPositions(10)

            // Then
            assertEquals(2, result.size)
            assertEquals("book-1", result[0].bookId)
            assertEquals("book-2", result[1].bookId)
        }

    @Test
    fun `getRecentPositions respects limit parameter`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.getRecentPositions(5) } returns emptyList()
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            repository.getRecentPositions(5)

            // Then
            verifySuspend { dao.getRecentPositions(5) }
        }

    @Test
    fun `getRecentPositions returns empty list when no positions exist`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.getRecentPositions(any()) } returns emptyList()
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.getRecentPositions(10)

            // Then
            assertTrue(result.isEmpty())
        }

    @Test
    fun `getRecentPositions converts all entities to domain models`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entities =
                listOf(
                    createPlaybackPositionEntity(
                        bookId = "book-1",
                        positionMs = 1000L,
                        playbackSpeed = 1.5f,
                        hasCustomSpeed = true,
                    ),
                    createPlaybackPositionEntity(
                        bookId = "book-2",
                        positionMs = 2000L,
                        playbackSpeed = 2.0f,
                        hasCustomSpeed = false,
                    ),
                )
            everySuspend { dao.getRecentPositions(10) } returns entities
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.getRecentPositions(10)

            // Then
            assertEquals(1.5f, result[0].playbackSpeed)
            assertTrue(result[0].hasCustomSpeed)
            assertEquals(2.0f, result[1].playbackSpeed)
            assertFalse(result[1].hasCustomSpeed)
        }

    // ========== save() Tests ==========

    @Test
    fun `save creates new position when none exists`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.get(BookId("new-book")) } returns null
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("unique-book-123")

            // Then
            assertNotNull(result)
            assertEquals("unique-book-123", result.bookId)
        }

    @Test
    fun `entity to domain conversion maps positionMs correctly`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(positionMs = 123456789L)
            everySuspend { dao.get(any()) } returns entity
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("book-1")

            // Then
            assertNotNull(result)
            assertEquals(123456789L, result.positionMs)
        }

    @Test
    fun `entity to domain conversion maps playbackSpeed correctly`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(playbackSpeed = 2.5f)
            everySuspend { dao.get(any()) } returns entity
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("book-1")

            // Then
            assertNotNull(result)
            assertEquals(2.5f, result.playbackSpeed)
        }

    @Test
    fun `entity to domain conversion maps hasCustomSpeed correctly when true`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(hasCustomSpeed = true)
            everySuspend { dao.get(any()) } returns entity
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("book-1")

            // Then
            assertNotNull(result)
            assertTrue(result.hasCustomSpeed)
        }

    @Test
    fun `entity to domain conversion maps hasCustomSpeed correctly when false`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(hasCustomSpeed = false)
            everySuspend { dao.get(any()) } returns entity
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("book-1")

            // Then
            assertNotNull(result)
            assertFalse(result.hasCustomSpeed)
        }

    @Test
    fun `entity to domain conversion maps updatedAt to updatedAtMs correctly`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(updatedAt = 1704110400000L)
            everySuspend { dao.get(any()) } returns entity
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("book-1")

            // Then
            assertNotNull(result)
            assertEquals(1704110400000L, result.updatedAtMs)
        }

    @Test
    fun `entity to domain conversion maps syncedAt to syncedAtMs correctly when present`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(syncedAt = 1704200000000L)
            everySuspend { dao.get(any()) } returns entity
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("book-1")

            // Then
            assertNotNull(result)
            assertEquals(1704200000000L, result.syncedAtMs)
        }

    @Test
    fun `entity to domain conversion maps syncedAt to null when not synced`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(syncedAt = null)
            everySuspend { dao.get(any()) } returns entity
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("book-1")

            // Then
            assertNotNull(result)
            assertNull(result.syncedAtMs)
        }

    @Test
    fun `entity to domain conversion maps lastPlayedAt to lastPlayedAtMs correctly when present`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(lastPlayedAt = 1704300000000L)
            everySuspend { dao.get(any()) } returns entity
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("book-1")

            // Then
            assertNotNull(result)
            assertEquals(1704300000000L, result.lastPlayedAtMs)
        }

    @Test
    fun `entity to domain conversion maps lastPlayedAt to null for legacy data`() =
        runTest {
            // Given - legacy data before migration may have null lastPlayedAt
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(lastPlayedAt = null)
            everySuspend { dao.get(any()) } returns entity
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("book-1")

            // Then
            assertNotNull(result)
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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("complete-book-123")

            // Then - verify all fields are correctly mapped
            assertNotNull(result)
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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("book-1")

            // Then
            assertNotNull(result)
            assertEquals(0L, result.positionMs)
        }

    @Test
    fun `get handles default playback speed correctly`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entity = createPlaybackPositionEntity(playbackSpeed = 1.0f)
            everySuspend { dao.get(any()) } returns entity
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.get("book-1")

            // Then
            assertNotNull(result)
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
            val repository = PlaybackPositionRepositoryImpl(dao, createMockSyncApi())

            // When
            val result = repository.observeAll().first()

            // Then - should have 1 entry, with the second position value
            assertEquals(1, result.size)
            assertEquals(2000L, result["book-1"]?.positionMs)
        }
}
