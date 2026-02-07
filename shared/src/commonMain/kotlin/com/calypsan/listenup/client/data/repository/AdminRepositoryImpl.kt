package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.BrowseFilesystemResponse
import com.calypsan.listenup.client.data.remote.AdminInvite
import com.calypsan.listenup.client.data.remote.AdminUser
import com.calypsan.listenup.client.data.remote.CollectionRef
import com.calypsan.listenup.client.data.remote.CreateInviteRequest
import com.calypsan.listenup.client.data.remote.InboxBookResponse
import com.calypsan.listenup.client.data.remote.LibraryResponse
import com.calypsan.listenup.client.data.remote.ServerSettingsRequest
import com.calypsan.listenup.client.data.remote.UpdateInstanceRequest
import com.calypsan.listenup.client.data.remote.ServerSettingsResponse
import com.calypsan.listenup.client.data.remote.UpdateLibraryRequest
import com.calypsan.listenup.client.data.remote.UpdatePermissionsRequest
import com.calypsan.listenup.client.data.remote.UpdateUserRequest
import com.calypsan.listenup.client.domain.model.AccessMode
import com.calypsan.listenup.client.domain.model.AdminUserInfo
import com.calypsan.listenup.client.domain.model.InboxBook
import com.calypsan.listenup.client.domain.model.InboxReleaseResult
import com.calypsan.listenup.client.domain.model.InviteInfo
import com.calypsan.listenup.client.domain.model.Library
import com.calypsan.listenup.client.domain.model.ServerSettings
import com.calypsan.listenup.client.domain.model.StagedCollection
import com.calypsan.listenup.client.domain.model.UserPermissions
import com.calypsan.listenup.client.domain.repository.AdminRepository

/**
 * Implementation of AdminRepository using AdminApiContract.
 *
 * Wraps admin API calls and converts data layer types to domain models.
 *
 * @property adminApi API client for admin operations
 */
