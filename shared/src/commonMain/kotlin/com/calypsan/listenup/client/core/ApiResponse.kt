package com.calypsan.listenup.client.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Standard API response envelope used by ListenUp server.
 *
 * All API responses from the server are wrapped in this envelope structure:
 * - Success responses: { "success": true, "data": {...} }
 * - Error responses: { "success": false, "error": "...", "message": "..." }
 *
 * This matches the server's response.Envelope type from
 * internal/http/response/response.go.
 *
 * @param T The type of data contained in successful responses
 */
@Serializable
data class ApiResponse<T>(
    @SerialName("success")
    val success: Boolean,

    @SerialName("data")
    val data: T? = null,

    @SerialName("error")
    val error: String? = null,

    @SerialName("message")
    val message: String? = null
)
