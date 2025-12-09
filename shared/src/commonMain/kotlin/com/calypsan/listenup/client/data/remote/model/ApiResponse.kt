package com.calypsan.listenup.client.data.remote.model

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Standard API response wrapper from the ListenUp server.
 *
 * All API endpoints return responses in this format, providing
 * consistent error handling across the application.
 */
@Serializable
data class ApiResponse<T>(
    @SerialName("success")
    val success: Boolean,
    @SerialName("data")
    val data: T? = null,
    @SerialName("error")
    val error: String? = null,
) {
    /**
     * Convert this API response to our domain Result type.
     *
     * This transformation happens at the data layer boundary, keeping
     * our domain layer free of API-specific concerns.
     */
    fun toResult(): Result<T> =
        if (success && data != null) {
            Success(data)
        } else {
            Failure(
                exception = ApiException(error ?: "Unknown API error"),
                message = error ?: "Unknown API error",
            )
        }
}

/**
 * Exception thrown when an API call fails.
 *
 * This wraps server-provided error messages in a typed exception
 * that can be handled appropriately by the presentation layer.
 */
class ApiException(
    message: String,
) : Exception(message)
