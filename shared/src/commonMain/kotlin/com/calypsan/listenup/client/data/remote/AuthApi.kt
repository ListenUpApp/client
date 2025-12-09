package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.remote.model.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * API client for authentication operations.
 *
 * Uses its own HttpClient instance (no auth plugin) since these endpoints
 * don't require authentication headers. The auth plugin would interfere
 * with token refresh logic.
 *
 * Reads server URL dynamically from SettingsRepository to support runtime
 * URL changes (e.g., user connecting to a different server).
 *
 * All endpoints return new tokens on success, following the server's
 * token rotation pattern (15min access, 30d refresh).
 */
class AuthApi(
    private val getServerUrl: suspend () -> ServerUrl?,
) {
    private val json =
        Json {
            prettyPrint = false
            isLenient = false
            ignoreUnknownKeys = true
        }

    private fun createClient(serverUrl: ServerUrl): HttpClient =
        HttpClient {
            install(ContentNegotiation) {
                json(this@AuthApi.json)
            }

            defaultRequest {
                url(serverUrl.value)
                contentType(ContentType.Application.Json)
            }
        }

    /**
     * Get server URL or throw if not configured.
     */
    private suspend fun requireServerUrl(): ServerUrl = getServerUrl() ?: error("Server URL not configured")

    /**
     * Create the root/admin user during initial server setup.
     *
     * This endpoint is only available when the server has no users configured.
     * Once a root user exists, this endpoint will return an error.
     *
     * @param email User's email address (will be username)
     * @param password User's password (min 8 characters)
     * @param firstName User's first name
     * @param lastName User's last name
     * @return AuthResponse with tokens and user info on success
     * @throws Exception on network errors or validation failures
     */
    suspend fun setup(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ): AuthResponse {
        val client = createClient(requireServerUrl())
        try {
            val response: ApiResponse<AuthResponse> =
                client
                    .post("/api/v1/auth/setup") {
                        setBody(SetupRequest(email, password, firstName, lastName))
                    }.body()

            return when (val result = response.toResult()) {
                is Success -> result.data
                is Failure -> throw result.exception
            }
        } finally {
            client.close()
        }
    }

    /**
     * Login with email and password.
     *
     * @return AuthResponse with tokens and user info on success
     * @throws Exception on network errors or invalid credentials
     */
    suspend fun login(
        email: String,
        password: String,
    ): AuthResponse {
        val deviceInfo =
            DeviceInfo(
                deviceType = "mobile",
                platform = getPlatform(),
                platformVersion = getPlatformVersion(),
                clientName = "ListenUp Mobile",
                clientVersion = "1.0.0",
                clientBuild = "1",
                deviceModel = getDeviceModel(),
            )

        val client = createClient(requireServerUrl())
        try {
            val response: ApiResponse<AuthResponse> =
                client
                    .post("/api/v1/auth/login") {
                        setBody(LoginRequest(email, password, deviceInfo))
                    }.body()

            return when (val result = response.toResult()) {
                is Success -> result.data
                is Failure -> throw result.exception
            }
        } finally {
            client.close()
        }
    }

    /**
     * Get the current platform name.
     * Expected to be implemented via expect/actual pattern.
     */
    private fun getPlatform(): String = "Android" // TODO: Use expect/actual

    /**
     * Get the current platform version.
     * Expected to be implemented via expect/actual pattern.
     */
    private fun getPlatformVersion(): String = "Unknown" // TODO: Use expect/actual

    /**
     * Get the device model.
     * Expected to be implemented via expect/actual pattern.
     */
    private fun getDeviceModel(): String = "Unknown" // TODO: Use expect/actual

    /**
     * Refresh access token using refresh token.
     *
     * Server rotates both tokens on refresh for security.
     * Old tokens are invalidated after successful refresh.
     *
     * @return AuthResponse with new token pair
     * @throws Exception on network errors or invalid/expired refresh token
     */
    suspend fun refresh(refreshToken: RefreshToken): AuthResponse {
        val client = createClient(requireServerUrl())
        try {
            val response: ApiResponse<AuthResponse> =
                client
                    .post("/api/v1/auth/refresh") {
                        setBody(RefreshRequest(refreshToken.value))
                    }.body()

            return when (val result = response.toResult()) {
                is Success -> result.data
                is Failure -> throw result.exception
            }
        } finally {
            client.close()
        }
    }

    /**
     * Logout and invalidate current session.
     *
     * Server invalidates both access and refresh tokens.
     * Always succeeds even if tokens are already invalid.
     */
    suspend fun logout(sessionId: String) {
        val client = createClient(requireServerUrl())
        try {
            client.post("/api/v1/auth/logout") {
                setBody(LogoutRequest(sessionId))
            }
        } finally {
            client.close()
        }
    }
}

// Request DTOs

@Serializable
private data class SetupRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
)

@Serializable
private data class LoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("device_info") val deviceInfo: DeviceInfo,
)

@Serializable
private data class DeviceInfo(
    @SerialName("device_type") val deviceType: String,
    @SerialName("platform") val platform: String,
    @SerialName("platform_version") val platformVersion: String,
    @SerialName("client_name") val clientName: String,
    @SerialName("client_version") val clientVersion: String,
    @SerialName("client_build") val clientBuild: String = "",
    @SerialName("device_name") val deviceName: String = "",
    @SerialName("device_model") val deviceModel: String = "",
    @SerialName("browser_name") val browserName: String = "",
    @SerialName("browser_version") val browserVersion: String = "",
)

@Serializable
private data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("device_info") val deviceInfo: DeviceInfo? = null,
)

@Serializable
private data class LogoutRequest(
    @SerialName("session_id") val sessionId: String,
)

// Response DTOs

/**
 * Authentication response from server.
 * Contains new tokens and user/session information.
 */
@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("user") val user: AuthUser,
) {
    // Convenience accessors for common user fields
    val userId: String get() = user.id
    val email: String get() = user.email
    val displayName: String get() = user.displayName
    val isRoot: Boolean get() = user.isRoot
}

/**
 * User information included in authentication responses.
 */
@Serializable
data class AuthUser(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("is_root") val isRoot: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("last_login_at") val lastLoginAt: String,
)
