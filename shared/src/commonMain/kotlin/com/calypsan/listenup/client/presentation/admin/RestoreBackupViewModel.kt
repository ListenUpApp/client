package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.BackupApiContract
import com.calypsan.listenup.client.data.remote.model.RestoreError
import com.calypsan.listenup.client.data.remote.model.RestoreRequest
import com.calypsan.listenup.client.domain.model.BackupValidation
import com.calypsan.listenup.client.domain.repository.SyncRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Restore mode - fresh wipe or merge with existing data.
 * API values must match server's backup.RestoreMode constants.
 */
enum class RestoreMode(val apiValue: String, val displayName: String, val description: String) {
    FRESH("full", "Fresh Restore", "Wipe all existing data and restore from backup"),
    MERGE("merge", "Merge", "Keep existing data and merge with backup"),
}

/**
 * Merge strategy when using merge mode.
 * API values must match server's backup.MergeStrategy constants.
 */
enum class MergeStrategy(val apiValue: String, val displayName: String, val description: String) {
    KEEP_LOCAL("keep_local", "Keep Local", "Keep existing local data on conflicts"),
    KEEP_BACKUP("keep_backup", "Keep Backup", "Replace with backup data on conflicts"),
    NEWEST("newest", "Newest Wins", "Keep the most recently modified version"),
}

/**
 * Step in the restore wizard.
 */
enum class RestoreStep {
    MODE_SELECTION,
    MERGE_STRATEGY,
    VALIDATION,
    CONFIRMATION,
    RESTORING,
    RESULTS,
}

/**
 * UI state for restore backup flow.
 */
data class RestoreBackupState(
    val backupId: String = "",
    val step: RestoreStep = RestoreStep.MODE_SELECTION,
    val mode: RestoreMode? = null,
    val mergeStrategy: MergeStrategy? = null,
    val isValidating: Boolean = false,
    val validation: BackupValidation? = null,
    val dryRunResults: DryRunResults? = null,
    val isRestoring: Boolean = false,
    val restoreResults: RestoreResults? = null,
    val error: String? = null,
)

/**
 * Results from a dry run.
 */
data class DryRunResults(
    val willImport: Map<String, Int>,
    val willSkip: Map<String, Int>,
    val errors: List<RestoreError>,
    val duration: String,
)

/**
 * Results from the actual restore.
 */
data class RestoreResults(
    val imported: Map<String, Int>,
    val skipped: Map<String, Int>,
    val errors: List<RestoreError>,
    val duration: String,
)

/**
 * ViewModel for restore backup flow.
 */
