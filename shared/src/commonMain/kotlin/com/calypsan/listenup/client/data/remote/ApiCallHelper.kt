package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.remote.model.ApiException
import com.calypsan.listenup.client.data.remote.model.ApiResponse

/**
 * Extract data from ApiResponse or throw an exception.
 *
 * This extension consolidates the common pattern:
 * ```
 * val response: ApiResponse<T> = client.get(url).body()
 * if (!response.success || response.data == null) {
 *     throw SomeException(response.error ?: "Failed")
 * }
 * return response.data
 * ```
 *
 * Into:
 * ```
 * val response: ApiResponse<T> = client.get(url).body()
 * return response.dataOrThrow("Failed to fetch data")
 * ```
 *
 * @param errorMessage Fallback message if server provides no error
 * @return The data if successful
 * @throws ApiException if the response indicates failure
 */
fun <T> ApiResponse<T>.dataOrThrow(errorMessage: String): T =
    when (val result = toResult()) {
        is Success -> result.data ?: throw ApiException(message = errorMessage)
        is Failure -> throw result.exception ?: ApiException(message = result.message)
    }

/**
 * Extract data from ApiResponse or throw with a custom exception.
 *
 * Useful when APIs have domain-specific exception types:
 * ```
 * return response.dataOrThrow { msg -> GenreApiException(msg) }
 * ```
 *
 * @param exceptionFactory Creates the exception to throw on failure
 * @return The data if successful
 */
inline fun <T, E : Exception> ApiResponse<T>.dataOrThrow(exceptionFactory: (String) -> E): T =
    when (val result = toResult()) {
        is Success -> result.data ?: throw exceptionFactory("Response data was null")
        is Failure -> throw exceptionFactory(result.message)
    }

/**
 * Validate API response without extracting data.
 *
 * Useful for POST/DELETE operations that don't return data:
 * ```
 * val response: ApiResponse<Unit> = client.post(url).body()
 * response.validateOrThrow("Failed to update")
 * ```
 *
 * @param errorMessage Fallback message if server provides no error
 * @throws ApiException if the response indicates failure
 */
fun <T> ApiResponse<T>.validateOrThrow(errorMessage: String) {
    when (val result = toResult()) {
        is Success -> { /* Success - nothing to do */ }

        is Failure -> {
            throw ApiException(message = result.message.ifEmpty { errorMessage })
        }
    }
}
