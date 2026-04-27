package com.calypsan.listenup.client.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Unified entity for all pending push operations.
 *
 * All operations that need to sync data to the server flow through this table:
 * - Entity edits (books, contributors, series)
 * - Relationship changes (setContributors, setSeries)
 * - Merge/unmerge operations
 * - Listening events
 * - Playback positions
 * - User preferences
 *
 * The handler pattern provides type-specific behavior (coalescing, batching,
 * execution) while this table provides unified lifecycle tracking.
 */
@Entity(
    tableName = "pending_operations",
    indices = [
        Index(value = ["status"]),
        Index(value = ["operationType", "entityId"]),
        Index(value = ["batchKey"]),
    ],
)
data class PendingOperationEntity(
    @PrimaryKey
    val id: String,
    // What kind of operation
    val operationType: OperationType,
    val entityType: EntityType?,
    val entityId: String?,
    // The change itself (JSON, shape depends on operationType)
    val payload: String,
    // Batching support - operations with same key execute together
    val batchKey: String?,
    // Lifecycle tracking
    val status: OperationStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val attemptCount: Int,
    val lastError: String?,
)

/**
 * Types of operations that can be queued for push sync.
 */
enum class OperationType {
    // Entity edits (coalesce by entityId)
    BOOK_UPDATE,
    CONTRIBUTOR_UPDATE,
    SERIES_UPDATE,

    // Relationship operations (replace entirely, don't merge)
    SET_BOOK_CONTRIBUTORS,
    SET_BOOK_SERIES,

    // Merge/unmerge (never coalesce, order matters)
    MERGE_CONTRIBUTOR,
    UNMERGE_CONTRIBUTOR,

    // Listening events (batch by batchKey, never coalesce)
    LISTENING_EVENT,

    // Playback position (coalesce by entityId)
    PLAYBACK_POSITION,

    // User preferences (coalesce globally)
    USER_PREFERENCES,

    // Profile updates (coalesce by user)
    PROFILE_UPDATE,
    PROFILE_AVATAR,

    // Mark book as complete (retry on failure)
    MARK_COMPLETE,

    // End a playback session and record total duration (coalesce by book)
    END_PLAYBACK_SESSION,

    // Shelf mutations (never coalesce - each is a discrete intent)
    CREATE_SHELF,
    UPDATE_SHELF,
    DELETE_SHELF,
    ADD_BOOKS_TO_SHELF,
    REMOVE_BOOK_FROM_SHELF,
}

/**
 * Entity types for operations that target a specific entity.
 */
enum class EntityType {
    BOOK,
    CONTRIBUTOR,
    SERIES,
    USER,
    SHELF,
}

/**
 * Status of a pending operation in its lifecycle.
 */
enum class OperationStatus {
    /** Waiting to sync */
    PENDING,

    /** Currently being synced */
    IN_PROGRESS,

    /** Failed after retries, awaiting user action */
    FAILED,
}
