package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.getOrThrow
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.remote.model.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val logger = KotlinLogging.logger {}

/**
 * API client for user preferences.
 *
 * Handles fetching and updating user preferences that sync across devices.
 * Uses ApiClientFactory to obtain authenticated HttpClient at call time.
 *
 * @property clientFactory Factory for creating authenticated HttpClient
 */
class UserPreferencesApi(
    private val clientFactory: ApiClientFactory,
) : UserPreferencesApiContract {
    /**
     * Get user preferences from the server.
     *
     * Endpoint: GET /api/v1/user/preferences
     * Auth: Required
     *
     * @return Result containing user preferences or error
     */
    override suspend fun getPreferences(): Result<UserPreferencesResponse> =
        suspendRunCatching {
            logger.debug { "Fetching user preferences" }
            val client = clientFactory.getClient()
            val response: ApiResponse<UserPreferencesApiResponse> =
                client.get("/api/v1/user/preferences").body()
            response.toResult().getOrThrow().toDomain()
        }

    /**
     * Update user preferences on the server.
     *
     * Endpoint: PUT /api/v1/user/preferences
     * Auth: Required
     *
     * @param request The preferences to update
     * @return Result containing updated preferences or error
     */
    override suspend fun updatePreferences(request: UserPreferencesRequest): Result<UserPreferencesResponse> =
        suspendRunCatching {
            logger.debug { "Updating user preferences: defaultPlaybackSpeed=${request.defaultPlaybackSpeed}" }
            val client = clientFactory.getClient()
            val apiRequest = UserPreferencesApiRequest(
                defaultPlaybackSpeed = request.defaultPlaybackSpeed,
            )
            val response: ApiResponse<UserPreferencesApiResponse> =
                client
                    .put("/api/v1/user/preferences") {
                        contentType(ContentType.Application.Json)
                        setBody(apiRequest)
                    }.body()
            response.toResult().getOrThrow().toDomain()
        }
}

/**
 * Internal API response model for user preferences.
 */
@Serializable
internal data class UserPreferencesApiResponse(
    @SerialName("default_playback_speed")
    val defaultPlaybackSpeed: Float,
) {
    fun toDomain(): UserPreferencesResponse =
        UserPreferencesResponse(
            defaultPlaybackSpeed = defaultPlaybackSpeed,
        )
}

/**
 * Internal API request model for user preferences.
 */
@Serializable
internal data class UserPreferencesApiRequest(
    @SerialName("default_playback_speed")
    val defaultPlaybackSpeed: Float?,
)
