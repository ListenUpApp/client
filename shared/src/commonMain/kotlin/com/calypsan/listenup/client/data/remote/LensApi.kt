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
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contract for lens API operations.
 * All methods require authentication.
 */
interface LensApiContract {
    /**
     * Get all lenses owned by the current user.
     */
    suspend fun getMyLenses(): List<LensResponse>

    /**
     * Discover lenses from other users containing accessible books.
     * Returns lenses grouped by owner.
     */
    suspend fun discoverLenses(): List<UserLensesResponse>

    /**
     * Create a new lens.
     */
    suspend fun createLens(name: String, description: String?): LensResponse

    /**
     * Get a lens by ID with its books.
     */
    suspend fun getLens(lensId: String): LensDetailResponse

    /**
     * Update a lens (owner only).
     */
    suspend fun updateLens(lensId: String, name: String, description: String?): LensResponse

    /**
     * Delete a lens (owner only).
     */
    suspend fun deleteLens(lensId: String)

    /**
     * Add books to a lens (owner only).
     */
    suspend fun addBooks(lensId: String, bookIds: List<String>)

    /**
     * Remove a book from a lens (owner only).
     */
    suspend fun removeBook(lensId: String, bookId: String)
}

/**
 * API client for lens operations.
 *
 * Requires authentication via ApiClientFactory.
 */
class LensApi(
    private val clientFactory: ApiClientFactory,
) : LensApiContract {
    override suspend fun getMyLenses(): List<LensResponse> {
        val client = clientFactory.getClient()
        val response: ApiResponse<ListLensesResponse> =
            client.get("/api/v1/lenses").body()

        return when (val result = response.toResult()) {
            is Success -> result.data.lenses
            is Failure -> throw result.exception
        }
    }

    override suspend fun discoverLenses(): List<UserLensesResponse> {
        val client = clientFactory.getClient()
        val response: ApiResponse<DiscoverLensesResponse> =
            client.get("/api/v1/lenses/discover").body()

        return when (val result = response.toResult()) {
            is Success -> result.data.users
            is Failure -> throw result.exception
        }
    }

    override suspend fun createLens(name: String, description: String?): LensResponse {
        val client = clientFactory.getClient()
        val response: ApiResponse<LensResponse> =
            client
                .post("/api/v1/lenses") {
                    setBody(CreateLensRequest(name, description ?: ""))
                }.body()

        return when (val result = response.toResult()) {
            is Success -> result.data
            is Failure -> throw result.exception
        }
    }

    override suspend fun getLens(lensId: String): LensDetailResponse {
        val client = clientFactory.getClient()
        val response: ApiResponse<LensDetailResponse> =
            client.get("/api/v1/lenses/$lensId").body()

        return when (val result = response.toResult()) {
            is Success -> result.data
            is Failure -> throw result.exception
        }
    }

    override suspend fun updateLens(lensId: String, name: String, description: String?): LensResponse {
        val client = clientFactory.getClient()
        val response: ApiResponse<LensResponse> =
            client
                .patch("/api/v1/lenses/$lensId") {
                    setBody(UpdateLensRequest(name, description ?: ""))
                }.body()

        return when (val result = response.toResult()) {
            is Success -> result.data
            is Failure -> throw result.exception
        }
    }

    override suspend fun deleteLens(lensId: String) {
        val client = clientFactory.getClient()
        val response = client.delete("/api/v1/lenses/$lensId")

        if (!response.status.isSuccess()) {
            val errorResponse: ApiResponse<Unit> = response.body()
            when (val result = errorResponse.toResult()) {
                is Success -> { /* Shouldn't happen */ }
                is Failure -> throw result.exception
            }
        }
    }

    override suspend fun addBooks(lensId: String, bookIds: List<String>) {
        val client = clientFactory.getClient()
        val response = client.post("/api/v1/lenses/$lensId/books") {
            setBody(AddBooksToLensRequest(bookIds))
        }

        if (!response.status.isSuccess()) {
            val errorResponse: ApiResponse<Unit> = response.body()
            when (val result = errorResponse.toResult()) {
                is Success -> { /* Shouldn't happen */ }
                is Failure -> throw result.exception
            }
        }
    }

    override suspend fun removeBook(lensId: String, bookId: String) {
        val client = clientFactory.getClient()
        val response = client.delete("/api/v1/lenses/$lensId/books/$bookId")

        if (!response.status.isSuccess()) {
            val errorResponse: ApiResponse<Unit> = response.body()
            when (val result = errorResponse.toResult()) {
                is Success -> { /* Shouldn't happen */ }
                is Failure -> throw result.exception
            }
        }
    }
}

// ========== Response Models ==========

/**
 * Lens owner information.
 */
@Serializable
data class LensOwnerResponse(
    @SerialName("id") val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_color") val avatarColor: String,
)

/**
 * Lens summary response.
 */
@Serializable
data class LensResponse(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("owner") val owner: LensOwnerResponse,
    @SerialName("book_count") val bookCount: Int,
    @SerialName("total_duration") val totalDuration: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

/**
 * A book within a lens.
 */
@Serializable
data class LensBookResponse(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("author_names") val authorNames: List<String>,
    @SerialName("cover_path") val coverPath: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Long,
)

/**
 * Lens detail response with books.
 */
@Serializable
data class LensDetailResponse(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("owner") val owner: LensOwnerResponse,
    @SerialName("book_count") val bookCount: Int,
    @SerialName("total_duration") val totalDuration: Long,
    @SerialName("books") val books: List<LensBookResponse>,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

/**
 * A user's lenses in discover response.
 */
@Serializable
data class UserLensesResponse(
    @SerialName("user") val user: LensOwnerResponse,
    @SerialName("lenses") val lenses: List<LensResponse>,
)

// ========== Request Models ==========

/**
 * Request to create a lens.
 */
@Serializable
private data class CreateLensRequest(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
)

/**
 * Request to update a lens.
 */
@Serializable
private data class UpdateLensRequest(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
)

/**
 * Request to add books to a lens.
 */
@Serializable
private data class AddBooksToLensRequest(
    @SerialName("book_ids") val bookIds: List<String>,
)

// ========== Internal Response Wrappers ==========

/**
 * Wrapper for list lenses response.
 */
@Serializable
private data class ListLensesResponse(
    @SerialName("lenses") val lenses: List<LensResponse>,
)

/**
 * Wrapper for discover lenses response.
 */
@Serializable
private data class DiscoverLensesResponse(
    @SerialName("users") val users: List<UserLensesResponse>,
)
