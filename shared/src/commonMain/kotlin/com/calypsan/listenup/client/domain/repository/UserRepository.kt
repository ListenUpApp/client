package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for user profile operations.
 *
 * Defines the interface for accessing the current user's data.
 * Implementations handle data source details (Room, API, etc).
 *
 * Part of the domain layer - implementations live in the data layer.
 */
interface UserRepository {
    /**
     * Observe the current user reactively.
     *
     * Emits whenever user data changes, enabling real-time UI updates
     * for profile information, display name, and avatar changes.
     *
     * @return Flow that emits User when available, null if not logged in
     */
    fun observeCurrentUser(): Flow<User?>

    /**
     * Observe whether the current user has admin privileges.
     *
     * Used by ViewModels to conditionally show admin-only features
     * such as user management, library configuration, and invites.
     *
     * @return Flow emitting true if user is admin, false otherwise
     */
    fun observeIsAdmin(): Flow<Boolean>

    /**
     * Get the current user synchronously.
     *
     * Useful for one-time checks where reactive observation isn't needed.
     *
     * @return User if logged in, null otherwise
     */
    suspend fun getCurrentUser(): User?
}
