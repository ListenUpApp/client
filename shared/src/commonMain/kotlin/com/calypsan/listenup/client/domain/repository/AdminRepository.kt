package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.domain.model.AccessMode
import com.calypsan.listenup.client.domain.model.AdminUserInfo
import com.calypsan.listenup.client.domain.model.InboxBook
import com.calypsan.listenup.client.domain.model.InboxReleaseResult
import com.calypsan.listenup.client.domain.model.InviteInfo
import com.calypsan.listenup.client.data.remote.BrowseFilesystemResponse
import com.calypsan.listenup.client.domain.model.Library
import com.calypsan.listenup.client.domain.model.ServerSettings

/**
 * Repository contract for admin operations.
 *
 * Provides access to user management, invite management, and server settings.
 * Only accessible to users with admin privileges.
 *
 * Part of the domain layer - implementations live in the data layer.
 */
@Suppress("TooManyFunctions")
interface AdminRepository {
    // ═══════════════════════════════════════════════════════════════════════
    // USER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get all approved users.
     *
     * @return List of all approved users
     */
    suspend fun getUsers(): List<AdminUserInfo>

    /**
     * Get all users awaiting approval.
     *
     * @return List of pending users
     */
    suspend fun getPendingUsers(): List<AdminUserInfo>

    /**
     * Approve a pending user registration.
     *
     * @param userId The user ID to approve
     * @return The approved user info
     */
    suspend fun approveUser(userId: String): AdminUserInfo

    /**
     * Deny a pending user registration.
     *
     * @param userId The user ID to deny
     */
    suspend fun denyUser(userId: String)

    /**
     * Delete an existing user.
     *
     * @param userId The user ID to delete
     */
    suspend fun deleteUser(userId: String)

    /**
     * Get a single user by ID.
     *
     * @param userId The user ID to fetch
     * @return The user info
     */
    suspend fun getUser(userId: String): AdminUserInfo

    /**
     * Update a user's details and permissions.
     *
     * @param userId The user ID to update
     * @param firstName New first name (null to keep unchanged)
     * @param lastName New last name (null to keep unchanged)
     * @param role New role (null to keep unchanged)
     * @param canShare New share permission (null to keep unchanged)
     * @return The updated user info
     */
    suspend fun updateUser(
        userId: String,
        firstName: String? = null,
        lastName: String? = null,
        role: String? = null,
        canShare: Boolean? = null,
    ): AdminUserInfo

    // ═══════════════════════════════════════════════════════════════════════
    // INVITE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get all invites (both pending and claimed).
     *
     * @return List of all invites
     */
    suspend fun getInvites(): List<InviteInfo>

    /**
     * Create a new invite code.
     *
     * @param name Display name for the invite
     * @param email Email to restrict the invite to
     * @param role Role to assign to users who use this invite
     * @param expiresInDays Number of days until the invite expires
     * @return The created invite
     */
    suspend fun createInvite(
        name: String,
        email: String,
        role: String = "member",
        expiresInDays: Int = 7,
    ): InviteInfo

    /**
     * Delete/revoke an invite.
     *
     * @param inviteId The invite ID to delete
     */
    suspend fun deleteInvite(inviteId: String)

    // ═══════════════════════════════════════════════════════════════════════
    // SERVER SETTINGS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Enable or disable open registration.
     *
     * When enabled, new users can register without an invite.
     *
     * @param enabled True to enable open registration
     */
    suspend fun setOpenRegistration(enabled: Boolean)

    /**
     * Get server settings.
     *
     * @return Current server settings including inbox status
     */
    suspend fun getServerSettings(): ServerSettings

    /**
     * Update instance settings (remote URL, name).
     */
    suspend fun updateInstanceRemoteUrl(remoteUrl: String): String?

    suspend fun updateServerSettings(
        serverName: String? = null,
        inboxEnabled: Boolean? = null,
    ): ServerSettings

    // ═══════════════════════════════════════════════════════════════════════
    // INBOX MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get all books in the inbox.
     *
     * @return List of inbox books awaiting review
     */
    suspend fun getInboxBooks(): List<InboxBook>

    /**
     * Release books from inbox.
     *
     * Released books become visible to users (either publicly or
     * in their staged collections).
     *
     * @param bookIds List of book IDs to release
     * @return Release result with counts
     */
    suspend fun releaseBooks(bookIds: List<String>): InboxReleaseResult

    /**
     * Stage a collection for an inbox book.
     *
     * When the book is released, it will be added to this collection.
     *
     * @param bookId The inbox book ID
     * @param collectionId The collection to stage
     */
    suspend fun stageCollection(
        bookId: String,
        collectionId: String,
    )

    /**
     * Remove a staged collection from an inbox book.
     *
     * @param bookId The inbox book ID
     * @param collectionId The collection to unstage
     */
    suspend fun unstageCollection(
        bookId: String,
        collectionId: String,
    )

    // ═══════════════════════════════════════════════════════════════════════
    // LIBRARY MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get all libraries.
     *
     * @return List of all libraries
     */
    suspend fun getLibraries(): List<Library>

    /**
     * Get a specific library.
     *
     * @param libraryId The library ID
     * @return The library
     */
    suspend fun getLibrary(libraryId: String): Library

    /**
     * Update library settings.
     *
     * @param libraryId The library ID to update
     * @param name New library name (null to keep unchanged)
     * @param skipInbox New inbox skip setting (null to keep unchanged)
     * @param accessMode New access mode (null to keep unchanged)
     * @return The updated library
     */
    suspend fun updateLibrary(
        libraryId: String,
        name: String? = null,
        skipInbox: Boolean? = null,
        accessMode: AccessMode? = null,
    ): Library

    /**
     * Add a scan path to a library.
     *
     * @param libraryId The library ID
     * @param path Absolute filesystem path to add
     * @return The updated library
     */
    suspend fun addScanPath(
        libraryId: String,
        path: String,
    ): Library

    /**
     * Remove a scan path from a library.
     *
     * @param libraryId The library ID
     * @param path The scan path to remove
     * @return The updated library
     */
    suspend fun removeScanPath(
        libraryId: String,
        path: String,
    ): Library

    /**
     * Trigger a manual library rescan.
     *
     * @param libraryId The library ID
     */
    suspend fun triggerScan(libraryId: String)

    /**
     * Browse the server filesystem.
     *
     * @param path Directory path to browse
     * @return Directory listing
     */
    suspend fun browseFilesystem(path: String): BrowseFilesystemResponse
}