class AdminRepositoryImpl(
    private val adminApi: AdminApiContract,
) : AdminRepository {
    // ═══════════════════════════════════════════════════════════════════════
    // USER MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    override suspend fun getUsers(): List<AdminUserInfo> = adminApi.getUsers().map { it.toDomain() }

    override suspend fun getPendingUsers(): List<AdminUserInfo> = adminApi.getPendingUsers().map { it.toDomain() }

    override suspend fun approveUser(userId: String): AdminUserInfo = adminApi.approveUser(userId).toDomain()

    override suspend fun denyUser(userId: String) {
        adminApi.denyUser(userId)
    }

    override suspend fun deleteUser(userId: String) {
        adminApi.deleteUser(userId)
    }

    override suspend fun getUser(userId: String): AdminUserInfo = adminApi.getUser(userId).toDomain()

    override suspend fun updateUser(
        userId: String,
        firstName: String?,
        lastName: String?,
        role: String?,
        canDownload: Boolean?,
        canShare: Boolean?,
    ): AdminUserInfo {
        val permissionsUpdate =
            if (canDownload != null || canShare != null) {
                UpdatePermissionsRequest(
                    canDownload = canDownload,
                    canShare = canShare,
                )
            } else {
                null
            }

        val request =
            UpdateUserRequest(
                firstName = firstName,
                lastName = lastName,
                role = role,
                permissions = permissionsUpdate,
            )

        return adminApi.updateUser(userId, request).toDomain()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INVITE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    override suspend fun getInvites(): List<InviteInfo> = adminApi.getInvites().map { it.toDomain() }

    override suspend fun createInvite(
        name: String,
        email: String,
        role: String,
        expiresInDays: Int,
    ): InviteInfo {
        val request =
            CreateInviteRequest(
                name = name,
                email = email,
                role = role,
                expiresInDays = expiresInDays,
            )
        return adminApi.createInvite(request).toDomain()
    }

    override suspend fun deleteInvite(inviteId: String) {
        adminApi.deleteInvite(inviteId)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SERVER SETTINGS
    // ═══════════════════════════════════════════════════════════════════════

    override suspend fun setOpenRegistration(enabled: Boolean) {
        adminApi.setOpenRegistration(enabled)
    }

    override suspend fun updateInstanceRemoteUrl(remoteUrl: String): String? {
        val response = adminApi.updateInstance(UpdateInstanceRequest(remoteUrl = remoteUrl))
        return response.remoteUrl
    }

    override suspend fun getServerSettings(): ServerSettings = adminApi.getServerSettings().toDomain()

    override suspend fun updateServerSettings(
        serverName: String?,
        inboxEnabled: Boolean?,
    ): ServerSettings =
        adminApi.updateServerSettings(
            ServerSettingsRequest(serverName = serverName, inboxEnabled = inboxEnabled),
        ).toDomain()

    // ═══════════════════════════════════════════════════════════════════════
    // INBOX MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    override suspend fun getInboxBooks(): List<InboxBook> = adminApi.listInboxBooks().books.map { it.toDomain() }

    override suspend fun releaseBooks(bookIds: List<String>): InboxReleaseResult {
        val response = adminApi.releaseBooks(bookIds)
        return InboxReleaseResult(
            released = response.released,
            publicCount = response.public,
            toCollections = response.toCollections,
        )
    }

    override suspend fun stageCollection(
        bookId: String,
        collectionId: String,
    ) {
        adminApi.stageCollection(bookId, collectionId)
    }

    override suspend fun unstageCollection(
        bookId: String,
        collectionId: String,
    ) {
        adminApi.unstageCollection(bookId, collectionId)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LIBRARY MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    override suspend fun getLibraries(): List<Library> = adminApi.getLibraries().map { it.toDomain() }

    override suspend fun getLibrary(libraryId: String): Library = adminApi.getLibrary(libraryId).toDomain()

    override suspend fun updateLibrary(
        libraryId: String,
        name: String?,
        skipInbox: Boolean?,
        accessMode: AccessMode?,
    ): Library {
        val request =
            UpdateLibraryRequest(
                name = name,
                skipInbox = skipInbox,
                accessMode = accessMode?.toApiString(),
            )
        return adminApi.updateLibrary(libraryId, request).toDomain()
    }

    override suspend fun addScanPath(libraryId: String, path: String): Library =
        adminApi.addScanPath(libraryId, path).toDomain()

    override suspend fun removeScanPath(libraryId: String, path: String): Library =
        adminApi.removeScanPath(libraryId, path).toDomain()

    override suspend fun triggerScan(libraryId: String) {
        adminApi.triggerScan(libraryId)
    }

    override suspend fun browseFilesystem(path: String): BrowseFilesystemResponse =
        adminApi.browseFilesystem(path)
}

// ═══════════════════════════════════════════════════════════════════════════
// CONVERSION FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Convert AdminUser API model to AdminUserInfo domain model.
 */
private fun AdminUser.toDomain(): AdminUserInfo =
    AdminUserInfo(
        id = id,
        email = email,
        displayName = displayName,
        firstName = firstName,
        lastName = lastName,
        isRoot = isRoot,
        role = role,
        status = status,
        permissions =
            UserPermissions(
                canDownload = permissions.canDownload,
                canShare = permissions.canShare,
            ),
        createdAt = createdAt,
    )

/**
 * Convert AdminInvite API model to InviteInfo domain model.
 */
private fun AdminInvite.toDomain(): InviteInfo =
    InviteInfo(
        id = id,
        code = code,
        name = name,
        email = email,
        role = role,
        expiresAt = expiresAt,
        claimedAt = claimedAt,
        url = url,
        createdAt = createdAt,
    )

/**
 * Convert ServerSettingsResponse API model to ServerSettings domain model.
 */
private fun ServerSettingsResponse.toDomain(): ServerSettings =
    ServerSettings(
        serverName = serverName,
        inboxEnabled = inboxEnabled,
        inboxCount = inboxCount,
    )

/**
 * Convert InboxBookResponse API model to InboxBook domain model.
 */
private fun InboxBookResponse.toDomain(): InboxBook =
    InboxBook(
        id = id,
        title = title,
        author = author,
        coverUrl = coverUrl,
        duration = duration,
        stagedCollectionIds = stagedCollectionIds,
        stagedCollections = stagedCollections.map { it.toDomain() },
        scannedAt = scannedAt,
    )

/**
 * Convert CollectionRef API model to StagedCollection domain model.
 */
private fun CollectionRef.toDomain(): StagedCollection =
    StagedCollection(
        id = id,
        name = name,
    )

/**
 * Convert LibraryResponse API model to Library domain model.
 */
private fun LibraryResponse.toDomain(): Library =
    Library(
        id = id,
        name = name,
        ownerId = ownerId,
        scanPaths = scanPaths,
        skipInbox = skipInbox,
        accessMode = AccessMode.fromString(accessMode),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
