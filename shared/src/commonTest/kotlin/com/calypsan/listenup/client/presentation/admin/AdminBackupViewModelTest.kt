package com.calypsan.listenup.client.presentation.admin

import com.calypsan.listenup.client.data.remote.BackupApiContract
import com.calypsan.listenup.client.data.remote.model.BackupResponse
import com.calypsan.listenup.client.data.remote.model.ValidationResponse
import com.calypsan.listenup.client.domain.model.BackupInfo
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentiallyReturns
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
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for AdminBackupViewModel.
 *
 * Covers:
 * - Loading backup list
 * - Creating backups with different options
 * - Deleting backups with confirmation
 * - Validating backups
 * - Error handling for all operations
 * - State transitions
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminBackupViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private fun createBackupResponse(
        id: String = "backup-1",
        path: String = "/backups/backup-1.listenup.zip",
        size: Long = 1024 * 1024, // 1 MB
        createdAt: String = "2024-01-15T10:30:00Z",
        checksum: String? = "abc123",
    ) = BackupResponse(
        id = id,
        path = path,
        size = size,
        createdAt = createdAt,
        checksum = checksum,
    )

    private fun createValidationResponse(
        valid: Boolean = true,
        version: String = "1.0",
        serverName: String = "Test Server",
        expectedCounts: Map<String, Int> =
            mapOf(
                "users" to 5,
                "books" to 100,
                "contributors" to 50,
            ),
        errors: List<String> = emptyList(),
        warnings: List<String> = emptyList(),
    ) = ValidationResponse(
        valid = valid,
        version = version,
        serverName = serverName,
        expectedCounts = expectedCounts,
        errors = errors,
        warnings = warnings,
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Initial State ==========

    @Test
    fun `initial state is loading`() =
        runTest {
            val api: BackupApiContract = mock()
            everySuspend { api.listBackups() } returns emptyList()

            val viewModel = AdminBackupViewModel(api)

            assertTrue(viewModel.state.value.isLoading)
            assertTrue(
                viewModel.state.value.backups
                    .isEmpty(),
            )
            assertNull(viewModel.state.value.error)
        }

    // ========== Load Backups ==========

    @Test
    fun `loadBackups fetches and displays backups sorted by date`() =
        runTest {
            val api: BackupApiContract = mock()
            val backups =
                listOf(
                    createBackupResponse(id = "older", createdAt = "2024-01-01T00:00:00Z"),
                    createBackupResponse(id = "newer", createdAt = "2024-01-15T00:00:00Z"),
                    createBackupResponse(id = "middle", createdAt = "2024-01-10T00:00:00Z"),
                )
            everySuspend { api.listBackups() } returns backups

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(3, viewModel.state.value.backups.size)
            // Should be sorted newest first
            assertEquals(
                "newer",
                viewModel.state.value.backups[0]
                    .id,
            )
            assertEquals(
                "middle",
                viewModel.state.value.backups[1]
                    .id,
            )
            assertEquals(
                "older",
                viewModel.state.value.backups[2]
                    .id,
            )
        }

    @Test
    fun `loadBackups handles empty list`() =
        runTest {
            val api: BackupApiContract = mock()
            everySuspend { api.listBackups() } returns emptyList()

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertTrue(
                viewModel.state.value.backups
                    .isEmpty(),
            )
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `loadBackups handles network error`() =
        runTest {
            val api: BackupApiContract = mock()
            everySuspend { api.listBackups() } throws RuntimeException("Network error")

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertNotNull(viewModel.state.value.error)
            assertTrue(
                viewModel.state.value.error!!
                    .contains("Network error"),
            )
        }

    @Test
    fun `loadBackups handles invalid date format gracefully`() =
        runTest {
            val api: BackupApiContract = mock()
            val backups =
                listOf(
                    createBackupResponse(id = "valid", createdAt = "2024-01-15T00:00:00Z"),
                    createBackupResponse(id = "invalid", createdAt = "not-a-date"),
                )
            everySuspend { api.listBackups() } returns backups

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            // Should not crash, invalid date should use DISTANT_PAST
            assertEquals(2, viewModel.state.value.backups.size)
            assertEquals(
                "valid",
                viewModel.state.value.backups[0]
                    .id,
            )
        }

    // ========== Create Backup ==========

    @Test
    fun `createBackup with default options`() =
        runTest {
            val api: BackupApiContract = mock()
            everySuspend { api.listBackups() } returns emptyList()
            everySuspend { api.createBackup(includeImages = false, includeEvents = true) } returns
                createBackupResponse(id = "new-backup")

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            viewModel.createBackup(includeImages = false, includeEvents = true)
            advanceUntilIdle()

            // After completion, isCreating should be false
            assertFalse(viewModel.state.value.isCreating)
            verifySuspend { api.createBackup(includeImages = false, includeEvents = true) }
        }

    @Test
    fun `createBackup with images included`() =
        runTest {
            val api: BackupApiContract = mock()
            everySuspend { api.listBackups() } returns emptyList()
            everySuspend { api.createBackup(includeImages = true, includeEvents = true) } returns
                createBackupResponse()

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            viewModel.createBackup(includeImages = true, includeEvents = true)
            advanceUntilIdle()

            verifySuspend { api.createBackup(includeImages = true, includeEvents = true) }
        }

    @Test
    fun `createBackup without events`() =
        runTest {
            val api: BackupApiContract = mock()
            everySuspend { api.listBackups() } returns emptyList()
            everySuspend { api.createBackup(includeImages = false, includeEvents = false) } returns
                createBackupResponse()

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            viewModel.createBackup(includeImages = false, includeEvents = false)
            advanceUntilIdle()

            verifySuspend { api.createBackup(includeImages = false, includeEvents = false) }
        }

    @Test
    fun `createBackup reloads list on success`() =
        runTest {
            val api: BackupApiContract = mock()
            // Use sequentiallyReturns for multiple calls
            everySuspend { api.listBackups() } sequentiallyReturns
                listOf(
                    listOf(createBackupResponse(id = "initial-backup")),
                    listOf(createBackupResponse(id = "reloaded-backup")),
                )
            everySuspend { api.createBackup(includeImages = false, includeEvents = true) } returns
                createBackupResponse(id = "new-backup")

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            assertEquals(
                "initial-backup",
                viewModel.state.value.backups[0]
                    .id,
            )

            viewModel.createBackup(includeImages = false, includeEvents = true)
            advanceUntilIdle()

            // List should have been reloaded (now shows reloaded-backup)
            assertEquals(
                "reloaded-backup",
                viewModel.state.value.backups[0]
                    .id,
            )
            // Verify listBackups was called twice
            verifySuspend(VerifyMode.exactly(2)) { api.listBackups() }
        }

    @Test
    fun `createBackup handles error`() =
        runTest {
            val api: BackupApiContract = mock()
            everySuspend { api.listBackups() } returns emptyList()
            everySuspend { api.createBackup(includeImages = false, includeEvents = true) } throws
                RuntimeException("Disk full")

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            viewModel.createBackup(includeImages = false, includeEvents = true)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isCreating)
            assertNotNull(viewModel.state.value.error)
            assertTrue(
                viewModel.state.value.error!!
                    .contains("Disk full"),
            )
        }

    // ========== Delete Backup ==========

    @Test
    fun `showDeleteConfirmation sets deleteConfirmBackup`() =
        runTest {
            val api: BackupApiContract = mock()
            val backup = createBackupResponse(id = "to-delete")
            everySuspend { api.listBackups() } returns listOf(backup)

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            val backupInfo = viewModel.state.value.backups[0]
            viewModel.showDeleteConfirmation(backupInfo)

            assertEquals(
                backupInfo.id,
                viewModel.state.value.deleteConfirmBackup
                    ?.id,
            )
        }

    @Test
    fun `dismissDeleteConfirmation clears deleteConfirmBackup`() =
        runTest {
            val api: BackupApiContract = mock()
            val backup = createBackupResponse()
            everySuspend { api.listBackups() } returns listOf(backup)

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            viewModel.showDeleteConfirmation(viewModel.state.value.backups[0])
            assertNotNull(viewModel.state.value.deleteConfirmBackup)

            viewModel.dismissDeleteConfirmation()
            assertNull(viewModel.state.value.deleteConfirmBackup)
        }

    @Test
    fun `deleteBackup removes backup from list`() =
        runTest {
            val api: BackupApiContract = mock()
            val backups =
                listOf(
                    createBackupResponse(id = "backup-1"),
                    createBackupResponse(id = "backup-2"),
                )
            everySuspend { api.listBackups() } returns backups
            everySuspend { api.deleteBackup("backup-1") } returns Unit

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.backups.size)

            val toDelete =
                viewModel.state.value.backups
                    .first { it.id == "backup-1" }
            viewModel.deleteBackup(toDelete)
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.backups.size)
            assertEquals(
                "backup-2",
                viewModel.state.value.backups[0]
                    .id,
            )
            assertFalse(viewModel.state.value.isDeleting)
        }

    @Test
    fun `deleteBackup clears confirmation dialog`() =
        runTest {
            val api: BackupApiContract = mock()
            val backup = createBackupResponse()
            everySuspend { api.listBackups() } returns listOf(backup)
            everySuspend { api.deleteBackup(backup.id) } returns Unit

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            val backupInfo = viewModel.state.value.backups[0]
            viewModel.showDeleteConfirmation(backupInfo)
            assertNotNull(viewModel.state.value.deleteConfirmBackup)

            viewModel.deleteBackup(backupInfo)
            advanceUntilIdle()

            assertNull(viewModel.state.value.deleteConfirmBackup)
        }

    @Test
    fun `deleteBackup handles error`() =
        runTest {
            val api: BackupApiContract = mock()
            val backup = createBackupResponse()
            everySuspend { api.listBackups() } returns listOf(backup)
            everySuspend { api.deleteBackup(backup.id) } throws RuntimeException("File in use")

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            viewModel.deleteBackup(viewModel.state.value.backups[0])
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isDeleting)
            assertNotNull(viewModel.state.value.error)
            assertTrue(
                viewModel.state.value.error!!
                    .contains("File in use"),
            )
            // Backup should still be in list
            assertEquals(1, viewModel.state.value.backups.size)
        }

    // ========== Validate Backup ==========

    @Test
    fun `validateBackup clears validatingBackupId after completion`() =
        runTest {
            val api: BackupApiContract = mock()
            val backup = createBackupResponse(id = "backup-1")
            everySuspend { api.listBackups() } returns listOf(backup)
            everySuspend { api.validateBackup("backup-1") } returns createValidationResponse()

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            viewModel.validateBackup(viewModel.state.value.backups[0])
            advanceUntilIdle()

            // After completion, validatingBackupId should be cleared
            assertNull(viewModel.state.value.validatingBackupId)
            // And we should have a validation result
            assertNotNull(viewModel.state.value.validationResult)
        }

    @Test
    fun `validateBackup stores validation result`() =
        runTest {
            val api: BackupApiContract = mock()
            val backup = createBackupResponse()
            val validation =
                createValidationResponse(
                    valid = true,
                    version = "1.0",
                    serverName = "My Server",
                    expectedCounts = mapOf("books" to 42),
                )
            everySuspend { api.listBackups() } returns listOf(backup)
            everySuspend { api.validateBackup(backup.id) } returns validation

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            viewModel.validateBackup(viewModel.state.value.backups[0])
            advanceUntilIdle()

            val result = viewModel.state.value.validationResult
            assertNotNull(result)
            assertTrue(result.valid)
            assertEquals("1.0", result.version)
            assertEquals("My Server", result.serverName)
            assertEquals(42, result.entityCounts["books"])
        }

    @Test
    fun `validateBackup handles invalid backup`() =
        runTest {
            val api: BackupApiContract = mock()
            val backup = createBackupResponse()
            val validation =
                createValidationResponse(
                    valid = false,
                    errors = listOf("Missing manifest.json", "Unsupported version"),
                )
            everySuspend { api.listBackups() } returns listOf(backup)
            everySuspend { api.validateBackup(backup.id) } returns validation

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            viewModel.validateBackup(viewModel.state.value.backups[0])
            advanceUntilIdle()

            val result = viewModel.state.value.validationResult
            assertNotNull(result)
            assertFalse(result.valid)
            assertEquals(2, result.errors.size)
            assertTrue(result.errors.contains("Missing manifest.json"))
        }

    @Test
    fun `validateBackup handles validation with warnings`() =
        runTest {
            val api: BackupApiContract = mock()
            val backup = createBackupResponse()
            val validation =
                createValidationResponse(
                    valid = true,
                    warnings = listOf("Some images may be missing"),
                )
            everySuspend { api.listBackups() } returns listOf(backup)
            everySuspend { api.validateBackup(backup.id) } returns validation

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            viewModel.validateBackup(viewModel.state.value.backups[0])
            advanceUntilIdle()

            val result = viewModel.state.value.validationResult
            assertNotNull(result)
            assertTrue(result.valid)
            assertEquals(1, result.warnings.size)
        }

    @Test
    fun `validateBackup handles network error`() =
        runTest {
            val api: BackupApiContract = mock()
            val backup = createBackupResponse()
            everySuspend { api.listBackups() } returns listOf(backup)
            everySuspend { api.validateBackup(backup.id) } throws RuntimeException("Connection timeout")

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            viewModel.validateBackup(viewModel.state.value.backups[0])
            advanceUntilIdle()

            assertNull(viewModel.state.value.validatingBackupId)
            assertNull(viewModel.state.value.validationResult)
            assertNotNull(viewModel.state.value.error)
            assertTrue(
                viewModel.state.value.error!!
                    .contains("Connection timeout"),
            )
        }

    @Test
    fun `dismissValidation clears validation result`() =
        runTest {
            val api: BackupApiContract = mock()
            val backup = createBackupResponse()
            everySuspend { api.listBackups() } returns listOf(backup)
            everySuspend { api.validateBackup(backup.id) } returns createValidationResponse()

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            viewModel.validateBackup(viewModel.state.value.backups[0])
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.validationResult)

            viewModel.dismissValidation()
            assertNull(viewModel.state.value.validationResult)
        }

    // ========== Error Handling ==========

    @Test
    fun `clearError clears error state`() =
        runTest {
            val api: BackupApiContract = mock()
            everySuspend { api.listBackups() } throws RuntimeException("Error")

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.error)

            viewModel.clearError()
            assertNull(viewModel.state.value.error)
        }

    // ========== Edge Cases ==========

    @Test
    fun `handles backup with null checksum`() =
        runTest {
            val api: BackupApiContract = mock()
            val backup = createBackupResponse(checksum = null)
            everySuspend { api.listBackups() } returns listOf(backup)

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.backups.size)
            assertNull(
                viewModel.state.value.backups[0]
                    .checksum,
            )
        }

    @Test
    fun `handles large backup size formatting`() =
        runTest {
            val api: BackupApiContract = mock()
            val backup = createBackupResponse(size = 2L * 1024 * 1024 * 1024) // 2 GB
            everySuspend { api.listBackups() } returns listOf(backup)

            val viewModel = AdminBackupViewModel(api)
            advanceUntilIdle()

            val backupInfo = viewModel.state.value.backups[0]
            assertTrue(backupInfo.sizeFormatted.contains("GB"))
        }
}
