package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.SyncMetadataEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for SyncStatusRepositoryImpl.
 *
 * Tests cover:
 * - Get last sync time (returns timestamp or null)
 * - Set last sync time (stores timestamp correctly)
 * - Clear last sync time (deletes metadata)
 *
 * Uses Mokkery for mocking SyncDao.
 * Follows Given-When-Then style.
 */
class SyncStatusRepositoryImplTest {
    private fun createMockDao(): SyncDao = mock<SyncDao>(MockMode.autoUnit)

    // ========== getLastSyncTime() Tests ==========

    @Test
    fun `getLastSyncTime returns timestamp when sync time exists`() =
        runTest {
            // Given
            val dao = createMockDao()
            val epochMillis = 1704067200000L
            everySuspend { dao.getValue(SyncDao.KEY_LAST_SYNC_BOOKS) } returns epochMillis.toString()
            val repository = SyncStatusRepositoryImpl(dao)

            // When
            val result = repository.getLastSyncTime()

            // Then
            assertEquals(Timestamp(epochMillis), result)
        }

    @Test
    fun `getLastSyncTime returns null when sync time does not exist`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.getValue(SyncDao.KEY_LAST_SYNC_BOOKS) } returns null
            val repository = SyncStatusRepositoryImpl(dao)

            // When
            val result = repository.getLastSyncTime()

            // Then
            assertNull(result)
        }

    @Test
    fun `getLastSyncTime returns null for invalid stored value`() =
        runTest {
            // Given - corrupted data that isn't a valid Long
            val dao = createMockDao()
            everySuspend { dao.getValue(SyncDao.KEY_LAST_SYNC_BOOKS) } returns "not-a-number"
            val repository = SyncStatusRepositoryImpl(dao)

            // When
            val result = repository.getLastSyncTime()

            // Then
            assertNull(result)
        }

    @Test
    fun `getLastSyncTime returns null for empty string value`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.getValue(SyncDao.KEY_LAST_SYNC_BOOKS) } returns ""
            val repository = SyncStatusRepositoryImpl(dao)

            // When
            val result = repository.getLastSyncTime()

            // Then
            assertNull(result)
        }

    @Test
    fun `getLastSyncTime calls dao with correct key`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.getValue(any()) } returns null
            val repository = SyncStatusRepositoryImpl(dao)

            // When
            repository.getLastSyncTime()

            // Then
            verifySuspend { dao.getValue(SyncDao.KEY_LAST_SYNC_BOOKS) }
        }

    // ========== setLastSyncTime() Tests ==========

    @Test
    fun `setLastSyncTime stores timestamp as string`() =
        runTest {
            // Given
            val dao = createMockDao()
            val timestamp = Timestamp(1704067200000L)
            val repository = SyncStatusRepositoryImpl(dao)

            // When
            repository.setLastSyncTime(timestamp)

            // Then
            verifySuspend {
                dao.upsert(
                    SyncMetadataEntity(
                        key = SyncDao.KEY_LAST_SYNC_BOOKS,
                        value = "1704067200000",
                    ),
                )
            }
        }

    @Test
    fun `setLastSyncTime handles zero timestamp`() =
        runTest {
            // Given - edge case: epoch start
            val dao = createMockDao()
            val timestamp = Timestamp(0L)
            val repository = SyncStatusRepositoryImpl(dao)

            // When
            repository.setLastSyncTime(timestamp)

            // Then
            verifySuspend {
                dao.upsert(
                    SyncMetadataEntity(
                        key = SyncDao.KEY_LAST_SYNC_BOOKS,
                        value = "0",
                    ),
                )
            }
        }

    @Test
    fun `setLastSyncTime handles large timestamp value`() =
        runTest {
            // Given - far future date
            val dao = createMockDao()
            val timestamp = Timestamp(9999999999999L)
            val repository = SyncStatusRepositoryImpl(dao)

            // When
            repository.setLastSyncTime(timestamp)

            // Then
            verifySuspend {
                dao.upsert(
                    SyncMetadataEntity(
                        key = SyncDao.KEY_LAST_SYNC_BOOKS,
                        value = "9999999999999",
                    ),
                )
            }
        }

    // ========== clearLastSyncTime() Tests ==========

    @Test
    fun `clearLastSyncTime calls dao delete with correct key`() =
        runTest {
            // Given
            val dao = createMockDao()
            val repository = SyncStatusRepositoryImpl(dao)

            // When
            repository.clearLastSyncTime()

            // Then
            verifySuspend { dao.delete(SyncDao.KEY_LAST_SYNC_BOOKS) }
        }

    @Test
    fun `clearLastSyncTime does not throw when no sync time exists`() =
        runTest {
            // Given - no existing sync time
            val dao = createMockDao()
            val repository = SyncStatusRepositoryImpl(dao)

            // When/Then - should not throw
            repository.clearLastSyncTime()

            // Verify delete was still called
            verifySuspend { dao.delete(SyncDao.KEY_LAST_SYNC_BOOKS) }
        }

    // ========== Edge Case Tests ==========

    @Test
    fun `getLastSyncTime handles negative timestamp value`() =
        runTest {
            // Given - edge case: negative timestamp (before epoch)
            val dao = createMockDao()
            everySuspend { dao.getValue(SyncDao.KEY_LAST_SYNC_BOOKS) } returns "-1000"
            val repository = SyncStatusRepositoryImpl(dao)

            // When
            val result = repository.getLastSyncTime()

            // Then
            assertEquals(Timestamp(-1000L), result)
        }

    @Test
    fun `getLastSyncTime handles whitespace-only value`() =
        runTest {
            // Given - whitespace value that can't parse to Long
            val dao = createMockDao()
            everySuspend { dao.getValue(SyncDao.KEY_LAST_SYNC_BOOKS) } returns "   "
            val repository = SyncStatusRepositoryImpl(dao)

            // When
            val result = repository.getLastSyncTime()

            // Then
            assertNull(result)
        }
}
