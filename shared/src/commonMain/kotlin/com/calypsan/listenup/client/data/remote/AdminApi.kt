@file:Suppress("StringLiteralDuplication")

package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.remote.model.ApiResponse
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contract for admin API operations.
 * All methods require authentication as an admin user.
 */
interface AdminApiContract {
    // User management
    suspend fun getUsers(): List<AdminUser>

    suspend fun getUser(userId: String): AdminUser

    suspend fun updateUser(
        userId: String,
        request: UpdateUserRequest,
    ): AdminUser

    suspend fun deleteUser(userId: String)

    // Pending user management
    suspend fun getPendingUsers(): List<AdminUser>

    suspend fun approveUser(userId: String): AdminUser

    suspend fun denyUser(userId: String)

    // Invite management
    suspend fun getInvites(): List<AdminInvite>

    suspend fun createInvite(request: CreateInviteRequest): AdminInvite

    suspend fun deleteInvite(inviteId: String)

    // Settings
    suspend fun setOpenRegistration(enabled: Boolean)
}

/**
 * API client for admin operations.
 *
 * Requires authentication via ApiClientFactory.
 * All endpoints require the user to be an admin (IsRoot or Role=admin).
 */
class AdminApi(
    private val clientFactory: ApiClientFactory,
) : AdminApiContract {
    // User Management

    override suspend fun getUsers(): List<AdminUser> {
        val client = clientFactory.getClient()
        val response: ApiResponse<UsersResponse> =
            client.get("/api/v1/admin/users").body()

        return when (val result = response.toResult()) {
            is Success -> result.data.users
            is Failure -> throw result.exception
        }
    }

    override suspend fun getUser(userId: String): AdminUser {
        val client = clientFactory.getClient()
        val response: ApiResponse<AdminUser> =
            client.get("/api/v1/admin/users/$userId").body()

        return when (val result = response.toResult()) {
            is Success -> result.data
            is Failure -> throw result.exception
        }
    }

    override suspend fun updateUser(
        userId: String,
        request: UpdateUserRequest,
    ): AdminUser {
        val client = clientFactory.getClient()
        val response: ApiResponse<AdminUser> =
            client
                .patch("/api/v1/admin/users/$userId") {
                    setBody(request)
                }.body()

        return when (val result = response.toResult()) {
            is Success -> result.data
            is Failure -> throw result.exception
        }
    }

    override suspend fun deleteUser(userId: String) {
        val client = clientFactory.getClient()
        val response = client.delete("/api/v1/admin/users/$userId")

        if (!response.status.isSuccess()) {
            // Try to parse error response
            val errorResponse: ApiResponse<Unit> = response.body()
            when (val result = errorResponse.toResult()) {
                is Success -> { /* Shouldn't happen */ }

                is Failure -> {
                    throw result.exception
                }
            }
        }
        // 204 No Content - success
    }

    // Pending User Management

    override suspend fun getPendingUsers(): List<AdminUser> {
        val client = clientFactory.getClient()
        val response: ApiResponse<UsersResponse> =
            client.get("/api/v1/admin/users/pending").body()

        return when (val result = response.toResult()) {
            is Success -> result.data.users
            is Failure -> throw result.exception
        }
    }

    override suspend fun approveUser(userId: String): AdminUser {
        val client = clientFactory.getClient()
        val response: ApiResponse<AdminUser> =
            client.post("/api/v1/admin/users/$userId/approve").body()

        return when (val result = response.toResult()) {
            is Success -> result.data
            is Failure -> throw result.exception
        }
    }

    override suspend fun denyUser(userId: String) {
        val client = clientFactory.getClient()
        val response = client.post("/api/v1/admin/users/$userId/deny")

        if (!response.status.isSuccess()) {
            val errorResponse: ApiResponse<Unit> = response.body()
            when (val result = errorResponse.toResult()) {
                is Success -> { /* Shouldn't happen */ }
                is Failure -> throw result.exception
            }
        }
    }

    // Invite Management

    override suspend fun getInvites(): List<AdminInvite> {
        val client = clientFactory.getClient()
        val response: ApiResponse<InvitesResponse> =
            client.get("/api/v1/admin/invites").body()

        return when (val result = response.toResult()) {
            is Success -> result.data.invites
            is Failure -> throw result.exception
        }
    }

    override suspend fun createInvite(request: CreateInviteRequest): AdminInvite {
        val client = clientFactory.getClient()
        val response: ApiResponse<AdminInvite> =
            client
                .post("/api/v1/admin/invites") {
                    setBody(request)
                }.body()

        return when (val result = response.toResult()) {
            is Success -> result.data
            is Failure -> throw result.exception
        }
    }

    override suspend fun deleteInvite(inviteId: String) {
        val client = clientFactory.getClient()
        val response = client.delete("/api/v1/admin/invites/$inviteId")

        if (!response.status.isSuccess()) {
            // Try to parse error response
            val errorResponse: ApiResponse<Unit> = response.body()
            when (val result = errorResponse.toResult()) {
                is Success -> { /* Shouldn't happen */ }

                is Failure -> {
                    throw result.exception
                }
            }
        }
        // 204 No Content - success
    }

    // Settings

    override suspend fun setOpenRegistration(enabled: Boolean) {
        val client = clientFactory.getClient()
        val response =
            client.put("/api/v1/admin/settings/open-registration") {
                setBody(SetOpenRegistrationRequest(enabled))
            }

        if (!response.status.isSuccess()) {
            val errorResponse: ApiResponse<Unit> = response.body()
            when (val result = errorResponse.toResult()) {
                is Success -> { /* Shouldn't happen */ }
                is Failure -> throw result.exception
            }
        }
    }
}

