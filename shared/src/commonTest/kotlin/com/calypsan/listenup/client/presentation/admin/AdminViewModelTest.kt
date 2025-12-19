package com.calypsan.listenup.client.presentation.admin

import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.AdminInvite
import com.calypsan.listenup.client.data.remote.AdminUser
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private fun createUser(id: String = "user-1", email: String = "test@example.com") =
        AdminUser(
            id = id,
            email = email,
            displayName = "Test User",
            firstName = "Test",
            lastName = "User",
            isRoot = false,
            role = "user",
            invitedBy = null,
            createdAt = "2024-01-01T00:00:00Z",
            lastLoginAt = null,
        )

    private fun createInvite(id: String = "invite-1", claimedAt: String? = null) =
        AdminInvite(
            id = id,
            code = "ABC123",
            name = "Invited User",
            email = "invited@example.com",
            role = "user",
            createdBy = "admin-1",
            expiresAt = "2024-02-01T00:00:00Z",
            claimedAt = claimedAt,
            claimedBy = null,
            url = "https://example.com/invite/ABC123",
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
    fun `initial state is loading`() = runTest {
        val adminApi: AdminApiContract = mock()
        everySuspend { adminApi.getUsers() } returns emptyList()
        everySuspend { adminApi.getInvites() } returns emptyList()

        val viewModel = AdminViewModel(adminApi)

        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `loadData fetches users and invites`() = runTest {
        val adminApi: AdminApiContract = mock()
        val users = listOf(createUser("user-1"), createUser("user-2"))
        val invites = listOf(createInvite("invite-1"))
        everySuspend { adminApi.getUsers() } returns users
        everySuspend { adminApi.getInvites() } returns invites

        val viewModel = AdminViewModel(adminApi)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(2, viewModel.state.value.users.size)
        assertEquals(1, viewModel.state.value.pendingInvites.size)
    }

    @Test
    fun `loadData filters out claimed invites`() = runTest {
        val adminApi: AdminApiContract = mock()
        val invites = listOf(
            createInvite("pending", claimedAt = null),
            createInvite("claimed", claimedAt = "2024-01-15T00:00:00Z"),
        )
        everySuspend { adminApi.getUsers() } returns emptyList()
        everySuspend { adminApi.getInvites() } returns invites

        val viewModel = AdminViewModel(adminApi)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.pendingInvites.size)
        assertEquals("pending", viewModel.state.value.pendingInvites[0].id)
    }

    @Test
    fun `loadData handles user fetch error`() = runTest {
        val adminApi: AdminApiContract = mock()
        everySuspend { adminApi.getUsers() } throws RuntimeException("Network error")
        everySuspend { adminApi.getInvites() } returns emptyList()

        val viewModel = AdminViewModel(adminApi)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.error?.contains("users") == true)
    }

    @Test
    fun `deleteUser removes user from list`() = runTest {
        val adminApi: AdminApiContract = mock()
        val users = listOf(createUser("user-1"), createUser("user-2"))
        everySuspend { adminApi.getUsers() } returns users
        everySuspend { adminApi.getInvites() } returns emptyList()
        everySuspend { adminApi.deleteUser("user-1") } returns Unit

        val viewModel = AdminViewModel(adminApi)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.users.size)

        viewModel.deleteUser("user-1")
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.users.size)
        assertEquals("user-2", viewModel.state.value.users[0].id)
    }


    @Test
    fun `revokeInvite removes invite from list`() = runTest {
        val adminApi: AdminApiContract = mock()
        val invites = listOf(createInvite("invite-1"), createInvite("invite-2"))
        everySuspend { adminApi.getUsers() } returns emptyList()
        everySuspend { adminApi.getInvites() } returns invites
        everySuspend { adminApi.deleteInvite("invite-1") } returns Unit

        val viewModel = AdminViewModel(adminApi)
        advanceUntilIdle()

        viewModel.revokeInvite("invite-1")
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.pendingInvites.size)
        assertEquals("invite-2", viewModel.state.value.pendingInvites[0].id)
    }

    @Test
    fun `clearError clears error state`() = runTest {
        val adminApi: AdminApiContract = mock()
        everySuspend { adminApi.getUsers() } throws RuntimeException("Error")
        everySuspend { adminApi.getInvites() } returns emptyList()

        val viewModel = AdminViewModel(adminApi)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.error != null)

        viewModel.clearError()

        assertNull(viewModel.state.value.error)
    }
}
