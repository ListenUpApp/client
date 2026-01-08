package com.calypsan.listenup.client.presentation.auth

import com.calypsan.listenup.client.checkIs
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.repository.RegistrationResult
import com.calypsan.listenup.client.domain.usecase.auth.RegisterUseCase
import dev.mokkery.answering.returns
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
 * - Delegation to RegisterUseCase
 * - State transitions (Idle -> Loading -> Success/Error)
 * - Error mapping from use case failures
 * - clearError behavior
 *
 * Uses Mokkery for mocking RegisterUseCase.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val registerUseCase: RegisterUseCase = mock()

        fun build(): RegisterViewModel =
            RegisterViewModel(
                registerUseCase = registerUseCase,
            )
    }

    private fun createFixture(): TestFixture = TestFixture()

    // ========== Test Data ==========

    private fun createRegistrationResult(
        userId: String = "user-123",
        message: String = "Registration pending approval",
    ): RegistrationResult =
        RegistrationResult(
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
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns
                Failure(
                    exception = IllegalArgumentException("Invalid email format"),
                    message = "Please enter a valid email address",
                )
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
            assertEquals("Please enter a valid email address", error.message)
        }

    @Test
    fun `registration rejects whitespace-only email`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns
                Failure(
                    exception = IllegalArgumentException("Invalid email format"),
                    message = "Please enter a valid email address",
                )
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
            assertEquals("Please enter a valid email address", error.message)
        }

    // ========== Password Validation Tests ==========

    @Test
    fun `registration rejects password shorter than 8 characters`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns
                Failure(
                    exception = IllegalArgumentException("Password too short"),
                    message = "Password must be at least 8 characters",
                )
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
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns
                Failure(
                    exception = IllegalArgumentException("Password too short"),
                    message = "Password must be at least 8 characters",
                )
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
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns Success(createRegistrationResult())
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
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns
                Failure(
                    exception = IllegalArgumentException("First name is required"),
                    message = "First name is required",
                )
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
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns
                Failure(
                    exception = IllegalArgumentException("First name is required"),
                    message = "First name is required",
                )
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
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns
                Failure(
                    exception = IllegalArgumentException("Last name is required"),
                    message = "Last name is required",
                )
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
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns
                Failure(
                    exception = IllegalArgumentException("Last name is required"),
                    message = "Last name is required",
                )
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
    fun `registration calls use case with correct parameters`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns Success(createRegistrationResult())
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
                fixture.registerUseCase("user@example.com", "secretpassword", "Jane", "Smith")
            }
        }

    @Test
    fun `registration transitions to Success on completion`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns Success(createRegistrationResult())
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
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns
                Failure(
                    exception = RuntimeException("Email already exists"),
                    message = "Email already exists",
                )
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

    // ========== clearError Tests ==========

    @Test
    fun `clearError resets Error state to Idle`() =
        runTest {
            // Given - put viewModel in error state
            val fixture = createFixture()
            everySuspend { fixture.registerUseCase(any(), any(), any(), any()) } returns
                Failure(
                    exception = IllegalArgumentException("Invalid email"),
                    message = "Please enter a valid email address",
                )
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
}
