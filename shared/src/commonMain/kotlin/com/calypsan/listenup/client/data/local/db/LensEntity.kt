package com.calypsan.listenup.client.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.calypsan.listenup.client.core.Timestamp

/**
 * Room entity representing a lens in the local database.
 *
 * Lenses are user-created curated lists of books for personal organization
 * and social discovery. Unlike collections (admin-managed access boundaries),
 * lenses are personal - each belongs to one user.
 */
@Entity(
    tableName = "lenses",
    indices = [Index("ownerId")],
)
data class LensEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String?,
    val ownerId: String,
    val ownerDisplayName: String,
    val ownerAvatarColor: String,
    val bookCount: Int,
    val totalDurationSeconds: Long,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
) {
    /**
     * Returns the display name formatted for the current user context.
     *
     * - Owner sees: "To Read"
     * - Others see: "Simon's To Read"
     */
    fun displayName(currentUserId: String): String = if (ownerId == currentUserId) name else "$ownerDisplayName's $name"
}
