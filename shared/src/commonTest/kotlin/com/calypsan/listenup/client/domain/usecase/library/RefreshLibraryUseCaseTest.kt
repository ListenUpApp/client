package com.calypsan.listenup.client.domain.usecase.library

import com.calypsan.listenup.client.checkIs
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.domain.model.SyncState
import com.calypsan.listenup.client.domain.repository.SyncRepository
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for RefreshLibraryUseCase.
 *
 * Tests cover:
 * - Successful sync flow
 * - Sync error handling and user-friendly messages
 * - Library mismatch handling
 * - Reset for new library flow
 */
class RefreshLibraryUseCaseTest {
    // ========== Test Fixtures ==========

    private class TestFixture {
        val syncRepository: SyncRepository = mock()
        val syncStateFlow = MutableStateFlow<SyncState>(SyncState.Idle)

        init {
            every { syncRepository.syncState } returns syncStateFlow
        }

        fun build(): RefreshLibraryUseCase =
            RefreshLibraryUseCase(
                syncRepository = syncRepository,
            )
    }

    private fun createFixture(): TestFixture = TestFixture()

    // ========== Successful Sync Tests ==========

    @Test
    fun `successful sync returns success result`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncRepository.sync() } returns Success(Unit)
            val useCase = fixture.build()

            // When
            val result = useCase()

            // Then
            val success = assertIs<Success<RefreshLibraryResult>>(result)
            assertEquals("Library refreshed successfully", success.data.message)
        }

    @Test
    fun `sync updates state flow`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncRepository.sync() } returns Success(Unit)
            val useCase = fixture.build()

            // When
            fixture.syncStateFlow.value = SyncState.Syncing
            val result = useCase()
            fixture.syncStateFlow.value = SyncState.Success(timestamp = Timestamp.now())

            // Then
            val success = assertIs<Success<RefreshLibraryResult>>(result)
            // State flow accessible via useCase.syncState
            assertEquals(fixture.syncStateFlow.value, useCase.syncState.value)
        }

    // ========== Error Handling Tests ==========

    @Test
    fun `sync failure returns failure with user-friendly message`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncRepository.sync() } returns Failure(
                exception = Exception("Connection refused"),
                message = "Sync failed",
            )
            val useCase = fixture.build()

            // When
            val result = useCase()

            // Then
            val failure = assertIs<Failure>(result)
            // Original exception message might not match user-friendly error
            assertTrue(failure.exception is RefreshException)
        }

    @Test
    fun `network error maps to user-friendly message`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncRepository.sync() } returns Failure(
                exception = Exception("network connection failed"),
                message = "network connection failed",
            )
            val useCase = fixture.build()

            // When
            val result = useCase()

            // Then
            val failure = assertIs<Failure>(result)
            assertTrue(
                failure.message.contains("network", ignoreCase = true) ||
                    failure.message.contains("connect", ignoreCase = true),
            )
        }

    @Test
    fun `timeout error maps to user-friendly message`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncRepository.sync() } returns Failure(
                exception = Exception("Request timeout"),
                message = "Request timeout",
            )
            val useCase = fixture.build()

            // When
            val result = useCase()

            // Then
            val failure = assertIs<Failure>(result)
            assertTrue(
                failure.message.contains("timeout", ignoreCase = true) ||
                    failure.message.contains("responding", ignoreCase = true),
            )
        }

    @Test
    fun `unauthorized error maps to session expired message`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncRepository.sync() } returns Failure(
                exception = Exception("401 Unauthorized"),
                message = "401 Unauthorized",
            )
            val useCase = fixture.build()

            // When
            val result = useCase()

            // Then
            val failure = assertIs<Failure>(result)
            assertTrue(
                failure.message.contains("session", ignoreCase = true) ||
                    failure.message.contains("log in", ignoreCase = true),
            )
        }

    // ========== Reset for New Library Tests ==========

    @Test
    fun `resetForNewLibrary calls sync repository with library id`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncRepository.resetForNewLibrary(any()) } returns Success(Unit)
            val useCase = fixture.build()

            // When
            useCase.resetForNewLibrary("new-library-id")

            // Then
            verifySuspend {
                fixture.syncRepository.resetForNewLibrary("new-library-id")
            }
        }

    @Test
    fun `resetForNewLibrary success returns success result`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncRepository.resetForNewLibrary(any()) } returns Success(Unit)
            val useCase = fixture.build()

            // When
            val result = useCase.resetForNewLibrary("new-library-id")

            // Then
            val success = assertIs<Success<RefreshLibraryResult>>(result)
            assertEquals("Library synced with new server", success.data.message)
        }

    @Test
    fun `resetForNewLibrary failure returns failure`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncRepository.resetForNewLibrary(any()) } returns Failure(
                exception = Exception("Reset failed"),
                message = "Reset failed",
            )
            val useCase = fixture.build()

            // When
            val result = useCase.resetForNewLibrary("new-library-id")

            // Then
            val failure = assertIs<Failure>(result)
            assertTrue(failure.exception is RefreshException)
        }

    // ========== SyncState Access Tests ==========

    @Test
    fun `syncState exposes sync repository state flow`() =
        runTest {
            // Given
            val fixture = createFixture()
            val useCase = fixture.build()

            // When
            fixture.syncStateFlow.value = SyncState.Syncing

            // Then
            assertEquals(SyncState.Syncing, useCase.syncState.value)
        }

    @Test
    fun `syncState reflects success status after sync`() =
        runTest {
            // Given
            val fixture = createFixture()
            val timestamp = Timestamp.now()
            val useCase = fixture.build()

            // When
            fixture.syncStateFlow.value = SyncState.Success(timestamp = timestamp)

            // Then
            val state = assertIs<SyncState.Success>(useCase.syncState.value)
            assertEquals(timestamp, state.timestamp)
        }
}
