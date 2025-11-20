package com.calypsan.listenup.client.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for User entities.
 *
 * Provides both suspend functions for one-time queries and Flow for reactive queries.
 * Uses Upsert for convenience (insert or update if exists).
 */
@Dao
interface UserDao {
    /**
     * Insert or update a user.
     * If a user with the same ID exists, it will be updated.
     */
    @Upsert
    suspend fun upsert(user: UserEntity)

    /**
     * Get the current user (single-user app model).
     * @return UserEntity if exists, null otherwise
     */
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    /**
     * Observe the current user reactively.
     * Emits whenever the user data changes.
     * @return Flow that emits UserEntity when available, null otherwise
     */
    @Query("SELECT * FROM users LIMIT 1")
    fun observeCurrentUser(): Flow<UserEntity?>

    /**
     * Clear all users from the database.
     * Used during logout or data reset.
     */
    @Query("DELETE FROM users")
    suspend fun clear()
}
