package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.core.ServerUrl
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
 * All endpoints return new tokens on success, following the server's
 * token rotation pattern (15min access, 30d refresh).
 */
class AuthApi(private val serverUrl: ServerUrl) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = false
                isLenient = false
                ignoreUnknownKeys = true
            })
        }

        defaultRequest {
            url(serverUrl.value)
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Login with email and password.
     *
     * @return AuthResponse with tokens and user info on success
     * @throws Exception on network errors or invalid credentials
     */
    suspend fun login(email: String, password: String): AuthResponse {
        return client.post("/api/auth/login") {
            setBody(LoginRequest(email, password))
        }.body()
    }

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
        return client.post("/api/auth/refresh") {
            setBody(RefreshRequest(refreshToken.value))
        }.body()
    }

    /**
     * Logout and invalidate current session.
     *
     * Server invalidates both access and refresh tokens.
     * Always succeeds even if tokens are already invalid.
     */
    suspend fun logout(accessToken: AccessToken) {
        client.post("/api/auth/logout") {
            setBody(LogoutRequest(accessToken.value))
        }
    }

    /**
     * Close the HTTP client and release resources.
     * Call this when the API instance is no longer needed.
     */
    fun close() {
        client.close()
    }
}

// Request DTOs

@Serializable
private data class LoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String
)

@Serializable
private data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
private data class LogoutRequest(
    @SerialName("access_token") val accessToken: String
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
    @SerialName("user_id") val userId: String,
    @SerialName("email") val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("is_root") val isRoot: Boolean
)
