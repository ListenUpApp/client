package com.calypsan.listenup.client.domain.model

import kotlinx.datetime.Instant

/**
 * Domain model for a backup file.
 */
data class BackupInfo(
    val id: String,
    val path: String,
    val size: Long,
    val createdAt: Instant,
    val checksum: String? = null,
) {
    /**
     * Human-readable size string.
     */
    val sizeFormatted: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> {
                val mb = size / (1024.0 * 1024.0)
                "${(mb * 10).toLong() / 10.0} MB"
            }
            else -> {
                val gb = size / (1024.0 * 1024.0 * 1024.0)
                "${(gb * 100).toLong() / 100.0} GB"
            }
        }
}

/**
 * Restore mode for backup restoration.
 */
enum class RestoreMode(val value: String) {
    /** Replace all data with backup contents */
    FULL("full"),
    /** Merge backup data with existing data */
    MERGE("merge"),
    /** Only import listening events */
    EVENTS_ONLY("events_only"),
}

/**
 * Conflict resolution strategy for merge mode.
 */
enum class MergeStrategy(val value: String) {
    /** Keep existing local data */
    KEEP_LOCAL("keep_local"),
    /** Overwrite with backup data */
    KEEP_BACKUP("keep_backup"),
    /** Keep whichever is newest */
    NEWEST("newest"),
}

/**
 * Result of a restore operation.
 */
data class RestoreResult(
    val imported: Map<String, Int>,
    val skipped: Map<String, Int>,
    val errors: List<String>,
    val duration: String,
)

/**
 * Validation result for a backup.
 */
data class BackupValidation(
    val valid: Boolean,
    val version: String?,
    val serverName: String?,
    val entityCounts: Map<String, Int>,
    val errors: List<String>,
    val warnings: List<String>,
)
