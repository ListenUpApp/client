package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.BackupApiContract
import com.calypsan.listenup.client.domain.model.BackupInfo
import com.calypsan.listenup.client.domain.model.BackupValidation
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

private val logger = KotlinLogging.logger {}

/**
 * UI state for the backup list screen.
 */
data class AdminBackupState(
    val backups: List<BackupInfo> = emptyList(),
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
    val deleteConfirmBackup: BackupInfo? = null,
    val validationResult: BackupValidation? = null,
    val validatingBackupId: String? = null,
)

/**
 * ViewModel for managing backups.
 */
class AdminBackupViewModel(
    private val backupApi: BackupApiContract,
) : ViewModel() {

    val state: StateFlow<AdminBackupState>
        field = MutableStateFlow(AdminBackupState())

    init {
        loadBackups()
    }

    fun loadBackups() {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            try {
                val backups = backupApi.listBackups()
                state.update {
                    it.copy(
                        backups = backups.map { b -> b.toDomain() }
                            .sortedByDescending { b -> b.createdAt },
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load backups" }
                state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load backups: ${e.message}",
                    )
                }
            }
        }
    }

    fun createBackup(includeImages: Boolean, includeEvents: Boolean) {
        viewModelScope.launch {
            state.update { it.copy(isCreating = true, error = null) }

            try {
                backupApi.createBackup(
                    includeImages = includeImages,
                    includeEvents = includeEvents,
                )
                // Reload list to show new backup
                loadBackups()
                state.update { it.copy(isCreating = false) }
            } catch (e: Exception) {
                logger.error(e) { "Failed to create backup" }
                state.update {
                    it.copy(
                        isCreating = false,
                        error = "Failed to create backup: ${e.message}",
                    )
                }
            }
        }
    }

    fun showDeleteConfirmation(backup: BackupInfo) {
        state.update { it.copy(deleteConfirmBackup = backup) }
    }

    fun dismissDeleteConfirmation() {
        state.update { it.copy(deleteConfirmBackup = null) }
    }

    fun deleteBackup(backup: BackupInfo) {
        viewModelScope.launch {
            state.update { it.copy(isDeleting = true, deleteConfirmBackup = null) }

            try {
                backupApi.deleteBackup(backup.id)
                state.update {
                    it.copy(
                        backups = it.backups.filter { b -> b.id != backup.id },
                        isDeleting = false,
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete backup" }
                state.update {
                    it.copy(
                        isDeleting = false,
                        error = "Failed to delete backup: ${e.message}",
                    )
                }
            }
        }
    }

    fun validateBackup(backup: BackupInfo) {
        viewModelScope.launch {
            state.update { it.copy(validatingBackupId = backup.id) }

            try {
                val result = backupApi.validateBackup(backup.id)
                state.update {
                    it.copy(
                        validatingBackupId = null,
                        validationResult = BackupValidation(
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
                        validatingBackupId = null,
                        error = "Failed to validate backup: ${e.message}",
                    )
                }
            }
        }
    }

    fun dismissValidation() {
        state.update { it.copy(validationResult = null) }
    }

    fun clearError() {
        state.update { it.copy(error = null) }
    }

    private fun com.calypsan.listenup.client.data.remote.model.BackupResponse.toDomain() =
        BackupInfo(
            id = id,
            path = path,
            size = size,
            createdAt = try {
                Instant.parse(createdAt)
            } catch (e: Exception) {
                Instant.DISTANT_PAST
            },
            checksum = checksum,
        )
}
