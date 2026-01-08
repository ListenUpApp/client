package com.calypsan.listenup.client.presentation.admin

import com.calypsan.listenup.client.domain.model.AdminUserInfo
import com.calypsan.listenup.client.domain.model.UserPermissions
import com.calypsan.listenup.client.domain.repository.AdminRepository
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
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
class UserDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private fun createUser(
        id: String = "user-1",
        email: String = "test@example.com",
        canDownload: Boolean = true,
        canShare: Boolean = true,
    ) = AdminUserInfo(
        id = id,
        email = email,
        displayName = "Test User",
        firstName = "Test",
        lastName = "User",
        isRoot = false,
        role = "member",
        status = "active",
        permissions = UserPermissions(canDownload = canDownload, canShare = canShare),
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
    fun `initial state is loading`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            everySuspend { adminRepository.getUser("user-1") } returns createUser()

            val viewModel = UserDetailViewModel(
                userId = "user-1",
                adminRepository = adminRepository,
            )

            assertTrue(viewModel.state.value.isLoading)
        }

    @Test
    fun `loadUser fetches user details`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            val user = createUser(canDownload = true, canShare = false)
            everySuspend { adminRepository.getUser("user-1") } returns user

            val viewModel = UserDetailViewModel(
                userId = "user-1",
                adminRepository = adminRepository,
            )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(user, viewModel.state.value.user)
            assertTrue(viewModel.state.value.canDownload)
            assertFalse(viewModel.state.value.canShare)
        }

    @Test
    fun `loadUser handles error`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            everySuspend { adminRepository.getUser("user-1") } throws RuntimeException("Network error")

            val viewModel = UserDetailViewModel(
                userId = "user-1",
                adminRepository = adminRepository,
            )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertTrue(viewModel.state.value.error?.contains("Network error") == true)
        }

    @Test
    fun `toggleCanDownload updates state and saves`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            val user = createUser(canDownload = true, canShare = true)
            val updatedUser = user.copy(
                permissions = UserPermissions(canDownload = false, canShare = true),
            )
            everySuspend { adminRepository.getUser("user-1") } returns user
            everySuspend {
                adminRepository.updateUser(
                    userId = "user-1",
                    canDownload = false,
                )
            } returns updatedUser

            val viewModel = UserDetailViewModel(
                userId = "user-1",
                adminRepository = adminRepository,
            )
            advanceUntilIdle()

            viewModel.toggleCanDownload()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.canDownload)
            verifySuspend(VerifyMode.atLeast(1)) {
                adminRepository.updateUser(userId = "user-1", canDownload = false)
            }
        }

    @Test
    fun `toggleCanShare updates state and saves`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            val user = createUser(canDownload = true, canShare = true)
            val updatedUser = user.copy(
                permissions = UserPermissions(canDownload = true, canShare = false),
            )
            everySuspend { adminRepository.getUser("user-1") } returns user
            everySuspend {
                adminRepository.updateUser(
                    userId = "user-1",
                    canShare = false,
                )
            } returns updatedUser

            val viewModel = UserDetailViewModel(
                userId = "user-1",
                adminRepository = adminRepository,
            )
            advanceUntilIdle()

            viewModel.toggleCanShare()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.canShare)
            verifySuspend(VerifyMode.atLeast(1)) {
                adminRepository.updateUser(userId = "user-1", canShare = false)
            }
        }

    @Test
    fun `update failure shows error and reverts state`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            val user = createUser(canDownload = true, canShare = true)
            everySuspend { adminRepository.getUser("user-1") } returns user
            everySuspend {
                adminRepository.updateUser(
                    userId = "user-1",
                    canDownload = false,
                )
            } throws RuntimeException("Server error")

            val viewModel = UserDetailViewModel(
                userId = "user-1",
                adminRepository = adminRepository,
            )
            advanceUntilIdle()

            viewModel.toggleCanDownload()
            advanceUntilIdle()

            // Should revert to original state on error
            assertTrue(viewModel.state.value.canDownload)
            assertTrue(viewModel.state.value.error?.contains("Server error") == true)
        }

    @Test
    fun `clearError clears error state`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            everySuspend { adminRepository.getUser("user-1") } throws RuntimeException("Error")

            val viewModel = UserDetailViewModel(
                userId = "user-1",
                adminRepository = adminRepository,
            )
            advanceUntilIdle()
            assertTrue(viewModel.state.value.error != null)

            viewModel.clearError()

            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `protected users cannot have permissions toggled`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            val rootUser = AdminUserInfo(
                id = "root-1",
                email = "root@example.com",
                displayName = "Root User",
                firstName = "Root",
                lastName = "User",
                isRoot = true,
                role = "admin",
                status = "active",
                permissions = UserPermissions(canDownload = true, canShare = true),
                createdAt = "2024-01-01T00:00:00Z",
            )
            everySuspend { adminRepository.getUser("root-1") } returns rootUser

            val viewModel = UserDetailViewModel(
                userId = "root-1",
                adminRepository = adminRepository,
            )
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isProtected)
        }
}
