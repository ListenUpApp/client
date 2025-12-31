package com.calypsan.listenup.client.presentation.invite

import com.calypsan.listenup.client.checkIs
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.remote.AuthResponse
import com.calypsan.listenup.client.data.remote.AuthUser
import com.calypsan.listenup.client.data.remote.InviteApiContract
import com.calypsan.listenup.client.data.remote.InviteDetails
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
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class InviteRegistrationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val serverUrl = "http://localhost:8080"
    private val inviteCode = "abc123"

    private fun createInviteDetails(valid: Boolean = true) =
        InviteDetails(
            name = "Invited User",
            email = "invited@example.com",
            serverName = "Test Server",
            invitedBy = "Admin User",
            valid = valid,
        )

    private fun createAuthResponse() =
        AuthResponse(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            sessionId = "session-id",
            tokenType = "Bearer",
            expiresIn = 3600,
            user =
                AuthUser(
                    id = "user-id",
                    email = "invited@example.com",
                    displayName = "Invited User",
                    firstName = "Invited",
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

    @Test
    fun `initial state is Loading`() =
        runTest {
            val inviteApi: InviteApiContract = mock()
            val settingsRepository: SettingsRepositoryContract = mock()
            val userDao: UserDao = mock()
            everySuspend { inviteApi.getInviteDetails(any(), any()) } returns createInviteDetails()

            val viewModel = InviteRegistrationViewModel(inviteApi, settingsRepository, userDao, serverUrl, inviteCode)

            checkIs<InviteLoadingState.Loading>(viewModel.state.value.loadingState)
        }

    @Test
    fun `loadInviteDetails shows Loaded state on success`() =
        runTest {
            val inviteApi: InviteApiContract = mock()
            val settingsRepository: SettingsRepositoryContract = mock()
            val userDao: UserDao = mock()
            everySuspend { inviteApi.getInviteDetails(serverUrl, inviteCode) } returns createInviteDetails()

            val viewModel = InviteRegistrationViewModel(inviteApi, settingsRepository, userDao, serverUrl, inviteCode)
            advanceUntilIdle()

            checkIs<InviteLoadingState.Loaded>(viewModel.state.value.loadingState)
        }

    @Test
    fun `loadInviteDetails shows Invalid for invalid invite`() =
        runTest {
            val inviteApi: InviteApiContract = mock()
            val settingsRepository: SettingsRepositoryContract = mock()
            val userDao: UserDao = mock()
            everySuspend { inviteApi.getInviteDetails(serverUrl, inviteCode) } returns createInviteDetails(valid = false)

            val viewModel = InviteRegistrationViewModel(inviteApi, settingsRepository, userDao, serverUrl, inviteCode)
            advanceUntilIdle()

            checkIs<InviteLoadingState.Invalid>(viewModel.state.value.loadingState)
        }

    @Test
    fun `loadInviteDetails shows Error on network failure`() =
        runTest {
            val inviteApi: InviteApiContract = mock()
            val settingsRepository: SettingsRepositoryContract = mock()
            val userDao: UserDao = mock()
            everySuspend { inviteApi.getInviteDetails(any(), any()) } throws RuntimeException("Network error")

            val viewModel = InviteRegistrationViewModel(inviteApi, settingsRepository, userDao, serverUrl, inviteCode)
            advanceUntilIdle()

            checkIs<InviteLoadingState.Error>(viewModel.state.value.loadingState)
        }

    @Test
    fun `submitRegistration validates password length`() =
        runTest {
            val inviteApi: InviteApiContract = mock()
            val settingsRepository: SettingsRepositoryContract = mock()
            val userDao: UserDao = mock()
            everySuspend { inviteApi.getInviteDetails(any(), any()) } returns createInviteDetails()

            val viewModel = InviteRegistrationViewModel(inviteApi, settingsRepository, userDao, serverUrl, inviteCode)
            advanceUntilIdle()

            viewModel.submitRegistration("short", "short")
            advanceUntilIdle()

            val status = viewModel.state.value.submissionStatus
            val error = assertIs<InviteSubmissionStatus.Error>(status)
            checkIs<InviteErrorType.ValidationError>(error.type)
        }

    @Test
    fun `submitRegistration validates password match`() =
        runTest {
            val inviteApi: InviteApiContract = mock()
            val settingsRepository: SettingsRepositoryContract = mock()
            val userDao: UserDao = mock()
            everySuspend { inviteApi.getInviteDetails(any(), any()) } returns createInviteDetails()

            val viewModel = InviteRegistrationViewModel(inviteApi, settingsRepository, userDao, serverUrl, inviteCode)
            advanceUntilIdle()

            viewModel.submitRegistration("password123", "different123")
            advanceUntilIdle()

            val status = viewModel.state.value.submissionStatus
            val error = assertIs<InviteSubmissionStatus.Error>(status)
            checkIs<InviteErrorType.PasswordMismatch>(error.type)
        }

    @Test
    fun `submitRegistration stores tokens on success`() =
        runTest {
            val inviteApi: InviteApiContract = mock()
            val settingsRepository: SettingsRepositoryContract = mock()
            val userDao: UserDao = mock()
            everySuspend { inviteApi.getInviteDetails(any(), any()) } returns createInviteDetails()
            everySuspend { inviteApi.claimInvite(serverUrl, inviteCode, "password123") } returns createAuthResponse()
            everySuspend { settingsRepository.setServerUrl(any()) } returns Unit
            everySuspend { settingsRepository.saveAuthTokens(any(), any(), any(), any()) } returns Unit
            everySuspend { userDao.upsert(any()) } returns Unit

            val viewModel = InviteRegistrationViewModel(inviteApi, settingsRepository, userDao, serverUrl, inviteCode)
            advanceUntilIdle()

            viewModel.submitRegistration("password123", "password123")
            advanceUntilIdle()

            checkIs<InviteSubmissionStatus.Success>(viewModel.state.value.submissionStatus)
            verifySuspend { settingsRepository.saveAuthTokens(any(), any(), any(), any()) }
        }

    @Test
    fun `clearError resets to Idle`() =
        runTest {
            val inviteApi: InviteApiContract = mock()
            val settingsRepository: SettingsRepositoryContract = mock()
            val userDao: UserDao = mock()
            everySuspend { inviteApi.getInviteDetails(any(), any()) } returns createInviteDetails()

            val viewModel = InviteRegistrationViewModel(inviteApi, settingsRepository, userDao, serverUrl, inviteCode)
            advanceUntilIdle()

            viewModel.submitRegistration("short", "short") // Trigger error
            advanceUntilIdle()
            checkIs<InviteSubmissionStatus.Error>(viewModel.state.value.submissionStatus)

            viewModel.clearError()

            checkIs<InviteSubmissionStatus.Idle>(viewModel.state.value.submissionStatus)
        }
}
