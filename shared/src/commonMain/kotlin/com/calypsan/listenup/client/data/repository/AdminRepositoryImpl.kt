package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.AdminInvite
import com.calypsan.listenup.client.data.remote.AdminUser
import com.calypsan.listenup.client.data.remote.CollectionRef
import com.calypsan.listenup.client.data.remote.CreateInviteRequest
import com.calypsan.listenup.client.data.remote.InboxBookResponse
import com.calypsan.listenup.client.data.remote.ServerSettingsRequest
import com.calypsan.listenup.client.data.remote.ServerSettingsResponse
import com.calypsan.listenup.client.domain.model.AdminUserInfo
import com.calypsan.listenup.client.domain.model.InboxBook
import com.calypsan.listenup.client.domain.model.InboxReleaseResult
import com.calypsan.listenup.client.domain.model.InviteInfo
import com.calypsan.listenup.client.domain.model.ServerSettings
import com.calypsan.listenup.client.domain.model.StagedCollection
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

    override suspend fun getServerSettings(): ServerSettings = adminApi.getServerSettings().toDomain()

    override suspend fun updateServerSettings(inboxEnabled: Boolean): ServerSettings =
        adminApi.updateServerSettings(ServerSettingsRequest(inboxEnabled = inboxEnabled)).toDomain()

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
