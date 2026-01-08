package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.exceptionOrFromMessage
import com.calypsan.listenup.client.data.remote.model.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Contract for invite API operations.
 */
interface InviteApiContract {
    /**
     * Get invite details for the landing/registration screen.
     *
     * @param serverUrl The server URL (e.g., "https://audiobooks.example.com")
     * @param code The invite code
     * @return Invite details including name, email, server name, and validity
     */
    suspend fun getInviteDetails(
        serverUrl: String,
        code: String,
    ): InviteDetails

    /**
     * Claim an invite by creating a new user account.
     *
     * @param serverUrl The server URL
     * @param code The invite code
     * @param password The password for the new account
     * @return Auth response with tokens and user info
     */
    suspend fun claimInvite(
        serverUrl: String,
        code: String,
        password: String,
    ): AuthResponse
}

/**
 * API client for public invite operations (no authentication required).
 *
 * Used for:
 * - Fetching invite details to display on registration screen
 * - Claiming invites to create user accounts
 *
 * Creates a fresh HttpClient per request since server URL is dynamic
 * (comes from the invite deep link, not from stored settings).
 */
class InviteApi : InviteApiContract {
    private val json =
        Json {
            prettyPrint = false
            isLenient = false
            ignoreUnknownKeys = true
        }

    private fun createClient(serverUrl: String): HttpClient =
        HttpClient {
            install(ContentNegotiation) {
                json(this@InviteApi.json)
            }

            defaultRequest {
                url(serverUrl)
                contentType(ContentType.Application.Json)
            }
        }

    override suspend fun getInviteDetails(
        serverUrl: String,
        code: String,
    ): InviteDetails {
        val client = createClient(serverUrl)
        try {
            val response: ApiResponse<InviteDetails> =
                client.get("/api/v1/invites/$code").body()

            return when (val result = response.toResult()) {
                is Success -> result.data
                is Failure -> throw result.exceptionOrFromMessage()
            }
        } finally {
            client.close()
        }
    }

    override suspend fun claimInvite(
        serverUrl: String,
        code: String,
        password: String,
    ): AuthResponse {
        val client = createClient(serverUrl)
        try {
            val deviceInfo =
                DeviceInfo(
                    deviceType = "mobile",
                    platform = "Android",
                    platformVersion = "Unknown",
                    clientName = "ListenUp Mobile",
                    clientVersion = "1.0.0",
                    clientBuild = "1",
                    deviceModel =
                        com.calypsan.listenup.client.core.PlatformUtils
                            .getDeviceModel(),
                )

            val response: ApiResponse<AuthResponse> =
                client
                    .post("/api/v1/invites/$code/claim") {
                        setBody(ClaimInviteRequest(password, deviceInfo))
                    }.body()

            return when (val result = response.toResult()) {
                is Success -> result.data
                is Failure -> throw result.exceptionOrFromMessage()
            }
        } finally {
            client.close()
        }
    }
}

// Request DTOs

@Serializable
private data class ClaimInviteRequest(
    @SerialName("password") val password: String,
    @SerialName("device_info") val deviceInfo: DeviceInfo,
)

// Response DTOs

/**
 * Invite details returned from the server.
 *
 * Used to display information on the registration screen
 * before the user claims the invite.
 */
@Serializable
data class InviteDetails(
    /** The pre-filled display name for the user */
    @SerialName("name") val name: String,
    /** The pre-filled email address for the user */
    @SerialName("email") val email: String,
    /** The name of the server/library being joined */
    @SerialName("server_name") val serverName: String,
    /** Display name of the person who sent the invite */
    @SerialName("invited_by") val invitedBy: String,
    /** Whether the invite is still valid (not claimed, not expired) */
    @SerialName("valid") val valid: Boolean,
)
