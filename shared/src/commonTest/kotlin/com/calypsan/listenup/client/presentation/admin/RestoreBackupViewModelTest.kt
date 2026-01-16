package com.calypsan.listenup.client.presentation.admin

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.remote.BackupApiContract
import com.calypsan.listenup.client.data.remote.model.RestoreError
import com.calypsan.listenup.client.data.remote.model.RestoreRequest
import com.calypsan.listenup.client.data.remote.model.RestoreResponse
import com.calypsan.listenup.client.data.remote.model.ValidationResponse
import com.calypsan.listenup.client.domain.repository.SyncRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for RestoreBackupViewModel.
 *
 * Covers:
 * - Wizard flow navigation (forward/back)
 * - Mode selection (Fresh vs Merge)
 * - Merge strategy selection
 * - Dry run execution
 * - Actual restore execution
 * - Error handling at each step
 * - State transitions
 * - Edge cases (partial failures, cancellation, etc.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestoreBackupViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private fun createMockSyncRepository(): SyncRepository {
        val syncRepo: SyncRepository = mock()
        everySuspend { syncRepo.forceFullResync() } returns Result.Success(Unit)
        everySuspend { syncRepo.refreshListeningHistory() } returns Result.Success(Unit)
        return syncRepo
    }

    private fun createValidationResponse(
        valid: Boolean = true,
        version: String = "1.0",
        serverName: String = "Test Server",
        expectedCounts: Map<String, Int> = mapOf(
            "users" to 5,
            "books" to 100,
            "contributors" to 50,
            "series" to 20,
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

    private fun createRestoreResponse(
        imported: Map<String, Int> = mapOf(
            "users" to 5,
            "books" to 100,
        ),
        skipped: Map<String, Int> = emptyMap(),
        errors: List<RestoreError> = emptyList(),
        duration: String = "2.5s",
    ) = RestoreResponse(
        imported = imported,
        skipped = skipped,
        errors = errors,
        duration = duration,
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
    fun `initial state starts at MODE_SELECTION step`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup("backup-1") } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())

        assertEquals(RestoreStep.MODE_SELECTION, viewModel.state.value.step)
        assertEquals("backup-1", viewModel.state.value.backupId)
        assertNull(viewModel.state.value.mode)
        assertNull(viewModel.state.value.mergeStrategy)
    }

    @Test
    fun `init validates backup automatically`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup("backup-1") } returns createValidationResponse(
            valid = true,
            serverName = "My Server",
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        // After init completes, validation should be done
        assertFalse(viewModel.state.value.isValidating)
        assertNotNull(viewModel.state.value.validation)
        assertEquals("My Server", viewModel.state.value.validation?.serverName)
    }

    @Test
    fun `init handles validation error`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup("backup-1") } throws RuntimeException("Network error")

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isValidating)
        assertNull(viewModel.state.value.validation)
        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("Network error"))
    }

    // ========== Mode Selection ==========

    @Test
    fun `selectMode updates mode in state`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        assertEquals(RestoreMode.FRESH, viewModel.state.value.mode)

        viewModel.selectMode(RestoreMode.MERGE)
        assertEquals(RestoreMode.MERGE, viewModel.state.value.mode)
    }

    @Test
    fun `selectMode clears any previous error`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } throws RuntimeException("Error")

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        viewModel.selectMode(RestoreMode.FRESH)
        assertNull(viewModel.state.value.error)
    }

    // ========== Merge Strategy Selection ==========

    @Test
    fun `selectMergeStrategy updates strategy in state`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        assertEquals(MergeStrategy.KEEP_LOCAL, viewModel.state.value.mergeStrategy)

        viewModel.selectMergeStrategy(MergeStrategy.KEEP_BACKUP)
        assertEquals(MergeStrategy.KEEP_BACKUP, viewModel.state.value.mergeStrategy)

        viewModel.selectMergeStrategy(MergeStrategy.NEWEST)
        assertEquals(MergeStrategy.NEWEST, viewModel.state.value.mergeStrategy)
    }

    // ========== Wizard Navigation - Forward ==========

    @Test
    fun `nextStep from MODE_SELECTION with FRESH mode goes to VALIDATION`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep()

        assertEquals(RestoreStep.VALIDATION, viewModel.state.value.step)
    }

    @Test
    fun `nextStep from MODE_SELECTION with MERGE mode goes to MERGE_STRATEGY`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.nextStep()

        assertEquals(RestoreStep.MERGE_STRATEGY, viewModel.state.value.step)
    }

    @Test
    fun `nextStep from MERGE_STRATEGY goes to VALIDATION`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.nextStep() // MODE_SELECTION -> MERGE_STRATEGY

        viewModel.selectMergeStrategy(MergeStrategy.NEWEST)
        viewModel.nextStep() // MERGE_STRATEGY -> VALIDATION

        assertEquals(RestoreStep.VALIDATION, viewModel.state.value.step)
    }

    @Test
    fun `nextStep from VALIDATION goes to CONFIRMATION`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION

        assertEquals(RestoreStep.CONFIRMATION, viewModel.state.value.step)
    }

    @Test
    fun `nextStep from CONFIRMATION triggers restore`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING (triggers restore)
        advanceUntilIdle() // Let restore complete

        // After restore completes, should be at RESULTS
        assertEquals(RestoreStep.RESULTS, viewModel.state.value.step)
        assertFalse(viewModel.state.value.isRestoring)
        verifySuspend { api.restore(any()) }
    }

    @Test
    fun `restore completes and goes to RESULTS`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse(
            imported = mapOf("books" to 50, "users" to 3),
            skipped = mapOf("books" to 10),
            duration = "5.2s",
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING
        advanceUntilIdle()

        assertEquals(RestoreStep.RESULTS, viewModel.state.value.step)
        assertFalse(viewModel.state.value.isRestoring)
        assertNotNull(viewModel.state.value.restoreResults)
        assertEquals(50, viewModel.state.value.restoreResults?.imported?.get("books"))
        assertEquals(10, viewModel.state.value.restoreResults?.skipped?.get("books"))
        assertEquals("5.2s", viewModel.state.value.restoreResults?.duration)
    }

    // ========== Wizard Navigation - Back ==========

    @Test
    fun `previousStep from MODE_SELECTION stays at MODE_SELECTION`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.previousStep()
        assertEquals(RestoreStep.MODE_SELECTION, viewModel.state.value.step)
    }

    @Test
    fun `previousStep from MERGE_STRATEGY goes to MODE_SELECTION`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.nextStep() // -> MERGE_STRATEGY
        assertEquals(RestoreStep.MERGE_STRATEGY, viewModel.state.value.step)

        viewModel.previousStep()
        assertEquals(RestoreStep.MODE_SELECTION, viewModel.state.value.step)
    }

    @Test
    fun `previousStep from VALIDATION with FRESH mode goes to MODE_SELECTION`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION

        viewModel.previousStep()
        assertEquals(RestoreStep.MODE_SELECTION, viewModel.state.value.step)
    }

    @Test
    fun `previousStep from VALIDATION with MERGE mode goes to MERGE_STRATEGY`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.nextStep() // -> MERGE_STRATEGY
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        viewModel.nextStep() // -> VALIDATION

        viewModel.previousStep()
        assertEquals(RestoreStep.MERGE_STRATEGY, viewModel.state.value.step)
    }

    @Test
    fun `previousStep from CONFIRMATION goes to VALIDATION`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION

        viewModel.previousStep()
        assertEquals(RestoreStep.VALIDATION, viewModel.state.value.step)
    }

    @Test
    fun `previousStep from RESTORING stays at RESTORING (cannot go back)`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        // Make restore hang by not completing
        everySuspend { api.restore(any()) } returns createRestoreResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING

        viewModel.previousStep()
        // Should still be at RESTORING - can't go back during restore
        assertEquals(RestoreStep.RESTORING, viewModel.state.value.step)
    }

    @Test
    fun `previousStep from RESULTS stays at RESULTS (cannot go back)`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING
        advanceUntilIdle() // -> RESULTS

        viewModel.previousStep()
        assertEquals(RestoreStep.RESULTS, viewModel.state.value.step)
    }

    // ========== Dry Run ==========

    @Test
    fun `performDryRun executes with dryRun flag true`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse(
            imported = mapOf("books" to 100),
            skipped = mapOf("users" to 2),
            duration = "0.5s",
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        viewModel.performDryRun()
        advanceUntilIdle()

        // Verify dry run was called with correct parameters
        verifySuspend {
            api.restore(
                RestoreRequest(
                    backupId = "backup-1",
                    mode = "merge",
                    mergeStrategy = "keep_local",
                    dryRun = true,
                    confirmFullWipe = false,
                ),
            )
        }

        assertFalse(viewModel.state.value.isValidating)
        assertNotNull(viewModel.state.value.dryRunResults)
        assertEquals(100, viewModel.state.value.dryRunResults?.willImport?.get("books"))
        assertEquals(2, viewModel.state.value.dryRunResults?.willSkip?.get("users"))
    }

    @Test
    fun `performDryRun handles errors`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } throws RuntimeException("Server error")

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        viewModel.performDryRun()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isValidating)
        assertNull(viewModel.state.value.dryRunResults)
        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("preview"))
    }

    // ========== Actual Restore ==========

    @Test
    fun `restore with FRESH mode sends confirmFullWipe true`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING (triggers restore)
        advanceUntilIdle()

        verifySuspend {
            api.restore(
                RestoreRequest(
                    backupId = "backup-1",
                    mode = "full",
                    mergeStrategy = null,
                    dryRun = false,
                    confirmFullWipe = true,
                ),
            )
        }
    }

    @Test
    fun `restore with MERGE mode sends mergeStrategy`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.nextStep() // -> MERGE_STRATEGY
        viewModel.selectMergeStrategy(MergeStrategy.NEWEST)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING
        advanceUntilIdle()

        verifySuspend {
            api.restore(
                RestoreRequest(
                    backupId = "backup-1",
                    mode = "merge",
                    mergeStrategy = "newest",
                    dryRun = false,
                    confirmFullWipe = false,
                ),
            )
        }
    }

    @Test
    fun `restore handles partial failures with errors in response`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse(
            imported = mapOf("books" to 98),
            skipped = mapOf("books" to 2),
            errors = listOf(
                RestoreError(entityType = "book", entityId = "book-1", error = "Invalid format"),
                RestoreError(entityType = "book", entityId = "book-2", error = "Missing field"),
            ),
            duration = "3.0s",
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep()
        viewModel.nextStep()
        viewModel.nextStep()
        advanceUntilIdle()

        val results = viewModel.state.value.restoreResults
        assertNotNull(results)
        assertEquals(98, results.imported["books"])
        assertEquals(2, results.errors.size)
        assertEquals("book-1", results.errors[0].entityId)
    }

    @Test
    fun `restore handles complete failure`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } throws RuntimeException("Restore failed: database locked")

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep()
        viewModel.nextStep()
        viewModel.nextStep()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRestoring)
        assertNull(viewModel.state.value.restoreResults)
        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("database locked"))
        // Should still be at RESTORING step (not advance to RESULTS)
        assertEquals(RestoreStep.RESTORING, viewModel.state.value.step)
    }

    // ========== Merge Strategies ==========

    @Test
    fun `KEEP_LOCAL strategy uses keep_local API value`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.nextStep() // -> MERGE_STRATEGY
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING (triggers restore)
        advanceUntilIdle()

        verifySuspend {
            api.restore(
                RestoreRequest(
                    backupId = "backup-1",
                    mode = "merge",
                    mergeStrategy = "keep_local",
                    dryRun = false,
                    confirmFullWipe = false,
                ),
            )
        }
    }

    @Test
    fun `KEEP_BACKUP strategy uses keep_backup API value`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.nextStep() // -> MERGE_STRATEGY
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_BACKUP)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING (triggers restore)
        advanceUntilIdle()

        verifySuspend {
            api.restore(
                RestoreRequest(
                    backupId = "backup-1",
                    mode = "merge",
                    mergeStrategy = "keep_backup",
                    dryRun = false,
                    confirmFullWipe = false,
                ),
            )
        }
    }

    // ========== Error Handling ==========

    @Test
    fun `clearError clears error state`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } throws RuntimeException("Error")

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        viewModel.clearError()
        assertNull(viewModel.state.value.error)
    }

    // ========== Edge Cases ==========

    @Test
    fun `handles empty import counts`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse(
            imported = emptyMap(),
            skipped = emptyMap(),
            errors = emptyList(),
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep()
        viewModel.nextStep()
        viewModel.nextStep()
        advanceUntilIdle()

        val results = viewModel.state.value.restoreResults
        assertNotNull(results)
        assertTrue(results.imported.isEmpty())
        assertTrue(results.errors.isEmpty())
    }

    @Test
    fun `handles invalid backup validation`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse(
            valid = false,
            errors = listOf("Corrupted archive", "Missing manifest"),
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        val validation = viewModel.state.value.validation
        assertNotNull(validation)
        assertFalse(validation.valid)
        assertEquals(2, validation.errors.size)
    }

    @Test
    fun `restore preserves mode selection when navigating back and forward`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.nextStep() // -> MERGE_STRATEGY
        viewModel.selectMergeStrategy(MergeStrategy.NEWEST)
        viewModel.nextStep() // -> VALIDATION
        viewModel.previousStep() // -> MERGE_STRATEGY
        viewModel.previousStep() // -> MODE_SELECTION

        // Mode should still be selected
        assertEquals(RestoreMode.MERGE, viewModel.state.value.mode)
        assertEquals(MergeStrategy.NEWEST, viewModel.state.value.mergeStrategy)
    }

    @Test
    fun `full wizard flow for MERGE with KEEP_LOCAL strategy`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse(
            valid = true,
            expectedCounts = mapOf("books" to 500, "users" to 10),
        )
        everySuspend { api.restore(any()) } returns createRestoreResponse(
            imported = mapOf("books" to 450, "users" to 8),
            skipped = mapOf("books" to 50, "users" to 2),
            duration = "15.3s",
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        // Step 1: Mode selection
        assertEquals(RestoreStep.MODE_SELECTION, viewModel.state.value.step)
        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.nextStep()

        // Step 2: Merge strategy
        assertEquals(RestoreStep.MERGE_STRATEGY, viewModel.state.value.step)
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        viewModel.nextStep()

        // Step 3: Validation review
        assertEquals(RestoreStep.VALIDATION, viewModel.state.value.step)
        assertEquals(500, viewModel.state.value.validation?.entityCounts?.get("books"))
        viewModel.nextStep()

        // Step 4: Confirmation
        assertEquals(RestoreStep.CONFIRMATION, viewModel.state.value.step)
        viewModel.nextStep()

        // Step 5 & 6: Restoring -> Results (advance to completion)
        advanceUntilIdle()

        // After completion, should be at Results
        assertEquals(RestoreStep.RESULTS, viewModel.state.value.step)
        assertFalse(viewModel.state.value.isRestoring)
        assertEquals(450, viewModel.state.value.restoreResults?.imported?.get("books"))
        assertEquals(50, viewModel.state.value.restoreResults?.skipped?.get("books"))
        assertEquals("15.3s", viewModel.state.value.restoreResults?.duration)
    }

    @Test
    fun `full wizard flow for FRESH restore`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        // MODE_SELECTION -> VALIDATION (skips MERGE_STRATEGY for FRESH)
        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep()
        assertEquals(RestoreStep.VALIDATION, viewModel.state.value.step)

        // VALIDATION -> CONFIRMATION
        viewModel.nextStep()
        assertEquals(RestoreStep.CONFIRMATION, viewModel.state.value.step)

        // CONFIRMATION -> RESTORING -> RESULTS
        viewModel.nextStep()
        advanceUntilIdle()
        assertEquals(RestoreStep.RESULTS, viewModel.state.value.step)
    }

    // ========== Post-Restore Sync Tests ==========

    @Test
    fun `FRESH restore triggers forceFullResync after completion`() = runTest {
        val api: BackupApiContract = mock()
        val syncRepo: SyncRepository = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()
        everySuspend { syncRepo.forceFullResync() } returns Result.Success(Unit)
        everySuspend { syncRepo.refreshListeningHistory() } returns Result.Success(Unit)

        val viewModel = RestoreBackupViewModel("backup-1", api, syncRepo)
        advanceUntilIdle()

        // Navigate to restore
        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING
        advanceUntilIdle()

        // Verify forceFullResync was called (not refreshListeningHistory)
        verifySuspend { syncRepo.forceFullResync() }
    }

    @Test
    fun `MERGE restore triggers refreshListeningHistory after completion`() = runTest {
        val api: BackupApiContract = mock()
        val syncRepo: SyncRepository = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()
        everySuspend { syncRepo.forceFullResync() } returns Result.Success(Unit)
        everySuspend { syncRepo.refreshListeningHistory() } returns Result.Success(Unit)

        val viewModel = RestoreBackupViewModel("backup-1", api, syncRepo)
        advanceUntilIdle()

        // Navigate to restore with MERGE mode
        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.nextStep() // -> MERGE_STRATEGY
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING
        advanceUntilIdle()

        // Verify refreshListeningHistory was called (not forceFullResync)
        verifySuspend { syncRepo.refreshListeningHistory() }
    }

    // ========== Additional Edge Cases ==========

    @Test
    fun `selectMergeStrategy clears any previous error`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } throws RuntimeException("Error")

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `validation handles warnings in addition to errors`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse(
            valid = true,
            warnings = listOf("Some entities may have outdated references", "Media files not verified"),
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        val validation = viewModel.state.value.validation
        assertNotNull(validation)
        assertTrue(validation.valid)
        assertEquals(2, validation.warnings.size)
        assertTrue(validation.warnings.contains("Some entities may have outdated references"))
    }

    @Test
    fun `dry run handles errors in response without throwing`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse(
            imported = mapOf("books" to 95),
            skipped = mapOf("books" to 3),
            errors = listOf(
                RestoreError(entityType = "book", entityId = "book-corrupt", error = "Malformed JSON"),
                RestoreError(entityType = "user", entityId = "user-invalid", error = "Missing email"),
            ),
            duration = "1.2s",
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        viewModel.performDryRun()
        advanceUntilIdle()

        // Should succeed (not throw) even with errors in response
        assertNull(viewModel.state.value.error)
        val dryRun = viewModel.state.value.dryRunResults
        assertNotNull(dryRun)
        assertEquals(2, dryRun.errors.size)
        assertEquals("book-corrupt", dryRun.errors[0].entityId)
    }

    @Test
    fun `validation handles null expectedCounts from server`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns ValidationResponse(
            valid = true,
            version = "1.0",
            serverName = "Test",
            expectedCounts = null, // Server returns null
            errors = emptyList(),
            warnings = emptyList(),
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        val validation = viewModel.state.value.validation
        assertNotNull(validation)
        assertTrue(validation.entityCounts.isEmpty())
    }

    @Test
    fun `validation handles null version and serverName`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns ValidationResponse(
            valid = true,
            version = null,
            serverName = null,
            expectedCounts = mapOf("books" to 10),
            errors = emptyList(),
            warnings = emptyList(),
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        val validation = viewModel.state.value.validation
        assertNotNull(validation)
        assertNull(validation.version)
        assertNull(validation.serverName)
    }

    @Test
    fun `nextStep from RESULTS stays at RESULTS`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING
        advanceUntilIdle() // -> RESULTS

        assertEquals(RestoreStep.RESULTS, viewModel.state.value.step)

        // Try to go forward again - should stay at RESULTS
        viewModel.nextStep()
        assertEquals(RestoreStep.RESULTS, viewModel.state.value.step)
    }

    @Test
    fun `switching from MERGE to FRESH mode preserves merge strategy`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        // Select MERGE and set strategy
        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.selectMergeStrategy(MergeStrategy.NEWEST)
        assertEquals(MergeStrategy.NEWEST, viewModel.state.value.mergeStrategy)

        // Switch to FRESH
        viewModel.selectMode(RestoreMode.FRESH)

        // Strategy is preserved (UI might still need it if user switches back)
        assertEquals(MergeStrategy.NEWEST, viewModel.state.value.mergeStrategy)
    }

    @Test
    fun `multiple dry runs update results`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        // First dry run
        everySuspend { api.restore(any()) } returns createRestoreResponse(
            imported = mapOf("books" to 50),
            duration = "1.0s",
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        viewModel.performDryRun()
        advanceUntilIdle()

        assertEquals(50, viewModel.state.value.dryRunResults?.willImport?.get("books"))

        // Change strategy and run second dry run with different results
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_BACKUP)
        everySuspend { api.restore(any()) } returns createRestoreResponse(
            imported = mapOf("books" to 100),
            duration = "1.5s",
        )
        viewModel.performDryRun()
        advanceUntilIdle()

        // Results should be updated
        assertEquals(100, viewModel.state.value.dryRunResults?.willImport?.get("books"))
    }

    @Test
    fun `FRESH restore succeeds even when sync fails`() = runTest {
        val api: BackupApiContract = mock()
        val syncRepo: SyncRepository = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse(
            imported = mapOf("books" to 100),
        )
        // Sync fails after restore
        everySuspend { syncRepo.forceFullResync() } returns Result.Failure(
            exception = RuntimeException("Network unavailable"),
            message = "Failed to sync",
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, syncRepo)
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING
        advanceUntilIdle()

        // Restore should still be considered successful - data is on server
        assertEquals(RestoreStep.RESULTS, viewModel.state.value.step)
        assertNotNull(viewModel.state.value.restoreResults)
        assertEquals(100, viewModel.state.value.restoreResults?.imported?.get("books"))
        // Note: Currently sync failure is silently ignored - this test documents current behavior
        // TODO: Consider if we should warn user that local sync failed
    }

    @Test
    fun `MERGE restore succeeds even when sync fails`() = runTest {
        val api: BackupApiContract = mock()
        val syncRepo: SyncRepository = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()
        // Sync fails after restore
        everySuspend { syncRepo.refreshListeningHistory() } returns Result.Failure(
            exception = RuntimeException("SSE disconnected"),
            message = "Failed to refresh",
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, syncRepo)
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.nextStep() // -> MERGE_STRATEGY
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING
        advanceUntilIdle()

        // Restore completes successfully even with sync failure
        assertEquals(RestoreStep.RESULTS, viewModel.state.value.step)
        assertNotNull(viewModel.state.value.restoreResults)
    }

    @Test
    fun `FRESH mode API value is full`() {
        assertEquals("full", RestoreMode.FRESH.apiValue)
    }

    @Test
    fun `MERGE mode API value is merge`() {
        assertEquals("merge", RestoreMode.MERGE.apiValue)
    }

    @Test
    fun `all merge strategies have correct API values`() {
        assertEquals("keep_local", MergeStrategy.KEEP_LOCAL.apiValue)
        assertEquals("keep_backup", MergeStrategy.KEEP_BACKUP.apiValue)
        assertEquals("newest", MergeStrategy.NEWEST.apiValue)
    }

    @Test
    fun `dry run clears error from previous failed dry run`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        // First dry run fails
        everySuspend { api.restore(any()) } throws RuntimeException("Network error")

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        viewModel.performDryRun()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)

        // Second dry run succeeds
        everySuspend { api.restore(any()) } returns createRestoreResponse()
        viewModel.performDryRun()
        advanceUntilIdle()

        // Error should be cleared
        assertNull(viewModel.state.value.error)
        assertNotNull(viewModel.state.value.dryRunResults)
    }

    @Test
    fun `validation with empty entity counts`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse(
            expectedCounts = emptyMap(),
        )

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        val validation = viewModel.state.value.validation
        assertNotNull(validation)
        assertTrue(validation.entityCounts.isEmpty())
    }

    @Test
    fun `isValidating is false after validation completes`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        // After validation completes, isValidating should be false
        assertFalse(viewModel.state.value.isValidating)
        assertNotNull(viewModel.state.value.validation)
    }

    @Test
    fun `isValidating is false after dry run completes`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.MERGE)
        viewModel.selectMergeStrategy(MergeStrategy.KEEP_LOCAL)
        viewModel.performDryRun()
        advanceUntilIdle()

        // After dry run completes, isValidating should be false
        assertFalse(viewModel.state.value.isValidating)
        assertNotNull(viewModel.state.value.dryRunResults)
    }

    @Test
    fun `isRestoring is false after restore completes`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING (triggers restore)
        advanceUntilIdle()

        // After restore completes, isRestoring should be false
        assertFalse(viewModel.state.value.isRestoring)
        assertEquals(RestoreStep.RESULTS, viewModel.state.value.step)
    }

    @Test
    fun `restore failure does not advance to RESULTS step`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } throws RuntimeException("Server error")

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        viewModel.selectMode(RestoreMode.FRESH)
        viewModel.nextStep() // -> VALIDATION
        viewModel.nextStep() // -> CONFIRMATION
        viewModel.nextStep() // -> RESTORING
        advanceUntilIdle()

        // Should stay at RESTORING, not advance to RESULTS
        assertEquals(RestoreStep.RESTORING, viewModel.state.value.step)
        assertNotNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.restoreResults)
    }

    @Test
    fun `dry run uses default MERGE mode when mode is null`() = runTest {
        val api: BackupApiContract = mock()
        everySuspend { api.validateBackup(any()) } returns createValidationResponse()
        everySuspend { api.restore(any()) } returns createRestoreResponse()

        val viewModel = RestoreBackupViewModel("backup-1", api, createMockSyncRepository())
        advanceUntilIdle()

        // Don't select mode - leave it null
        assertNull(viewModel.state.value.mode)

        viewModel.performDryRun()
        advanceUntilIdle()

        // Should use MERGE as default
        verifySuspend {
            api.restore(
                RestoreRequest(
                    backupId = "backup-1",
                    mode = "merge", // Default when mode is null
                    mergeStrategy = null,
                    dryRun = true,
                    confirmFullWipe = false,
                ),
            )
        }
    }
}
