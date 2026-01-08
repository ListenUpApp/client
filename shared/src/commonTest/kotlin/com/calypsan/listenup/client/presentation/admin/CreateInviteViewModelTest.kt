package com.calypsan.listenup.client.presentation.admin

import com.calypsan.listenup.client.checkIs
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.InviteInfo
import com.calypsan.listenup.client.domain.usecase.admin.CreateInviteUseCase
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
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

@OptIn(ExperimentalCoroutinesApi::class)
class CreateInviteViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private fun createInviteInfo() =
        InviteInfo(
            id = "invite-1",
            code = "XYZ789",
            name = "New User",
            email = "new@example.com",
            role = "user",
            expiresAt = "2024-02-01T00:00:00Z",
            claimedAt = null,
            url = "https://example.com/invite/XYZ789",
            createdAt = "2024-01-01T00:00:00Z",
        )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() =
        runTest {
            val createInviteUseCase: CreateInviteUseCase = mock()
            val viewModel = CreateInviteViewModel(createInviteUseCase)

            checkIs<CreateInviteStatus.Idle>(viewModel.state.value.status)
        }

    @Test
    fun `createInvite validates empty name`() =
        runTest {
            val createInviteUseCase: CreateInviteUseCase = mock()
            everySuspend { createInviteUseCase(any(), any(), any(), any()) } returns
                Failure(
                    RuntimeException("Name is required"),
                    "Name is required",
                )
            val viewModel = CreateInviteViewModel(createInviteUseCase)

            viewModel.createInvite(name = "", email = "test@example.com", role = "user", expiresInDays = 7)
            advanceUntilIdle()

            val status = assertIs<CreateInviteStatus.Error>(viewModel.state.value.status)
            val errorType = assertIs<CreateInviteErrorType.ValidationError>(status.type)
            assertEquals(CreateInviteField.NAME, errorType.field)
        }

    @Test
    fun `createInvite validates invalid email`() =
        runTest {
            val createInviteUseCase: CreateInviteUseCase = mock()
            everySuspend { createInviteUseCase(any(), any(), any(), any()) } returns
                Failure(
                    RuntimeException("Invalid email"),
                    "Invalid email",
                )
            val viewModel = CreateInviteViewModel(createInviteUseCase)

            viewModel.createInvite(name = "Test User", email = "invalid-email", role = "user", expiresInDays = 7)
            advanceUntilIdle()

            val status = assertIs<CreateInviteStatus.Error>(viewModel.state.value.status)
            val errorType = assertIs<CreateInviteErrorType.ValidationError>(status.type)
            assertEquals(CreateInviteField.EMAIL, errorType.field)
        }

    @Test
    fun `createInvite returns Success with invite on success`() =
        runTest {
            val createInviteUseCase: CreateInviteUseCase = mock()
            val invite = createInviteInfo()
            everySuspend { createInviteUseCase(any(), any(), any(), any()) } returns Success(invite)
            val viewModel = CreateInviteViewModel(createInviteUseCase)

            viewModel.createInvite(name = "New User", email = "new@example.com", role = "user", expiresInDays = 7)
            advanceUntilIdle()

            val status = assertIs<CreateInviteStatus.Success>(viewModel.state.value.status)
            assertEquals(invite.id, status.invite.id)
        }

    @Test
    fun `createInvite handles email already exists error`() =
        runTest {
            val createInviteUseCase: CreateInviteUseCase = mock()
            everySuspend { createInviteUseCase(any(), any(), any(), any()) } returns
                Failure(
                    RuntimeException("Email already exists"),
                    "Email already exists",
                )
            val viewModel = CreateInviteViewModel(createInviteUseCase)

            viewModel.createInvite(name = "Test", email = "test@example.com", role = "user", expiresInDays = 7)
            advanceUntilIdle()

            val status = assertIs<CreateInviteStatus.Error>(viewModel.state.value.status)
            checkIs<CreateInviteErrorType.EmailInUse>(status.type)
        }

    @Test
    fun `createInvite handles network error`() =
        runTest {
            val createInviteUseCase: CreateInviteUseCase = mock()
            everySuspend { createInviteUseCase(any(), any(), any(), any()) } returns
                Failure(
                    RuntimeException("Network connection failed"),
                    "Network connection failed",
                )
            val viewModel = CreateInviteViewModel(createInviteUseCase)

            viewModel.createInvite(name = "Test", email = "test@example.com", role = "user", expiresInDays = 7)
            advanceUntilIdle()

            val status = assertIs<CreateInviteStatus.Error>(viewModel.state.value.status)
            checkIs<CreateInviteErrorType.NetworkError>(status.type)
        }

    @Test
    fun `clearError resets to Idle`() =
        runTest {
            val createInviteUseCase: CreateInviteUseCase = mock()
            everySuspend { createInviteUseCase(any(), any(), any(), any()) } returns
                Failure(
                    RuntimeException("Name is required"),
                    "Name is required",
                )
            val viewModel = CreateInviteViewModel(createInviteUseCase)

            viewModel.createInvite(name = "", email = "test@example.com", role = "user", expiresInDays = 7)
            advanceUntilIdle()
            checkIs<CreateInviteStatus.Error>(viewModel.state.value.status)

            viewModel.clearError()

            checkIs<CreateInviteStatus.Idle>(viewModel.state.value.status)
        }

    @Test
    fun `reset returns to initial state`() =
        runTest {
            val createInviteUseCase: CreateInviteUseCase = mock()
            everySuspend { createInviteUseCase(any(), any(), any(), any()) } returns Success(createInviteInfo())
            val viewModel = CreateInviteViewModel(createInviteUseCase)

            viewModel.createInvite(name = "Test", email = "test@example.com", role = "user", expiresInDays = 7)
            advanceUntilIdle()
            checkIs<CreateInviteStatus.Success>(viewModel.state.value.status)

            viewModel.reset()

            checkIs<CreateInviteStatus.Idle>(viewModel.state.value.status)
        }
}
