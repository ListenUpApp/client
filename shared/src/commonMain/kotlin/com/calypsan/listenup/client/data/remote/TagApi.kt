package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.domain.model.Tag
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * API client for global tag operations.
 *
 * Tags are community-wide content descriptors that any user can apply
 * to books they have access to.
 *
 * @property clientFactory Factory for creating authenticated HttpClient
 */
class TagApi(
    private val clientFactory: ApiClientFactory,
) : TagApiContract {
    /**
     * Get all global tags ordered by popularity.
     *
     * Endpoint: GET /api/v1/tags
     */
    override suspend fun listTags(): List<Tag> {
        val client = clientFactory.getClient()
        val response: ListTagsResponse = client.get("/api/v1/tags").body()
        return response.tags.map { it.toDomain() }
    }

    /**
     * Get a tag by its slug.
     *
     * Endpoint: GET /api/v1/tags/{slug}
     *
     * @param slug The tag slug
     * @return The tag, or null if not found
     */
    override suspend fun getTagBySlug(slug: String): Tag? {
        val client = clientFactory.getClient()
        val httpResponse: HttpResponse = client.get("/api/v1/tags/$slug")
        return if (httpResponse.status.isSuccess()) {
            httpResponse.body<TagResponse>().toDomain()
        } else {
            null
        }
    }

    /**
     * Get tags for a specific book.
     *
     * Endpoint: GET /api/v1/books/{bookId}/tags
     *
     * @param bookId The book ID to get tags for
     */
    override suspend fun getBookTags(bookId: String): List<Tag> {
        val client = clientFactory.getClient()
        val response: GetBookTagsResponse = client.get("/api/v1/books/$bookId/tags").body()
        return response.tags.map { it.toDomain() }
    }

    /**
     * Add a tag to a book. Creates the tag if it doesn't exist.
     *
     * Endpoint: POST /api/v1/books/{bookId}/tags
     *
     * @param bookId The book to tag
     * @param rawInput The tag text (will be normalized to slug by server)
     * @return The tag that was added or created
     */
    override suspend fun addTagToBook(
        bookId: String,
        rawInput: String,
    ): Tag {
        val client = clientFactory.getClient()
        val response: TagResponse =
            client
                .post("/api/v1/books/$bookId/tags") {
                    contentType(ContentType.Application.Json)
                    setBody(AddTagRequest(tag = rawInput))
                }.body()
        return response.toDomain()
    }

    /**
     * Remove a tag from a book.
     *
     * Endpoint: DELETE /api/v1/books/{bookId}/tags/{slug}
     *
     * @param bookId The book to untag
     * @param slug The tag slug to remove
     */
    override suspend fun removeTagFromBook(
        bookId: String,
        slug: String,
    ) {
        val client = clientFactory.getClient()
        client.delete("/api/v1/books/$bookId/tags/$slug")
    }
}

// === Response DTOs ===

/**
 * Response wrapper for listing tags.
 */
@Serializable
internal data class ListTagsResponse(
    val tags: List<TagResponse>,
)

/**
 * Response wrapper for getting book tags.
 */
@Serializable
internal data class GetBookTagsResponse(
    val tags: List<TagResponse>,
)

/**
 * Tag API response DTO.
 */
@Serializable
internal data class TagResponse(
    val id: String,
    val slug: String,
    @SerialName("book_count")
    val bookCount: Int = 0,
    @SerialName("created_at")
    val createdAt: String? = null,
) {
    fun toDomain() =
        Tag(
            id = id,
            slug = slug,
            bookCount = bookCount,
            createdAt = createdAt?.let { Instant.parse(it) },
        )
}

// === Request DTOs ===

@Serializable
internal data class AddTagRequest(
    val tag: String,
)
