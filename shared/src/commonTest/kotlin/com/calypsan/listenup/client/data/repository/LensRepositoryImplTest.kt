package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.LensDao
import com.calypsan.listenup.client.data.local.db.LensEntity
import com.calypsan.listenup.client.data.remote.LensApiContract
import com.calypsan.listenup.client.domain.model.Lens
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for LensRepositoryImpl.
 *
 * Tests cover:
 * - observeMyLenses: Observing lenses owned by a specific user
 * - observeDiscoverLenses: Observing lenses from other users for discovery
 * - observeById: Observing a single lens by ID
 * - getById: Synchronous lens retrieval by ID
 * - countDiscoverLenses: Counting lenses from other users
 * - Entity to domain conversion
 *
 * Uses Mokkery for mocking LensDao.
 */
class LensRepositoryImplTest {
    // ========== Test Fixtures ==========

    private fun createMockLensDao(): LensDao = mock()

    private fun createMockLensApi(): LensApiContract = mock()

    private fun createRepository(
        dao: LensDao = createMockLensDao(),
        lensApi: LensApiContract = createMockLensApi(),
    ): LensRepositoryImpl =
        LensRepositoryImpl(dao, lensApi)

    // ========== Test Data Factories ==========

    private fun createLensEntity(
        id: String = "lens-1",
        name: String = "To Read",
        description: String? = "Books I want to read",
        ownerId: String = "user-1",
        ownerDisplayName: String = "John Doe",
        ownerAvatarColor: String = "#FF5733",
        bookCount: Int = 5,
        totalDurationSeconds: Long = 36000L, // 10 hours
        createdAtMs: Long = 1704067200000L,
        updatedAtMs: Long = 1704153600000L,
    ): LensEntity =
        LensEntity(
            id = id,
            name = name,
            description = description,
            ownerId = ownerId,
            ownerDisplayName = ownerDisplayName,
            ownerAvatarColor = ownerAvatarColor,
            bookCount = bookCount,
            totalDurationSeconds = totalDurationSeconds,
            createdAt = Timestamp(createdAtMs),
            updatedAt = Timestamp(updatedAtMs),
        )

    // ========== observeMyLenses Tests ==========

    @Test
    fun `observeMyLenses returns empty list when user has no lenses`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            every { dao.observeMyLenses("user-1") } returns flowOf(emptyList())
            val repository = createRepository(dao)

            // When
            val result = repository.observeMyLenses("user-1").first()

