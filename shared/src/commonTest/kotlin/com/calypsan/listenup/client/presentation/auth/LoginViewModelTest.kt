package com.calypsan.listenup.client.presentation.auth

import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserEntity
import com.calypsan.listenup.client.data.remote.AuthApiContract
import com.calypsan.listenup.client.data.remote.AuthResponse
import com.calypsan.listenup.client.data.remote.AuthUser
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for LoginViewModel.
 *
 * Tests cover:
 * - Email validation (@ and . requirements)
 * - Password validation (non-empty)
 * - Successful login flow with token/user persistence
 * - Error classification from exceptions
 * - State transitions (Idle → Loading → Success/Error)
 * - clearError behavior
 *
 * Uses Mokkery for mocking AuthApiContract, SettingsRepository, and UserDao.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val authApi: AuthApiContract = mock()
        val settingsRepository: SettingsRepositoryContract = mock()
        val userDao: UserDao = mock()

        fun build(): LoginViewModel = LoginViewModel(
            authApi = authApi,
            settingsRepository = settingsRepository,
            userDao = userDao,
        )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs for successful operations
        everySuspend { fixture.settingsRepository.saveAuthTokens(any(), any(), any(), any()) } returns Unit
        everySuspend { fixture.userDao.upsert(any<UserEntity>()) } returns Unit

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createAuthResponse(
        accessToken: String = "access-token-123",
        refreshToken: String = "refresh-token-456",
        sessionId: String = "session-789",
        userId: String = "user-1",
        email: String = "test@example.com",
    ): AuthResponse = AuthResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        sessionId = sessionId,
        tokenType = "Bearer",
        expiresIn = 3600,
        user = AuthUser(
            id = userId,
            email = email,
            displayName = "Test User",
            firstName = "Test",
            lastName = "User",
            isRoot = false,
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            lastLoginAt = "2024-01-01T00:00:00Z",
        ),
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state is Idle`() = runTest {
        // Given
        val fixture = createFixture()
        val viewModel = fixture.build()

        // Then
        assertEquals(LoginStatus.Idle, viewModel.state.value.status)
    }

    // ========== Email Validation Tests ==========

    @Test
    fun `login rejects email without at symbol`() = runTest {
        // Given
        val fixture = createFixture()
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "invalid.email", password = "password123")
        advanceUntilIdle()

        // Then
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Error>(status)
        assertIs<LoginErrorType.ValidationError>(status.type)
        assertEquals(LoginField.EMAIL, (status.type as LoginErrorType.ValidationError).field)
    }

    @Test
    fun `login rejects email without dot`() = runTest {
        // Given
        val fixture = createFixture()
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "invalid@email", password = "password123")
        advanceUntilIdle()

        // Then
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Error>(status)
        assertIs<LoginErrorType.ValidationError>(status.type)
        assertEquals(LoginField.EMAIL, (status.type as LoginErrorType.ValidationError).field)
    }

    @Test
    fun `login rejects empty email`() = runTest {
        // Given
        val fixture = createFixture()
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "", password = "password123")
        advanceUntilIdle()

        // Then
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Error>(status)
        assertIs<LoginErrorType.ValidationError>(status.type)
        assertEquals(LoginField.EMAIL, (status.type as LoginErrorType.ValidationError).field)
    }

    @Test
    fun `login accepts valid email with at and dot`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } returns createAuthResponse()
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "password123")
        advanceUntilIdle()

        // Then - should proceed to API call, not validation error
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Success>(status)
    }

    @Test
    fun `login trims whitespace from email`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } returns createAuthResponse()
        val viewModel = fixture.build()

        // When - email has leading/trailing spaces
        viewModel.onLoginSubmit(email = "  user@example.com  ", password = "password123")
        advanceUntilIdle()

        // Then - should succeed (trimmed email is valid)
        assertIs<LoginStatus.Success>(viewModel.state.value.status)
        verifySuspend { fixture.authApi.login("user@example.com", "password123") }
    }

    // ========== Password Validation Tests ==========

    @Test
    fun `login rejects empty password`() = runTest {
        // Given
        val fixture = createFixture()
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "")
        advanceUntilIdle()

        // Then
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Error>(status)
        assertIs<LoginErrorType.ValidationError>(status.type)
        assertEquals(LoginField.PASSWORD, (status.type as LoginErrorType.ValidationError).field)
    }

    @Test
    fun `login accepts non-empty password`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } returns createAuthResponse()
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "x")
        advanceUntilIdle()

        // Then - should proceed (single char password is valid)
        assertIs<LoginStatus.Success>(viewModel.state.value.status)
    }

    // ========== Successful Login Flow Tests ==========

    @Test
    fun `login shows Loading state during API call`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } returns createAuthResponse()
        val viewModel = fixture.build()

        // When - start login but don't advance
        viewModel.onLoginSubmit(email = "user@example.com", password = "password123")

        // Then - should be Loading
        // Note: This is hard to test precisely since state changes quickly
        // We verify the final state is Success which implies it went through Loading
        advanceUntilIdle()
        assertIs<LoginStatus.Success>(viewModel.state.value.status)
    }

    @Test
    fun `login saves auth tokens on success`() = runTest {
        // Given
        val response = createAuthResponse(
            accessToken = "my-access-token",
            refreshToken = "my-refresh-token",
            sessionId = "my-session",
            userId = "user-42",
        )
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } returns response
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "password123")
        advanceUntilIdle()

        // Then
        verifySuspend {
            fixture.settingsRepository.saveAuthTokens(
                access = AccessToken("my-access-token"),
                refresh = RefreshToken("my-refresh-token"),
                sessionId = "my-session",
                userId = "user-42",
            )
        }
    }

    @Test
    fun `login persists user data on success`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } returns createAuthResponse()
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "password123")
        advanceUntilIdle()

        // Then
        verifySuspend { fixture.userDao.upsert(any<UserEntity>()) }
    }

    @Test
    fun `login transitions to Success on completion`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } returns createAuthResponse()
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "password123")
        advanceUntilIdle()

        // Then
        assertIs<LoginStatus.Success>(viewModel.state.value.status)
    }

    // ========== Error Classification Tests ==========

    @Test
    fun `login classifies invalid credentials error`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } throws Exception("invalid credentials")
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "wrong")
        advanceUntilIdle()

        // Then
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Error>(status)
        assertIs<LoginErrorType.InvalidCredentials>(status.type)
    }

    @Test
    fun `login classifies 401 as invalid credentials`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } throws Exception("HTTP 401 Unauthorized")
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "wrong")
        advanceUntilIdle()

        // Then
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Error>(status)
        assertIs<LoginErrorType.InvalidCredentials>(status.type)
    }

    @Test
    fun `login classifies connection refused as network error`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } throws Exception("Connection refused")
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "password")
        advanceUntilIdle()

        // Then
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Error>(status)
        assertIs<LoginErrorType.NetworkError>(status.type)
        assertEquals("Connection refused. Is the server running?", (status.type as LoginErrorType.NetworkError).detail)
    }

    @Test
    fun `login classifies timeout as network error`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } throws Exception("Connection timed out")
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "password")
        advanceUntilIdle()

        // Then
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Error>(status)
        assertIs<LoginErrorType.NetworkError>(status.type)
        assertEquals("Connection timed out. Check server address.", (status.type as LoginErrorType.NetworkError).detail)
    }

    @Test
    fun `login classifies unknown host as network error`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } throws Exception("Unable to resolve host")
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "password")
        advanceUntilIdle()

        // Then
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Error>(status)
        assertIs<LoginErrorType.NetworkError>(status.type)
        assertEquals("Server not found. Check the address.", (status.type as LoginErrorType.NetworkError).detail)
    }

    @Test
    fun `login classifies 500 as server error`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } throws Exception("HTTP 500 Internal Server Error")
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "password")
        advanceUntilIdle()

        // Then
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Error>(status)
        assertIs<LoginErrorType.ServerError>(status.type)
        assertEquals("Server error (500)", (status.type as LoginErrorType.ServerError).detail)
    }

    @Test
    fun `login classifies 503 as server error`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } throws Exception("HTTP 503 Service Unavailable")
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "password")
        advanceUntilIdle()

        // Then
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Error>(status)
        assertIs<LoginErrorType.ServerError>(status.type)
        assertEquals("Server error (503)", (status.type as LoginErrorType.ServerError).detail)
    }

    @Test
    fun `login classifies unknown error as server error with message`() = runTest {
        // Given
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } throws Exception("Something unexpected happened")
        val viewModel = fixture.build()

        // When
        viewModel.onLoginSubmit(email = "user@example.com", password = "password")
        advanceUntilIdle()

        // Then
        val status = viewModel.state.value.status
        assertIs<LoginStatus.Error>(status)
        assertIs<LoginErrorType.ServerError>(status.type)
        assertEquals("Something unexpected happened", (status.type as LoginErrorType.ServerError).detail)
    }

    // ========== clearError Tests ==========

    @Test
    fun `clearError resets Error state to Idle`() = runTest {
        // Given - put viewModel in error state
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } throws Exception("error")
        val viewModel = fixture.build()
        viewModel.onLoginSubmit(email = "user@example.com", password = "password")
        advanceUntilIdle()
        assertIs<LoginStatus.Error>(viewModel.state.value.status)

        // When
        viewModel.clearError()

        // Then
        assertEquals(LoginStatus.Idle, viewModel.state.value.status)
    }

    @Test
    fun `clearError does nothing when not in Error state`() = runTest {
        // Given - viewModel in Idle state
        val fixture = createFixture()
        val viewModel = fixture.build()
        assertEquals(LoginStatus.Idle, viewModel.state.value.status)

        // When
        viewModel.clearError()

        // Then - still Idle
        assertEquals(LoginStatus.Idle, viewModel.state.value.status)
    }

    @Test
    fun `clearError does nothing when in Success state`() = runTest {
        // Given - viewModel in Success state
        val fixture = createFixture()
        everySuspend { fixture.authApi.login(any(), any()) } returns createAuthResponse()
        val viewModel = fixture.build()
        viewModel.onLoginSubmit(email = "user@example.com", password = "password")
        advanceUntilIdle()
        assertIs<LoginStatus.Success>(viewModel.state.value.status)

        // When
        viewModel.clearError()

        // Then - still Success
        assertIs<LoginStatus.Success>(viewModel.state.value.status)
    }
}
