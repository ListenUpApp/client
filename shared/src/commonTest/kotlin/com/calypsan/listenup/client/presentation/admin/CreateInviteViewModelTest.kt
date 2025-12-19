package com.calypsan.listenup.client.presentation.admin

import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.AdminInvite
import com.calypsan.listenup.client.data.remote.CreateInviteRequest
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
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

    private fun createInviteResponse() =
        AdminInvite(
            id = "invite-1",
            code = "XYZ789",
            name = "New User",
            email = "new@example.com",
            role = "user",
            createdBy = "admin-1",
            expiresAt = "2024-02-01T00:00:00Z",
            claimedAt = null,
            claimedBy = null,
            url = "https://example.com/invite/XYZ789",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
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
    fun `initial state is Idle`() = runTest {
        val adminApi: AdminApiContract = mock()
        val viewModel = CreateInviteViewModel(adminApi)

        assertIs<CreateInviteStatus.Idle>(viewModel.state.value.status)
    }

    @Test
    fun `createInvite validates empty name`() = runTest {
        val adminApi: AdminApiContract = mock()
        val viewModel = CreateInviteViewModel(adminApi)

        viewModel.createInvite(name = "", email = "test@example.com", role = "user", expiresInDays = 7)
        advanceUntilIdle()

        val status = viewModel.state.value.status
        assertIs<CreateInviteStatus.Error>(status)
        val errorType = status.type
        assertIs<CreateInviteErrorType.ValidationError>(errorType)
        assertEquals(CreateInviteField.NAME, errorType.field)
    }

    @Test
    fun `createInvite validates invalid email`() = runTest {
        val adminApi: AdminApiContract = mock()
        val viewModel = CreateInviteViewModel(adminApi)

        viewModel.createInvite(name = "Test User", email = "invalid-email", role = "user", expiresInDays = 7)
        advanceUntilIdle()

        val status = viewModel.state.value.status
        assertIs<CreateInviteStatus.Error>(status)
        val errorType = status.type
        assertIs<CreateInviteErrorType.ValidationError>(errorType)
        assertEquals(CreateInviteField.EMAIL, errorType.field)
    }

    @Test
    fun `createInvite trims whitespace from inputs`() = runTest {
        val adminApi: AdminApiContract = mock()
        everySuspend { adminApi.createInvite(any()) } returns createInviteResponse()
        val viewModel = CreateInviteViewModel(adminApi)

        viewModel.createInvite(name = "  Test User  ", email = "  test@example.com  ", role = "user", expiresInDays = 7)
        advanceUntilIdle()

        assertIs<CreateInviteStatus.Success>(viewModel.state.value.status)
    }


    @Test
    fun `createInvite returns Success with invite on success`() = runTest {
        val adminApi: AdminApiContract = mock()
        val invite = createInviteResponse()
        everySuspend { adminApi.createInvite(any()) } returns invite
        val viewModel = CreateInviteViewModel(adminApi)

        viewModel.createInvite(name = "New User", email = "new@example.com", role = "user", expiresInDays = 7)
        advanceUntilIdle()

        val status = viewModel.state.value.status
        assertIs<CreateInviteStatus.Success>(status)
        assertEquals(invite.id, status.invite.id)
    }

    @Test
    fun `createInvite handles email already exists error`() = runTest {
        val adminApi: AdminApiContract = mock()
        everySuspend { adminApi.createInvite(any()) } throws RuntimeException("Email already exists")
        val viewModel = CreateInviteViewModel(adminApi)

        viewModel.createInvite(name = "Test", email = "test@example.com", role = "user", expiresInDays = 7)
        advanceUntilIdle()

        val status = viewModel.state.value.status
        assertIs<CreateInviteStatus.Error>(status)
        assertIs<CreateInviteErrorType.EmailInUse>(status.type)
    }

    @Test
    fun `createInvite handles network error`() = runTest {
        val adminApi: AdminApiContract = mock()
        everySuspend { adminApi.createInvite(any()) } throws RuntimeException("Network connection failed")
        val viewModel = CreateInviteViewModel(adminApi)

        viewModel.createInvite(name = "Test", email = "test@example.com", role = "user", expiresInDays = 7)
        advanceUntilIdle()

        val status = viewModel.state.value.status
        assertIs<CreateInviteStatus.Error>(status)
        assertIs<CreateInviteErrorType.NetworkError>(status.type)
    }

    @Test
    fun `clearError resets to Idle`() = runTest {
        val adminApi: AdminApiContract = mock()
        val viewModel = CreateInviteViewModel(adminApi)

        viewModel.createInvite(name = "", email = "test@example.com", role = "user", expiresInDays = 7)
        advanceUntilIdle()
        assertIs<CreateInviteStatus.Error>(viewModel.state.value.status)

        viewModel.clearError()

        assertIs<CreateInviteStatus.Idle>(viewModel.state.value.status)
    }

    @Test
    fun `reset returns to initial state`() = runTest {
        val adminApi: AdminApiContract = mock()
        everySuspend { adminApi.createInvite(any()) } returns createInviteResponse()
        val viewModel = CreateInviteViewModel(adminApi)

        viewModel.createInvite(name = "Test", email = "test@example.com", role = "user", expiresInDays = 7)
        advanceUntilIdle()
        assertIs<CreateInviteStatus.Success>(viewModel.state.value.status)

        viewModel.reset()

        assertIs<CreateInviteStatus.Idle>(viewModel.state.value.status)
    }
}
