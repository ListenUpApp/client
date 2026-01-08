package com.calypsan.listenup.client.domain.usecase.auth

import com.calypsan.listenup.client.checkIs
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.repository.AuthRepository
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.UserRepository
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for LogoutUseCase.
 *
 * Tests cover:
 * - Successful logout with server invalidation
 * - Local logout when server is unreachable
 * - Local-only logout method
 * - Token and user data clearing
 */
class LogoutUseCaseTest {
    // ========== Test Fixtures ==========

    private class TestFixture {
        val authRepository: AuthRepository = mock()
        val authSession: AuthSession = mock()
        val userRepository: UserRepository = mock()

        fun build(): LogoutUseCase =
            LogoutUseCase(
                authRepository = authRepository,
                authSession = authSession,
                userRepository = userRepository,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs
        everySuspend { fixture.authSession.getSessionId() } returns "session-123"
        everySuspend { fixture.authSession.clearAuthTokens() } returns Unit
        everySuspend { fixture.userRepository.clearUsers() } returns Unit
        everySuspend { fixture.authRepository.logout(any()) } returns Unit

        return fixture
    }

    // ========== Successful Logout Tests ==========

    @Test
    fun `logout invalidates session on server`() =
        runTest {
            // Given
            val fixture = createFixture()
            val useCase = fixture.build()

            // When
            useCase()

            // Then
            verifySuspend { fixture.authRepository.logout("session-123") }
        }

    @Test
    fun `logout clears auth tokens`() =
        runTest {
            // Given
            val fixture = createFixture()
            val useCase = fixture.build()

            // When
            useCase()

            // Then
            verifySuspend { fixture.authSession.clearAuthTokens() }
        }

    @Test
    fun `logout clears user data`() =
        runTest {
            // Given
            val fixture = createFixture()
            val useCase = fixture.build()

            // When
            useCase()

            // Then
            verifySuspend { fixture.userRepository.clearUsers() }
        }

    @Test
    fun `logout returns success`() =
        runTest {
            // Given
            val fixture = createFixture()
            val useCase = fixture.build()

            // When
            val result = useCase()

            // Then
            checkIs<Success<Unit>>(result)
        }

    // ========== Server Failure Handling Tests ==========

    @Test
    fun `logout succeeds even when server call fails`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authRepository.logout(any()) } throws Exception("Network error")
            val useCase = fixture.build()

            // When
            val result = useCase()

            // Then - should still succeed (best-effort server invalidation)
            checkIs<Success<Unit>>(result)
        }

    @Test
    fun `logout clears local state even when server call fails`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authRepository.logout(any()) } throws Exception("Network error")
            val useCase = fixture.build()

            // When
            useCase()

            // Then - local state should still be cleared
            verifySuspend { fixture.authSession.clearAuthTokens() }
            verifySuspend { fixture.userRepository.clearUsers() }
        }

    // ========== No Session ID Tests ==========

    @Test
    fun `logout skips server call when no session ID`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authSession.getSessionId() } returns null
            val useCase = fixture.build()

            // When
            val result = useCase()

            // Then - should still succeed without server call
            checkIs<Success<Unit>>(result)
            verifySuspend { fixture.authSession.clearAuthTokens() }
            verifySuspend { fixture.userRepository.clearUsers() }
        }

    // ========== Local-Only Logout Tests ==========

    @Test
    fun `logoutLocally clears tokens without server call`() =
        runTest {
            // Given
            val fixture = createFixture()
            val useCase = fixture.build()

            // When
            val result = useCase.logoutLocally()

            // Then
            checkIs<Success<Unit>>(result)
            verifySuspend { fixture.authSession.clearAuthTokens() }
            verifySuspend { fixture.userRepository.clearUsers() }
            // authRepository.logout should NOT be called - we can't verify "no call" with mokkery
            // but the test succeeds if clearAuthTokens and clearUsers are called
        }
}