            // Then
            assertTrue(result.isEmpty())
        }

    @Test
    fun `observeMyLenses returns lenses converted to domain models`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entities =
                listOf(
                    createLensEntity(id = "lens-1", name = "To Read", ownerId = "user-1"),
                    createLensEntity(id = "lens-2", name = "Favorites", ownerId = "user-1"),
                )
            every { dao.observeMyLenses("user-1") } returns flowOf(entities)
            val repository = createRepository(dao)

            // When
            val result = repository.observeMyLenses("user-1").first()

            // Then
            assertEquals(2, result.size)
            assertEquals("lens-1", result[0].id)
            assertEquals("To Read", result[0].name)
            assertEquals("lens-2", result[1].id)
            assertEquals("Favorites", result[1].name)
        }

    @Test
    fun `observeMyLenses emits updates when data changes`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val flow = MutableStateFlow(listOf(createLensEntity(id = "lens-1", name = "To Read")))
            every { dao.observeMyLenses("user-1") } returns flow
            val repository = createRepository(dao)

            // When - initial state
            val initial = repository.observeMyLenses("user-1").first()

            // Then
            assertEquals(1, initial.size)
            assertEquals("To Read", initial[0].name)

            // When - update
            flow.value =
                listOf(
                    createLensEntity(id = "lens-1", name = "To Read"),
                    createLensEntity(id = "lens-2", name = "Favorites"),
                )
            val updated = repository.observeMyLenses("user-1").first()

            // Then
            assertEquals(2, updated.size)
        }

    // ========== observeDiscoverLenses Tests ==========

    @Test
    fun `observeDiscoverLenses returns empty list when no lenses from other users`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            every { dao.observeDiscoverLenses("user-1") } returns flowOf(emptyList())
            val repository = createRepository(dao)

            // When
            val result = repository.observeDiscoverLenses("user-1").first()

            // Then
            assertTrue(result.isEmpty())
        }

    @Test
    fun `observeDiscoverLenses returns lenses from other users`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entities =
                listOf(
                    createLensEntity(
                        id = "lens-1",
                        name = "Sci-Fi Collection",
                        ownerId = "user-2",
                        ownerDisplayName = "Jane Smith",
                    ),
                    createLensEntity(
                        id = "lens-2",
                        name = "Mystery Novels",
                        ownerId = "user-3",
                        ownerDisplayName = "Bob Wilson",
                    ),
                )
            every { dao.observeDiscoverLenses("user-1") } returns flowOf(entities)
            val repository = createRepository(dao)

            // When
            val result = repository.observeDiscoverLenses("user-1").first()

            // Then
            assertEquals(2, result.size)
            assertEquals("user-2", result[0].ownerId)
            assertEquals("Jane Smith", result[0].ownerDisplayName)
            assertEquals("user-3", result[1].ownerId)
            assertEquals("Bob Wilson", result[1].ownerDisplayName)
        }

    @Test
    fun `observeDiscoverLenses excludes current user lenses via DAO`() =
        runTest {
            // Given: DAO is expected to filter by currentUserId
            val dao = createMockLensDao()
            val currentUserId = "current-user"
            every { dao.observeDiscoverLenses(currentUserId) } returns flowOf(emptyList())
            val repository = createRepository(dao)

            // When
            repository.observeDiscoverLenses(currentUserId).first()

            // Then: DAO was called with correct userId (filtering happens at DAO level)
            // This is verified by the mock setup - if wrong userId was passed, it would fail
        }

    // ========== observeById Tests ==========

    @Test
    fun `observeById returns null when lens not found`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            every { dao.observeById("non-existent") } returns flowOf(null)
            val repository = createRepository(dao)

            // When
            val result = repository.observeById("non-existent").first()

            // Then
            assertNull(result)
        }

    @Test
    fun `observeById returns lens converted to domain model`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entity =
                createLensEntity(
                    id = "lens-1",
                    name = "To Read",
                    description = "My reading list",
                    ownerId = "user-1",
                    ownerDisplayName = "John Doe",
                    ownerAvatarColor = "#FF5733",
                    bookCount = 10,
                    totalDurationSeconds = 72000L,
                    createdAtMs = 1704067200000L,
                    updatedAtMs = 1704153600000L,
                )
            every { dao.observeById("lens-1") } returns flowOf(entity)
            val repository = createRepository(dao)

            // When
            val result = repository.observeById("lens-1").first()

            // Then
            assertEquals("lens-1", result?.id)
            assertEquals("To Read", result?.name)
            assertEquals("My reading list", result?.description)
            assertEquals("user-1", result?.ownerId)
            assertEquals("John Doe", result?.ownerDisplayName)
            assertEquals("#FF5733", result?.ownerAvatarColor)
            assertEquals(10, result?.bookCount)
            assertEquals(72000L, result?.totalDurationSeconds)
            assertEquals(1704067200000L, result?.createdAtMs)
            assertEquals(1704153600000L, result?.updatedAtMs)
        }

    @Test
    fun `observeById emits updates when lens changes`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val flow = MutableStateFlow<LensEntity?>(createLensEntity(id = "lens-1", name = "To Read"))
            every { dao.observeById("lens-1") } returns flow
            val repository = createRepository(dao)

            // When - initial state
            val initial = repository.observeById("lens-1").first()

            // Then
            assertEquals("To Read", initial?.name)

            // When - update
            flow.value = createLensEntity(id = "lens-1", name = "Must Read")
            val updated = repository.observeById("lens-1").first()

            // Then
            assertEquals("Must Read", updated?.name)
        }

    @Test
    fun `observeById emits null when lens is deleted`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val flow = MutableStateFlow<LensEntity?>(createLensEntity(id = "lens-1", name = "To Read"))
            every { dao.observeById("lens-1") } returns flow
            val repository = createRepository(dao)

            // When - initial state has lens
            val initial = repository.observeById("lens-1").first()
            assertEquals("To Read", initial?.name)

            // When - lens is deleted
            flow.value = null
            val afterDeletion = repository.observeById("lens-1").first()

            // Then
            assertNull(afterDeletion)
        }

    // ========== getById Tests ==========

    @Test
    fun `getById returns null when lens not found`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            everySuspend { dao.getById("non-existent") } returns null
            val repository = createRepository(dao)

            // When
            val result = repository.getById("non-existent")

            // Then
            assertNull(result)
        }

    @Test
    fun `getById returns lens converted to domain model`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entity =
                createLensEntity(
                    id = "lens-1",
                    name = "Favorites",
                    description = "My favorite books",
                    ownerId = "user-1",
                    ownerDisplayName = "Alice",
                    ownerAvatarColor = "#00FF00",
                    bookCount = 25,
                    totalDurationSeconds = 180000L,
                    createdAtMs = 1700000000000L,
                    updatedAtMs = 1700100000000L,
                )
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val result = repository.getById("lens-1")

            // Then
            assertEquals("lens-1", result?.id)
            assertEquals("Favorites", result?.name)
            assertEquals("My favorite books", result?.description)
            assertEquals("user-1", result?.ownerId)
            assertEquals("Alice", result?.ownerDisplayName)
            assertEquals("#00FF00", result?.ownerAvatarColor)
            assertEquals(25, result?.bookCount)
            assertEquals(180000L, result?.totalDurationSeconds)
            assertEquals(1700000000000L, result?.createdAtMs)
            assertEquals(1700100000000L, result?.updatedAtMs)
        }

    @Test
    fun `getById calls DAO with correct id`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            everySuspend { dao.getById("specific-lens-id") } returns null
            val repository = createRepository(dao)

            // When
            repository.getById("specific-lens-id")

            // Then
            verifySuspend { dao.getById("specific-lens-id") }
        }

    // ========== countDiscoverLenses Tests ==========

    @Test
    fun `countDiscoverLenses returns zero when no lenses from other users`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            everySuspend { dao.countDiscoverLenses("user-1") } returns 0
            val repository = createRepository(dao)

            // When
            val result = repository.countDiscoverLenses("user-1")

            // Then
            assertEquals(0, result)
        }

    @Test
    fun `countDiscoverLenses returns correct count`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            everySuspend { dao.countDiscoverLenses("user-1") } returns 15
            val repository = createRepository(dao)

            // When
            val result = repository.countDiscoverLenses("user-1")

            // Then
            assertEquals(15, result)
        }

    @Test
    fun `countDiscoverLenses calls DAO with correct userId`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val currentUserId = "current-user-123"
            everySuspend { dao.countDiscoverLenses(currentUserId) } returns 5
            val repository = createRepository(dao)

            // When
            repository.countDiscoverLenses(currentUserId)

            // Then
            verifySuspend { dao.countDiscoverLenses(currentUserId) }
        }

    // ========== Entity to Domain Conversion Tests ==========

    @Test
    fun `toDomain converts all fields correctly`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entity =
                createLensEntity(
                    id = "test-lens",
                    name = "Test Lens",
                    description = "A test description",
                    ownerId = "owner-123",
                    ownerDisplayName = "Test Owner",
                    ownerAvatarColor = "#AABBCC",
                    bookCount = 42,
                    totalDurationSeconds = 123456L,
                    createdAtMs = 1609459200000L, // Jan 1, 2021 00:00:00 UTC
                    updatedAtMs = 1640995200000L, // Jan 1, 2022 00:00:00 UTC
                )
            everySuspend { dao.getById("test-lens") } returns entity
            val repository = createRepository(dao)

            // When
            val result = repository.getById("test-lens")

            // Then: All fields should match
            assertEquals("test-lens", result?.id)
            assertEquals("Test Lens", result?.name)
            assertEquals("A test description", result?.description)
            assertEquals("owner-123", result?.ownerId)
            assertEquals("Test Owner", result?.ownerDisplayName)
            assertEquals("#AABBCC", result?.ownerAvatarColor)
            assertEquals(42, result?.bookCount)
            assertEquals(123456L, result?.totalDurationSeconds)
            assertEquals(1609459200000L, result?.createdAtMs)
            assertEquals(1640995200000L, result?.updatedAtMs)
        }

    @Test
    fun `toDomain handles null description`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entity = createLensEntity(description = null)
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val result = repository.getById("lens-1")

            // Then
            assertNull(result?.description)
        }

    @Test
    fun `toDomain handles zero book count`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entity = createLensEntity(bookCount = 0)
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val result = repository.getById("lens-1")

            // Then
            assertEquals(0, result?.bookCount)
        }

    @Test
    fun `toDomain handles zero duration`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entity = createLensEntity(totalDurationSeconds = 0L)
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val result = repository.getById("lens-1")

            // Then
            assertEquals(0L, result?.totalDurationSeconds)
        }

    @Test
    fun `toDomain handles large duration values`() =
        runTest {
            // Given: A lens with 1000 hours of content
            val dao = createMockLensDao()
            val largeSeconds = 1000L * 60 * 60 // 1000 hours
            val entity = createLensEntity(totalDurationSeconds = largeSeconds)
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val result = repository.getById("lens-1")

            // Then
            assertEquals(largeSeconds, result?.totalDurationSeconds)
        }

    @Test
    fun `toDomain converts timestamps from Timestamp epochMillis`() =
        runTest {
            // Given: Entity with specific timestamps
            val dao = createMockLensDao()
            val createdMs = 1704067200000L // Jan 1, 2024 00:00:00 UTC
            val updatedMs = 1704153600000L // Jan 2, 2024 00:00:00 UTC
            val entity = createLensEntity(createdAtMs = createdMs, updatedAtMs = updatedMs)
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val result = repository.getById("lens-1")

            // Then: Domain model should have epoch millis values
            assertEquals(createdMs, result?.createdAtMs)
            assertEquals(updatedMs, result?.updatedAtMs)
        }

    // ========== Domain Model Behavior Tests ==========

    @Test
    fun `domain lens displayName returns name for owner`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entity = createLensEntity(name = "To Read", ownerId = "user-1")
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val lens = repository.getById("lens-1")

            // Then: Owner sees just the lens name
            assertEquals("To Read", lens?.displayName("user-1"))
        }

    @Test
    fun `domain lens displayName includes owner name for other users`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entity =
                createLensEntity(
                    name = "To Read",
                    ownerId = "user-1",
                    ownerDisplayName = "Simon",
                )
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val lens = repository.getById("lens-1")

            // Then: Other users see "Owner's Lens Name"
            assertEquals("Simon's To Read", lens?.displayName("user-2"))
        }

    @Test
    fun `domain lens isOwnedBy returns true for owner`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entity = createLensEntity(ownerId = "user-1")
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val lens = repository.getById("lens-1")

            // Then
            assertTrue(lens?.isOwnedBy("user-1") == true)
        }

    @Test
    fun `domain lens isOwnedBy returns false for non-owner`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entity = createLensEntity(ownerId = "user-1")
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val lens = repository.getById("lens-1")

            // Then
            assertTrue(lens?.isOwnedBy("user-2") == false)
        }

    @Test
    fun `domain lens formattedDuration returns hours and minutes`() =
        runTest {
            // Given: 2 hours 30 minutes = 9000 seconds
            val dao = createMockLensDao()
            val entity = createLensEntity(totalDurationSeconds = 9000L)
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val lens = repository.getById("lens-1")

            // Then
            assertEquals("2h 30m", lens?.formattedDuration)
        }

    @Test
    fun `domain lens formattedDuration returns only hours when no minutes`() =
        runTest {
            // Given: 3 hours = 10800 seconds
            val dao = createMockLensDao()
            val entity = createLensEntity(totalDurationSeconds = 10800L)
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val lens = repository.getById("lens-1")

            // Then
            assertEquals("3h", lens?.formattedDuration)
        }

    @Test
    fun `domain lens formattedDuration returns only minutes when less than an hour`() =
        runTest {
            // Given: 45 minutes = 2700 seconds
            val dao = createMockLensDao()
            val entity = createLensEntity(totalDurationSeconds = 2700L)
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val lens = repository.getById("lens-1")

            // Then
            assertEquals("45m", lens?.formattedDuration)
        }

    @Test
    fun `domain lens formattedDuration returns 0m for zero duration`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entity = createLensEntity(totalDurationSeconds = 0L)
            everySuspend { dao.getById("lens-1") } returns entity
            val repository = createRepository(dao)

            // When
            val lens = repository.getById("lens-1")

            // Then
            assertEquals("0m", lens?.formattedDuration)
        }

    // ========== Multiple Lenses Flow Tests ==========

    @Test
    fun `observeMyLenses converts all entities in list`() =
        runTest {
            // Given
            val dao = createMockLensDao()
            val entities =
                listOf(
                    createLensEntity(id = "lens-1", name = "To Read", bookCount = 5),
                    createLensEntity(id = "lens-2", name = "Favorites", bookCount = 10),
                    createLensEntity(id = "lens-3", name = "Completed", bookCount = 20),
                )
            every { dao.observeMyLenses("user-1") } returns flowOf(entities)
            val repository = createRepository(dao)

            // When
            val result = repository.observeMyLenses("user-1").first()

            // Then
            assertEquals(3, result.size)
            assertEquals(5, result[0].bookCount)
            assertEquals(10, result[1].bookCount)
            assertEquals(20, result[2].bookCount)
        }

    @Test
    fun `observeDiscoverLenses preserves ordering from DAO`() =
        runTest {
            // Given: Lenses ordered by ownerDisplayName and name (as per DAO query)
            val dao = createMockLensDao()
            val entities =
                listOf(
                    createLensEntity(id = "lens-1", name = "A Lens", ownerDisplayName = "Alice"),
                    createLensEntity(id = "lens-2", name = "B Lens", ownerDisplayName = "Alice"),
                    createLensEntity(id = "lens-3", name = "C Lens", ownerDisplayName = "Bob"),
                )
            every { dao.observeDiscoverLenses("user-1") } returns flowOf(entities)
            val repository = createRepository(dao)

            // When
            val result = repository.observeDiscoverLenses("user-1").first()

            // Then: Order should be preserved
            assertEquals("lens-1", result[0].id)
            assertEquals("lens-2", result[1].id)
            assertEquals("lens-3", result[2].id)
        }
}