// Response wrappers

@Serializable
private data class UsersResponse(
    @SerialName("users") val users: List<AdminUser>,
)

@Serializable
private data class InvitesResponse(
    @SerialName("invites") val invites: List<AdminInvite>,
)

// Models

/**
 * Admin view of a user.
 * Contains more information than the regular user model.
 */
@Serializable
data class AdminUser(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("is_root") val isRoot: Boolean,
    @SerialName("role") val role: String,
    @SerialName("status") val status: String = "active",
    @SerialName("invited_by") val invitedBy: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_login_at") val lastLoginAt: String? = null,
) {
    /**
     * Check if this user can be modified/deleted by the current admin.
     * Root users cannot be modified except by themselves.
     */
    val isProtected: Boolean get() = isRoot

    /**
     * Whether this user is pending admin approval.
     */
    val isPending: Boolean get() = status == "pending"
}

/**
 * Admin view of an invite.
 */
@Serializable
data class AdminInvite(
    @SerialName("id") val id: String,
    @SerialName("code") val code: String,
    @SerialName("name") val name: String,
    @SerialName("email") val email: String,
    @SerialName("role") val role: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("claimed_at") val claimedAt: String? = null,
    @SerialName("claimed_by") val claimedBy: String? = null,
    @SerialName("url") val url: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    /**
     * Human-readable status of the invite.
     */
    val status: InviteStatus
        get() = if (claimedAt != null) InviteStatus.CLAIMED else InviteStatus.PENDING
}

enum class InviteStatus {
    PENDING,
    CLAIMED,
    EXPIRED,
    REVOKED,
}

/**
 * Request to create a new invite.
 */
@Serializable
data class CreateInviteRequest(
    @SerialName("name") val name: String,
    @SerialName("email") val email: String,
    @SerialName("role") val role: String = "member",
    @SerialName("expires_in_days") val expiresInDays: Int = 7,
)

/**
 * Request to update a user.
 */
@Serializable
data class UpdateUserRequest(
    @SerialName("role") val role: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
)

/**
 * Request to set open registration setting.
 */
@Serializable
data class SetOpenRegistrationRequest(
    @SerialName("enabled") val enabled: Boolean,
)
