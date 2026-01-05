package com.calypsan.listenup.client.presentation.auth

import com.calypsan.listenup.client.checkIs
import com.calypsan.listenup.client.data.remote.AuthApiContract
import com.calypsan.listenup.client.data.remote.RegisterResponse
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
 * Tests for RegisterViewModel.
 *
 * Tests cover:
 * - Initial state
 * - Email validation (blank check)
 * - Password validation (minimum 8 characters)
 * - First name validation (blank check)
 * - Last name validation (blank check)
 * - Successful registration flow
 * - Error handling from API exceptions
 * - clearError behavior
 *
 * Uses Mokkery for mocking AuthApiContract and SettingsRepository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val authApi: AuthApiContract = mock()
        val settingsRepository: SettingsRepositoryContract = mock()

        fun build(): RegisterViewModel =
            RegisterViewModel(
                authApi = authApi,
                settingsRepository = settingsRepository,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs for successful operations
        everySuspend { fixture.settingsRepository.savePendingRegistration(any(), any(), any()) } returns Unit

        return fixture
    }

    // ========== Test Data ==========

    private fun createRegisterResponse(
        userId: String = "user-123",
        message: String = "Registration pending approval",
    ): RegisterResponse =
        RegisterResponse(
            userId = userId,
            message = message,
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
            assertEquals(RegisterStatus.Idle, viewModel.state.value.status)
        }

    // ========== Email Validation Tests ==========

    @Test
    fun `registration rejects blank email`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "",
                password = "password123",
                firstName = "John",
                lastName = "Doe",
            )
            advanceUntilIdle()

            // Then
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("Email is required", error.message)
        }

    @Test
    fun `registration rejects whitespace-only email`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "   ",
                password = "password123",
                firstName = "John",
                lastName = "Doe",
            )
            advanceUntilIdle()

            // Then
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("Email is required", error.message)
        }

    // ========== Password Validation Tests ==========

    @Test
    fun `registration rejects password shorter than 8 characters`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "test@example.com",
                password = "short",
                firstName = "John",
                lastName = "Doe",
            )
            advanceUntilIdle()

            // Then
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("Password must be at least 8 characters", error.message)
        }

    @Test
    fun `registration rejects password of exactly 7 characters`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "test@example.com",
                password = "1234567",
                firstName = "John",
                lastName = "Doe",
            )
            advanceUntilIdle()

            // Then
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("Password must be at least 8 characters", error.message)
        }

    @Test
    fun `registration accepts password of exactly 8 characters`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.register(any(), any(), any(), any()) } returns createRegisterResponse()
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "test@example.com",
                password = "12345678",
                firstName = "John",
                lastName = "Doe",
            )
            advanceUntilIdle()

            // Then
            checkIs<RegisterStatus.Success>(viewModel.state.value.status)
        }

    // ========== First Name Validation Tests ==========

    @Test
    fun `registration rejects blank first name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "test@example.com",
                password = "password123",
                firstName = "",
                lastName = "Doe",
            )
            advanceUntilIdle()

            // Then
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("First name is required", error.message)
        }

    @Test
    fun `registration rejects whitespace-only first name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "test@example.com",
                password = "password123",
                firstName = "   ",
                lastName = "Doe",
            )
            advanceUntilIdle()

            // Then
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("First name is required", error.message)
        }

    // ========== Last Name Validation Tests ==========

    @Test
    fun `registration rejects blank last name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "test@example.com",
                password = "password123",
                firstName = "John",
                lastName = "",
            )
            advanceUntilIdle()

            // Then
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("Last name is required", error.message)
        }

    @Test
    fun `registration rejects whitespace-only last name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "test@example.com",
                password = "password123",
                firstName = "John",
                lastName = "   ",
            )
            advanceUntilIdle()

            // Then
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("Last name is required", error.message)
        }

    // ========== Successful Registration Tests ==========

    @Test
    fun `registration calls API with correct parameters`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.register(any(), any(), any(), any()) } returns createRegisterResponse()
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "user@example.com",
                password = "secretpassword",
                firstName = "Jane",
                lastName = "Smith",
            )
            advanceUntilIdle()

            // Then
            verifySuspend {
                fixture.authApi.register("user@example.com", "secretpassword", "Jane", "Smith")
            }
        }

    @Test
    fun `registration saves pending registration on success`() =
        runTest {
            // Given
            val response = createRegisterResponse(userId = "new-user-456")
            val fixture = createFixture()
            everySuspend { fixture.authApi.register(any(), any(), any(), any()) } returns response
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "user@example.com",
                password = "secretpassword",
                firstName = "Jane",
                lastName = "Smith",
            )
            advanceUntilIdle()

            // Then
            verifySuspend {
                fixture.settingsRepository.savePendingRegistration(
                    userId = "new-user-456",
                    email = "user@example.com",
                    password = "secretpassword",
                )
            }
        }

    @Test
    fun `registration transitions to Success on completion`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.register(any(), any(), any(), any()) } returns createRegisterResponse()
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "user@example.com",
                password = "password123",
                firstName = "John",
                lastName = "Doe",
            )
            advanceUntilIdle()

            // Then
            checkIs<RegisterStatus.Success>(viewModel.state.value.status)
        }

    // ========== Error Handling Tests ==========

    @Test
    fun `registration shows error on API failure`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.register(any(), any(), any(), any()) } throws RuntimeException("Email already exists")
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "user@example.com",
                password = "password123",
                firstName = "John",
                lastName = "Doe",
            )
            advanceUntilIdle()

            // Then
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("Email already exists", error.message)
        }

    @Test
    fun `registration shows default message on API failure with null message`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.authApi.register(any(), any(), any(), any()) } throws RuntimeException()
            val viewModel = fixture.build()

            // When
            viewModel.onRegisterSubmit(
                email = "user@example.com",
                password = "password123",
                firstName = "John",
                lastName = "Doe",
            )
            advanceUntilIdle()

            // Then
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("Registration failed. Please try again.", error.message)
        }

    // ========== clearError Tests ==========

    @Test
    fun `clearError resets Error state to Idle`() =
        runTest {
            // Given - put viewModel in error state
            val fixture = createFixture()
            val viewModel = fixture.build()
            viewModel.onRegisterSubmit(
                email = "",
                password = "password123",
                firstName = "John",
                lastName = "Doe",
            )
            advanceUntilIdle()
            checkIs<RegisterStatus.Error>(viewModel.state.value.status)

            // When
            viewModel.clearError()

            // Then
            assertEquals(RegisterStatus.Idle, viewModel.state.value.status)
        }

    @Test
    fun `clearError can be called from any state`() =
        runTest {
            // Given - viewModel in Idle state
            val fixture = createFixture()
            val viewModel = fixture.build()
            assertEquals(RegisterStatus.Idle, viewModel.state.value.status)

            // When
            viewModel.clearError()

            // Then - still Idle (no crash)
            assertEquals(RegisterStatus.Idle, viewModel.state.value.status)
        }

    // ========== Validation Order Tests ==========

    @Test
    fun `validation checks email before password`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When - both email and password invalid
            viewModel.onRegisterSubmit(
                email = "",
                password = "short",
                firstName = "John",
                lastName = "Doe",
            )
            advanceUntilIdle()

            // Then - email error shows first
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("Email is required", error.message)
        }

    @Test
    fun `validation checks password before first name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When - password and first name invalid
            viewModel.onRegisterSubmit(
                email = "test@example.com",
                password = "short",
                firstName = "",
                lastName = "Doe",
            )
            advanceUntilIdle()

            // Then - password error shows first
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("Password must be at least 8 characters", error.message)
        }

    @Test
    fun `validation checks first name before last name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When - first name and last name invalid
            viewModel.onRegisterSubmit(
                email = "test@example.com",
                password = "password123",
                firstName = "",
                lastName = "",
            )
            advanceUntilIdle()

            // Then - first name error shows first
            val error = assertIs<RegisterStatus.Error>(viewModel.state.value.status)
            assertEquals("First name is required", error.message)
        }
}
