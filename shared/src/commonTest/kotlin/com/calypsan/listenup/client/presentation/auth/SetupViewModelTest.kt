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
 * Tests for SetupViewModel.
 *
 * Tests cover:
 * - First name validation (non-blank)
 * - Last name validation (non-blank)
 * - Email validation (@ and . requirements)
 * - Password validation (min 8 chars)
 * - Password confirmation (must match)
 * - Successful setup flow with token/user persistence
 * - Error classification from exceptions
 * - State transitions (Idle -> Loading -> Success/Error)
 * - clearError behavior
 *
 * Uses Mokkery for mocking AuthApiContract, SettingsRepository, and UserDao.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val authApi: AuthApiContract = mock()
        val settingsRepository: SettingsRepositoryContract = mock()
        val userDao: UserDao = mock()

        fun build(): SetupViewModel =
            SetupViewModel(
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
        email: String = "admin@example.com",
    ): AuthResponse =
        AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            sessionId = sessionId,
            tokenType = "Bearer",
            expiresIn = 3600,
            user =
                AuthUser(
                    id = userId,
                    email = email,
                    displayName = "Admin User",
                    firstName = "Admin",
                    lastName = "User",
                    isRoot = true,
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
    fun `initial state is Idle`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // Then
            assertEquals(SetupStatus.Idle, viewModel.state.value.status)
        }

    // ========== First Name Validation Tests ==========

    @Test
    fun `setup rejects empty first name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.ValidationError>(status.type)
            assertEquals(SetupField.FIRST_NAME, (status.type as SetupErrorType.ValidationError).field)
        }

    @Test
    fun `setup rejects blank first name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "   ",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.ValidationError>(status.type)
            assertEquals(SetupField.FIRST_NAME, (status.type as SetupErrorType.ValidationError).field)
        }

    // ========== Last Name Validation Tests ==========

    @Test
    fun `setup rejects empty last name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.ValidationError>(status.type)
            assertEquals(SetupField.LAST_NAME, (status.type as SetupErrorType.ValidationError).field)
        }

    @Test
    fun `setup rejects blank last name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "   ",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.ValidationError>(status.type)
            assertEquals(SetupField.LAST_NAME, (status.type as SetupErrorType.ValidationError).field)
        }

    // ========== Email Validation Tests ==========

    @Test
    fun `setup rejects email without at symbol`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "invalid.email",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.ValidationError>(status.type)
            assertEquals(SetupField.EMAIL, (status.type as SetupErrorType.ValidationError).field)
        }

    @Test
    fun `setup rejects email without dot`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "invalid@email",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.ValidationError>(status.type)
            assertEquals(SetupField.EMAIL, (status.type as SetupErrorType.ValidationError).field)
        }

    @Test
    fun `setup rejects empty email`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.ValidationError>(status.type)
            assertEquals(SetupField.EMAIL, (status.type as SetupErrorType.ValidationError).field)
        }

    // ========== Password Validation Tests ==========

    @Test
    fun `setup rejects password shorter than 8 characters`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "1234567",
                passwordConfirm = "1234567",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.ValidationError>(status.type)
            assertEquals(SetupField.PASSWORD, (status.type as SetupErrorType.ValidationError).field)
        }

    @Test
    fun `setup accepts password with exactly 8 characters`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.setup(any(), any(), any(), any()) } returns createAuthResponse()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "12345678",
                passwordConfirm = "12345678",
            )
            advanceUntilIdle()

            // Then - should proceed to API call, not validation error
            assertIs<SetupStatus.Success>(viewModel.state.value.status)
        }

    @Test
    fun `setup rejects empty password`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "",
                passwordConfirm = "",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.ValidationError>(status.type)
            assertEquals(SetupField.PASSWORD, (status.type as SetupErrorType.ValidationError).field)
        }

    // ========== Password Confirmation Tests ==========

    @Test
    fun `setup rejects mismatched password confirmation`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "differentpassword",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.ValidationError>(status.type)
            assertEquals(SetupField.PASSWORD_CONFIRM, (status.type as SetupErrorType.ValidationError).field)
        }

    @Test
    fun `setup accepts matching password confirmation`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.setup(any(), any(), any(), any()) } returns createAuthResponse()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            assertIs<SetupStatus.Success>(viewModel.state.value.status)
        }

    // ========== Successful Setup Flow Tests ==========

    @Test
    fun `setup saves auth tokens on success`() =
        runTest {
            // Given
            val response =
                createAuthResponse(
                    accessToken = "my-access-token",
                    refreshToken = "my-refresh-token",
                    sessionId = "my-session",
                    userId = "admin-42",
                )
            val fixture = createFixture()
            everySuspend { fixture.authApi.setup(any(), any(), any(), any()) } returns response
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            verifySuspend {
                fixture.settingsRepository.saveAuthTokens(
                    access = AccessToken("my-access-token"),
                    refresh = RefreshToken("my-refresh-token"),
                    sessionId = "my-session",
                    userId = "admin-42",
                )
            }
        }

    @Test
    fun `setup persists user data on success`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.setup(any(), any(), any(), any()) } returns createAuthResponse()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.userDao.upsert(any<UserEntity>()) }
        }

    @Test
    fun `setup transitions to Success on completion`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.setup(any(), any(), any(), any()) } returns createAuthResponse()
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            assertIs<SetupStatus.Success>(viewModel.state.value.status)
        }

    @Test
    fun `setup trims whitespace from names and email`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.setup(any(), any(), any(), any()) } returns createAuthResponse()
            val viewModel = fixture.build()

            // When - names and email have leading/trailing spaces
            viewModel.onSetupSubmit(
                firstName = "  Admin  ",
                lastName = "  User  ",
                email = "  admin@example.com  ",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then - should succeed with trimmed values
            assertIs<SetupStatus.Success>(viewModel.state.value.status)
            verifySuspend {
                fixture.authApi.setup(
                    email = "admin@example.com",
                    password = "password123",
                    firstName = "Admin",
                    lastName = "User",
                )
            }
        }

    // ========== Error Classification Tests ==========

    @Test
    fun `setup classifies already configured error`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.setup(any(), any(), any(), any()) } throws Exception("Server already configured")
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.AlreadyConfigured>(status.type)
        }

    @Test
    fun `setup classifies network error from connection message`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.setup(any(), any(), any(), any()) } throws Exception("Connection refused")
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.NetworkError>(status.type)
        }

    @Test
    fun `setup classifies network error from network message`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.setup(any(), any(), any(), any()) } throws Exception("Network unavailable")
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.NetworkError>(status.type)
        }

    @Test
    fun `setup classifies unknown error as server error`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.setup(any(), any(), any(), any()) } throws Exception("Something unexpected")
            val viewModel = fixture.build()

            // When
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()

            // Then
            val status = viewModel.state.value.status
            assertIs<SetupStatus.Error>(status)
            assertIs<SetupErrorType.ServerError>(status.type)
        }

    // ========== clearError Tests ==========

    @Test
    fun `clearError resets Error state to Idle`() =
        runTest {
            // Given - put viewModel in error state
            val fixture = createFixture()
            val viewModel = fixture.build()
            viewModel.onSetupSubmit(
                firstName = "",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()
            assertIs<SetupStatus.Error>(viewModel.state.value.status)

            // When
            viewModel.clearError()

            // Then
            assertEquals(SetupStatus.Idle, viewModel.state.value.status)
        }

    @Test
    fun `clearError does nothing when not in Error state`() =
        runTest {
            // Given - viewModel in Idle state
            val fixture = createFixture()
            val viewModel = fixture.build()
            assertEquals(SetupStatus.Idle, viewModel.state.value.status)

            // When
            viewModel.clearError()

            // Then - still Idle
            assertEquals(SetupStatus.Idle, viewModel.state.value.status)
        }

    @Test
    fun `clearError does nothing when in Success state`() =
        runTest {
            // Given - viewModel in Success state
            val fixture = createFixture()
            everySuspend { fixture.authApi.setup(any(), any(), any(), any()) } returns createAuthResponse()
            val viewModel = fixture.build()
            viewModel.onSetupSubmit(
                firstName = "Admin",
                lastName = "User",
                email = "admin@example.com",
                password = "password123",
                passwordConfirm = "password123",
            )
            advanceUntilIdle()
            assertIs<SetupStatus.Success>(viewModel.state.value.status)

            // When
            viewModel.clearError()

            // Then - still Success
            assertIs<SetupStatus.Success>(viewModel.state.value.status)
        }
}
