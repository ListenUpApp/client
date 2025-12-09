package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.domain.model.Tag
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API client for tag operations.
 *
 * Tags are user-scoped and used for personal organization of books.
 * Each user has their own set of tags that can be applied to any book.
 *
 * @property clientFactory Factory for creating authenticated HttpClient
 */
class TagApi(
    private val clientFactory: ApiClientFactory
) {

    /**
     * Get all tags for the current user.
     *
     * Endpoint: GET /api/v1/tags/
     */
    suspend fun getUserTags(): List<Tag> {
        val client = clientFactory.getClient()
        val response: List<TagResponse> = client.get("/api/v1/tags/").body()
        return response.map { it.toDomain() }
    }

    /**
     * Get tags for a specific book.
     *
     * Endpoint: GET /api/v1/books/{bookId}/tags
     *
     * @param bookId The book ID to get tags for
     */
    suspend fun getBookTags(bookId: String): List<Tag> {
        val client = clientFactory.getClient()
        val response: List<TagResponse> = client.get("/api/v1/books/$bookId/tags").body()
        return response.map { it.toDomain() }
    }

    /**
     * Add a tag to a book.
     *
     * Endpoint: POST /api/v1/books/{bookId}/tags
     *
     * @param bookId The book to tag
     * @param tagId The tag to apply
     */
    suspend fun addTagToBook(bookId: String, tagId: String) {
        val client = clientFactory.getClient()
        client.post("/api/v1/books/$bookId/tags") {
            contentType(ContentType.Application.Json)
            setBody(AddTagRequest(tagId = tagId))
        }
    }

    /**
     * Remove a tag from a book.
     *
     * Endpoint: DELETE /api/v1/books/{bookId}/tags/{tagId}
     *
     * @param bookId The book to untag
     * @param tagId The tag to remove
     */
    suspend fun removeTagFromBook(bookId: String, tagId: String) {
        val client = clientFactory.getClient()
        client.delete("/api/v1/books/$bookId/tags/$tagId")
    }

    /**
     * Create a new tag.
     *
     * Endpoint: POST /api/v1/tags/
     *
     * @param name The display name for the new tag
     * @param color Optional hex color for the tag (e.g., "#FF5733")
     * @return The created tag
     */
    suspend fun createTag(name: String, color: String? = null): Tag {
        val client = clientFactory.getClient()
        val response: TagResponse = client.post("/api/v1/tags/") {
            contentType(ContentType.Application.Json)
            setBody(CreateTagRequest(name = name, color = color))
        }.body()
        return response.toDomain()
    }

    /**
     * Delete a tag.
     *
     * Endpoint: DELETE /api/v1/tags/{tagId}
     *
     * @param tagId The tag to delete
     */
    suspend fun deleteTag(tagId: String) {
        val client = clientFactory.getClient()
        client.delete("/api/v1/tags/$tagId")
    }
}

/**
 * Tag API response DTO.
 */
@Serializable
internal data class TagResponse(
    val id: String,
    val name: String,
    val slug: String,

    @SerialName("owner_id")
    val ownerId: String,

    val color: String? = null,

    @SerialName("book_count")
    val bookCount: Int = 0,

    @SerialName("created_at")
    val createdAt: Long,

    @SerialName("updated_at")
    val updatedAt: Long,

    @SerialName("deleted_at")
    val deletedAt: Long? = null
) {
    fun toDomain() = Tag(
        id = id,
        name = name,
        slug = slug,
        color = color,
        bookCount = bookCount
    )
}

@Serializable
internal data class AddTagRequest(
    @SerialName("tag_id")
    val tagId: String
)

@Serializable
internal data class CreateTagRequest(
    val name: String,
    val color: String? = null
)
