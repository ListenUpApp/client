package com.calypsan.listenup.client.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a user in the local database.
 *
 * Maps to the User domain model from the server.
 * Timestamps are stored as Unix epoch milliseconds for cross-platform compatibility.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,

    val email: String,

    val displayName: String,

    val isRoot: Boolean,

    /**
     * Creation timestamp in Unix epoch milliseconds.
     * Use kotlinx.datetime.Instant for domain model conversion.
     */
    val createdAt: Long,

    /**
     * Last update timestamp in Unix epoch milliseconds.
     * Use kotlinx.datetime.Instant for domain model conversion.
     */
    val updatedAt: Long
)
