package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.remote.ListenUpApiContract
import com.calypsan.listenup.client.data.remote.model.PlaybackProgressResponse
import com.calypsan.listenup.client.domain.model.Instance
import com.calypsan.listenup.client.domain.model.InstanceId
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Tests for InstanceRepositoryImpl.
 *
 * Tests cover:
 * - First call fetches from API
 * - Subsequent calls return cached data (when forceRefresh=false)
 * - forceRefresh=true bypasses cache and fetches fresh data
 * - Cache is updated on successful API response
 * - Error results don't get cached
 *
 * Uses Mokkery for mocking ListenUpApiContract.
 */
@OptIn(ExperimentalTime::class)
class InstanceRepositoryImplTest {
    // ========== Test Fixtures ==========

    private class TestFixture {
        val api: ListenUpApiContract = mock()

        fun build(): InstanceRepositoryImpl = InstanceRepositoryImpl(api = api)
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs - return failure (tests override as needed)
        everySuspend { fixture.api.getInstance() } returns Failure(RuntimeException("Not configured"))

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createInstance(
        id: String = "instance-1",
        name: String = "Test Server",
        version: String = "1.0.0",
        setupRequired: Boolean = false,
    ): Instance = Instance(
        id = InstanceId(id),
        name = name,
        version = version,
        localUrl = "http://localhost:8080",
        remoteUrl = null,
        setupRequired = setupRequired,
        createdAt = Instant.fromEpochMilliseconds(1704067200000L),
        updatedAt = Instant.fromEpochMilliseconds(1704067200000L),
    )

    // ========== API Fetch Tests ==========

    @Test
    fun `getInstance fetches from API on first call`() = runTest {
        // Given
        val fixture = createFixture()
        val instance = createInstance(name = "My Server")
        everySuspend { fixture.api.getInstance() } returns Success(instance)
        val repository = fixture.build()

        // When
        val result = repository.getInstance()

        // Then
        assertIs<Success<Instance>>(result)
        assertEquals("My Server", result.data.name)
        verifySuspend { fixture.api.getInstance() }
    }

    @Test
    fun `getInstance returns cached data on subsequent calls`() = runTest {
        // Given
        val fixture = createFixture()
        val instance = createInstance(name = "Cached Server")
        var callCount = 0
        everySuspend { fixture.api.getInstance() } returns Success(instance).also { callCount++ }
        val repository = fixture.build()

        // When - first call
        repository.getInstance()

        // When - second call (should use cache)
        val result = repository.getInstance(forceRefresh = false)

        // Then
        assertIs<Success<Instance>>(result)
        assertEquals("Cached Server", result.data.name)
    }

    @Test
    fun `getInstance with forceRefresh true bypasses cache`() = runTest {
        // Given
        val fixture = createFixture()
        val oldInstance = createInstance(name = "Old Server", version = "1.0.0")
        val newInstance = createInstance(name = "New Server", version = "2.0.0")

        everySuspend { fixture.api.getInstance() } returns Success(oldInstance)
        val repository = fixture.build()

        // When - first call caches old instance
        repository.getInstance()

        // Update mock to return new instance
        everySuspend { fixture.api.getInstance() } returns Success(newInstance)

        // When - force refresh to get new instance
        val result = repository.getInstance(forceRefresh = true)

        // Then
        assertIs<Success<Instance>>(result)
        assertEquals("New Server", result.data.name)
        assertEquals("2.0.0", result.data.version)
    }

    @Test
    fun `getInstance caches successful API response`() = runTest {
        // Given
        val fixture = createFixture()
        val instance = createInstance(name = "Server to Cache")
        everySuspend { fixture.api.getInstance() } returns Success(instance)
        val repository = fixture.build()

        // When - first call should cache
        repository.getInstance()

        // When - second call should return cached
        everySuspend { fixture.api.getInstance() } returns Failure(RuntimeException("Should not be called"))
        val result = repository.getInstance(forceRefresh = false)

        // Then - should return cached data, not the failure
        assertIs<Success<Instance>>(result)
        assertEquals("Server to Cache", result.data.name)
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `getInstance returns failure when API fails`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.api.getInstance() } returns Failure(RuntimeException("Network error"))
        val repository = fixture.build()

        // When
        val result = repository.getInstance()

        // Then
        assertIs<Failure>(result)
        assertEquals("Network error", result.exception.message)
    }

    @Test
    fun `getInstance does not cache failure results`() = runTest {
        // Given
        val fixture = createFixture()
        val instance = createInstance(name = "Recovered Server")
        everySuspend { fixture.api.getInstance() } returns Failure(RuntimeException("First call fails"))
        val repository = fixture.build()

        // When - first call fails
        val firstResult = repository.getInstance()
        assertIs<Failure>(firstResult)

        // Now API succeeds
        everySuspend { fixture.api.getInstance() } returns Success(instance)

        // When - second call should try API again (not use cached failure)
        val secondResult = repository.getInstance()

        // Then
        assertIs<Success<Instance>>(secondResult)
        assertEquals("Recovered Server", secondResult.data.name)
    }

    // ========== Instance Property Tests ==========

    @Test
    fun `getInstance returns instance with all properties populated`() = runTest {
        // Given
        val fixture = createFixture()
        val instance = createInstance(
            id = "test-instance-123",
            name = "Full Server",
            version = "2.5.0",
            setupRequired = false,
        )
        everySuspend { fixture.api.getInstance() } returns Success(instance)
        val repository = fixture.build()

        // When
        val result = repository.getInstance()

        // Then
        assertIs<Success<Instance>>(result)
        assertEquals("test-instance-123", result.data.id.value)
        assertEquals("Full Server", result.data.name)
        assertEquals("2.5.0", result.data.version)
        assertEquals(false, result.data.setupRequired)
        assertEquals("http://localhost:8080", result.data.localUrl)
    }

    @Test
    fun `getInstance returns instance needing setup`() = runTest {
        // Given
        val fixture = createFixture()
        val instance = createInstance(setupRequired = true)
        everySuspend { fixture.api.getInstance() } returns Success(instance)
        val repository = fixture.build()

        // When
        val result = repository.getInstance()

        // Then
        assertIs<Success<Instance>>(result)
        assertEquals(true, result.data.setupRequired)
        assertEquals(true, result.data.needsSetup)
        assertEquals(false, result.data.isReady)
    }

    @Test
    fun `getInstance returns instance that is ready`() = runTest {
        // Given
        val fixture = createFixture()
        val instance = createInstance(setupRequired = false)
        everySuspend { fixture.api.getInstance() } returns Success(instance)
        val repository = fixture.build()

        // When
        val result = repository.getInstance()

        // Then
        assertIs<Success<Instance>>(result)
        assertEquals(false, result.data.setupRequired)
        assertEquals(false, result.data.needsSetup)
        assertEquals(true, result.data.isReady)
        assertEquals(true, result.data.hasRootUser)
    }

    // ========== Default Parameter Tests ==========

    @Test
    fun `getInstance defaults to forceRefresh false`() = runTest {
        // Given
        val fixture = createFixture()
        val instance = createInstance(name = "Default Refresh Server")
        everySuspend { fixture.api.getInstance() } returns Success(instance)
        val repository = fixture.build()

        // When - first call
        repository.getInstance()

        // Replace with failure
        everySuspend { fixture.api.getInstance() } returns Failure(RuntimeException("Should not call"))

        // When - call without parameter (defaults to false)
        val result = repository.getInstance()

        // Then - should use cache
        assertIs<Success<Instance>>(result)
        assertEquals("Default Refresh Server", result.data.name)
    }
}
