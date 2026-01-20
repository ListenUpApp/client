package com.calypsan.listenup.client.presentation.admin

import com.calypsan.listenup.client.domain.model.AccessMode
import com.calypsan.listenup.client.domain.model.Library
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
class LibrarySettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private fun createLibrary(
        id: String = "lib-1",
        name: String = "Main Library",
        accessMode: AccessMode = AccessMode.OPEN,
        skipInbox: Boolean = false,
    ) = Library(
        id = id,
        name = name,
        ownerId = "owner-1",
        scanPaths = listOf("/path/to/audiobooks"),
        skipInbox = skipInbox,
        accessMode = accessMode,
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
    fun `initial state is loading`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            everySuspend { adminRepository.getLibrary("lib-1") } returns createLibrary()

            val viewModel =
                LibrarySettingsViewModel(
                    libraryId = "lib-1",
                    adminRepository = adminRepository,
                )

            assertTrue(viewModel.state.value.isLoading)
        }

    @Test
    fun `loadLibrary fetches library details`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            val library = createLibrary(accessMode = AccessMode.RESTRICTED, skipInbox = true)
            everySuspend { adminRepository.getLibrary("lib-1") } returns library

            val viewModel =
                LibrarySettingsViewModel(
                    libraryId = "lib-1",
                    adminRepository = adminRepository,
                )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(library, viewModel.state.value.library)
            assertEquals(AccessMode.RESTRICTED, viewModel.state.value.accessMode)
            assertTrue(viewModel.state.value.skipInbox)
        }

    @Test
    fun `loadLibrary handles error`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            everySuspend { adminRepository.getLibrary("lib-1") } throws RuntimeException("Network error")

            val viewModel =
                LibrarySettingsViewModel(
                    libraryId = "lib-1",
                    adminRepository = adminRepository,
                )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertTrue(
                viewModel.state.value.error
                    ?.contains("Network error") == true,
            )
        }

    @Test
    fun `setAccessMode updates state and saves`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            val library = createLibrary(accessMode = AccessMode.OPEN)
            val updatedLibrary = library.copy(accessMode = AccessMode.RESTRICTED)
            everySuspend { adminRepository.getLibrary("lib-1") } returns library
            everySuspend {
                adminRepository.updateLibrary(
                    libraryId = "lib-1",
                    accessMode = AccessMode.RESTRICTED,
                )
            } returns updatedLibrary

            val viewModel =
                LibrarySettingsViewModel(
                    libraryId = "lib-1",
                    adminRepository = adminRepository,
                )
            advanceUntilIdle()

            viewModel.setAccessMode(AccessMode.RESTRICTED)
            advanceUntilIdle()

            assertEquals(AccessMode.RESTRICTED, viewModel.state.value.accessMode)
            verifySuspend(VerifyMode.atLeast(1)) {
                adminRepository.updateLibrary(libraryId = "lib-1", accessMode = AccessMode.RESTRICTED)
            }
        }

    @Test
    fun `toggleSkipInbox updates state and saves`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            val library = createLibrary(skipInbox = false)
            val updatedLibrary = library.copy(skipInbox = true)
            everySuspend { adminRepository.getLibrary("lib-1") } returns library
            everySuspend {
                adminRepository.updateLibrary(
                    libraryId = "lib-1",
                    skipInbox = true,
                )
            } returns updatedLibrary

            val viewModel =
                LibrarySettingsViewModel(
                    libraryId = "lib-1",
                    adminRepository = adminRepository,
                )
            advanceUntilIdle()

            viewModel.toggleSkipInbox()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.skipInbox)
            verifySuspend(VerifyMode.atLeast(1)) {
                adminRepository.updateLibrary(libraryId = "lib-1", skipInbox = true)
            }
        }

    @Test
    fun `update failure shows error and reverts state`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            val library = createLibrary(accessMode = AccessMode.OPEN)
            everySuspend { adminRepository.getLibrary("lib-1") } returns library
            everySuspend {
                adminRepository.updateLibrary(
                    libraryId = "lib-1",
                    accessMode = AccessMode.RESTRICTED,
                )
            } throws RuntimeException("Server error")

            val viewModel =
                LibrarySettingsViewModel(
                    libraryId = "lib-1",
                    adminRepository = adminRepository,
                )
            advanceUntilIdle()

            viewModel.setAccessMode(AccessMode.RESTRICTED)
            advanceUntilIdle()

            // Should revert to original state on error
            assertEquals(AccessMode.OPEN, viewModel.state.value.accessMode)
            assertTrue(
                viewModel.state.value.error
                    ?.contains("Server error") == true,
            )
        }

    @Test
    fun `clearError clears error state`() =
        runTest {
            val adminRepository: AdminRepository = mock()
            everySuspend { adminRepository.getLibrary("lib-1") } throws RuntimeException("Error")

            val viewModel =
                LibrarySettingsViewModel(
                    libraryId = "lib-1",
                    adminRepository = adminRepository,
                )
            advanceUntilIdle()
            assertTrue(viewModel.state.value.error != null)

            viewModel.clearError()

            assertNull(viewModel.state.value.error)
        }
}