class RestoreBackupViewModel(
    private val backupId: String,
    private val backupApi: BackupApiContract,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    val state: StateFlow<RestoreBackupState>
        field = MutableStateFlow(RestoreBackupState(backupId = backupId))

    init {
        validateBackup()
    }

    private fun validateBackup() {
        viewModelScope.launch {
            state.update { it.copy(isValidating = true, error = null) }

            try {
                val result = backupApi.validateBackup(backupId)
                state.update {
                    it.copy(
                        isValidating = false,
                        validation = BackupValidation(
                            valid = result.valid,
                            version = result.version,
                            serverName = result.serverName,
                            entityCounts = result.expectedCounts ?: emptyMap(),
                            errors = result.errors,
                            warnings = result.warnings,
                        ),
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to validate backup" }
                state.update {
                    it.copy(
                        isValidating = false,
                        error = "Failed to validate backup: ${e.message}",
                    )
                }
            }
        }
    }

    fun selectMode(mode: RestoreMode) {
        state.update { it.copy(mode = mode, error = null) }
    }

    fun selectMergeStrategy(strategy: MergeStrategy) {
        state.update { it.copy(mergeStrategy = strategy, error = null) }
    }

    fun nextStep() {
        val current = state.value
        val nextStep = when (current.step) {
            RestoreStep.MODE_SELECTION -> {
                if (current.mode == RestoreMode.MERGE) {
                    RestoreStep.MERGE_STRATEGY
                } else {
                    RestoreStep.VALIDATION
                }
            }
            RestoreStep.MERGE_STRATEGY -> RestoreStep.VALIDATION
            RestoreStep.VALIDATION -> RestoreStep.CONFIRMATION
            RestoreStep.CONFIRMATION -> {
                performRestore()
                RestoreStep.RESTORING
            }
            RestoreStep.RESTORING -> RestoreStep.RESULTS
            RestoreStep.RESULTS -> RestoreStep.RESULTS
        }
        state.update { it.copy(step = nextStep) }
    }

    fun previousStep() {
        val current = state.value
        val prevStep = when (current.step) {
            RestoreStep.MODE_SELECTION -> RestoreStep.MODE_SELECTION
            RestoreStep.MERGE_STRATEGY -> RestoreStep.MODE_SELECTION
            RestoreStep.VALIDATION -> {
                if (current.mode == RestoreMode.MERGE) {
                    RestoreStep.MERGE_STRATEGY
                } else {
                    RestoreStep.MODE_SELECTION
                }
            }
            RestoreStep.CONFIRMATION -> RestoreStep.VALIDATION
            RestoreStep.RESTORING -> RestoreStep.RESTORING // Can't go back during restore
            RestoreStep.RESULTS -> RestoreStep.RESULTS // Can't go back after complete
        }
        state.update { it.copy(step = prevStep) }
    }

    fun performDryRun() {
        viewModelScope.launch {
            state.update { it.copy(isValidating = true, error = null) }

            try {
                val current = state.value
                val result = backupApi.restore(
                    RestoreRequest(
                        backupId = backupId,
                        mode = current.mode?.apiValue ?: RestoreMode.MERGE.apiValue,
                        mergeStrategy = current.mergeStrategy?.apiValue,
                        dryRun = true,
                        confirmFullWipe = false,
                    ),
                )
                state.update {
                    it.copy(
                        isValidating = false,
                        dryRunResults = DryRunResults(
                            willImport = result.imported,
                            willSkip = result.skipped,
                            errors = result.errors,
                            duration = result.duration,
                        ),
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to perform dry run" }
                state.update {
                    it.copy(
                        isValidating = false,
                        error = "Failed to preview restore: ${e.message}",
                    )
                }
            }
        }
    }

    private fun performRestore() {
        viewModelScope.launch {
            state.update { it.copy(isRestoring = true, error = null) }

            try {
                val current = state.value
                val result = backupApi.restore(
                    RestoreRequest(
                        backupId = backupId,
                        mode = current.mode?.apiValue ?: RestoreMode.MERGE.apiValue,
                        mergeStrategy = current.mergeStrategy?.apiValue,
                        dryRun = false,
                        confirmFullWipe = current.mode == RestoreMode.FRESH,
                    ),
                )

                // After server restore completes, sync client state
                // FRESH: Server data was completely replaced, need to clear and resync
                // MERGE: Server data was merged, need to refresh listening history
                logger.info { "Restore complete, syncing client state for mode ${current.mode}" }
                if (current.mode == RestoreMode.FRESH) {
                    // Clear local database and do full resync
                    syncRepository.forceFullResync()
                } else {
                    // Merge mode - just refresh listening history like ABS import
                    syncRepository.refreshListeningHistory()
                }

                state.update {
                    it.copy(
                        isRestoring = false,
                        step = RestoreStep.RESULTS,
                        restoreResults = RestoreResults(
                            imported = result.imported,
                            skipped = result.skipped,
                            errors = result.errors,
                            duration = result.duration,
                        ),
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to restore backup" }
                state.update {
                    it.copy(
                        isRestoring = false,
                        error = "Failed to restore: ${e.message}",
                    )
                }
            }
        }
    }

    fun clearError() {
        state.update { it.copy(error = null) }
    }
}
