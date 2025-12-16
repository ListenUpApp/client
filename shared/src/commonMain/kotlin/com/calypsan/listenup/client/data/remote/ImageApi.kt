package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.remote.model.ApiResponse
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API client for image operations (download and upload).
 *
 * Handles communication with image endpoints:
 * - Downloads JPEG cover images from server
 * - Uploads book covers and contributor photos
 * - Uses multipart form data for uploads
 *
 * Uses ApiClientFactory to obtain authenticated HttpClient at call time,
 * avoiding runBlocking during dependency injection initialization.
 *
 * @property clientFactory Factory for creating authenticated HttpClient
 * @property settingsRepository For constructing full image URLs
 */
class ImageApi(
    private val clientFactory: ApiClientFactory,
    private val settingsRepository: SettingsRepositoryContract,
) : ImageApiContract {
    /**
     * Constructs a full URL from a relative path returned by the server.
     * Server returns paths like "/api/v1/contributors/xxx/image" but Coil
     * needs absolute URLs like "http://server:port/api/v1/contributors/xxx/image".
     */
    private suspend fun buildFullUrl(relativePath: String): String {
        val serverUrl = settingsRepository.getServerUrl()?.value ?: ""
        val path = relativePath.trimStart('/')
        return "$serverUrl/$path"
    }

    /**
     * Download cover image for a book.
     *
     * Returns raw JPEG bytes which can be saved to local storage
     * via ImageStorage. Returns failure if cover doesn't exist (404).
     *
     * Endpoint: GET /api/v1/covers/{bookId}
     * Auth: Not required (public access)
     * Response: image/jpeg (raw bytes)
     *
     * @param bookId Unique identifier for the book
     * @return Result containing image bytes or error
     */
    override suspend fun downloadCover(bookId: BookId): Result<ByteArray> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            client.get("/api/v1/covers/${bookId.value}").body<ByteArray>()
        }

    /**
     * Download profile image for a contributor.
     *
     * Returns raw image bytes which can be saved to local storage
     * via ImageStorage. Returns failure if image doesn't exist (404).
     *
     * Endpoint: GET /api/v1/contributors/{contributorId}/image
     * Auth: Not required (public access)
     * Response: image/jpeg (raw bytes)
     *
     * @param contributorId Unique identifier for the contributor
     * @return Result containing image bytes or error
     */
    override suspend fun downloadContributorImage(contributorId: String): Result<ByteArray> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            client.get("/api/v1/contributors/$contributorId/image").body<ByteArray>()
        }

    /**
     * Upload cover image for a book.
     *
     * Sends image as multipart form data with "file" field.
     * Server validates image format (JPEG, PNG, WebP, GIF) via magic bytes.
     *
     * Endpoint: PUT /api/v1/books/{bookId}/cover
     * Auth: Required (Bearer token)
     * Request: multipart/form-data with "file" field
     *
     * @param bookId Unique identifier for the book
     * @param imageData Raw image bytes
     * @param filename Original filename (used for content disposition)
     * @return Result containing the image URL or error
     */
    override suspend fun uploadBookCover(
        bookId: String,
        imageData: ByteArray,
        filename: String,
    ): Result<ImageUploadResponse> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            val response: ApiResponse<ImageUploadApiResponse> =
                client
                    .submitFormWithBinaryData(
                        url = "/api/v1/books/$bookId/cover",
                        formData =
                            formData {
                                append(
                                    "file",
                                    imageData,
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                                        append(HttpHeaders.ContentType, "image/*")
                                    },
                                )
                            },
                    ) {
                        method = io.ktor.http.HttpMethod.Put
                    }.body()

            when (val result = response.toResult()) {
                is Success -> {
                    val apiResponse = result.data
                    val relativeUrl = apiResponse.imageUrl ?: apiResponse.coverUrl ?: ""
                    ImageUploadResponse(imageUrl = buildFullUrl(relativeUrl))
                }

                is Failure -> {
                    throw result.exception
                }
            }
        }

    /**
     * Upload profile image for a contributor.
     *
     * Sends image as multipart form data with "file" field.
     * Server validates image format (JPEG, PNG, WebP, GIF) via magic bytes.
     *
     * Endpoint: PUT /api/v1/contributors/{contributorId}/image
     * Auth: Required (Bearer token)
     * Request: multipart/form-data with "file" field
     *
     * @param contributorId Unique identifier for the contributor
     * @param imageData Raw image bytes
     * @param filename Original filename (used for content disposition)
     * @return Result containing the image URL or error
     */
    override suspend fun uploadContributorImage(
        contributorId: String,
        imageData: ByteArray,
        filename: String,
    ): Result<ImageUploadResponse> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            val response: ApiResponse<ImageUploadApiResponse> =
                client
                    .submitFormWithBinaryData(
                        url = "/api/v1/contributors/$contributorId/image",
                        formData =
                            formData {
                                append(
                                    "file",
                                    imageData,
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                                        append(HttpHeaders.ContentType, "image/*")
                                    },
                                )
                            },
                    ) {
                        method = io.ktor.http.HttpMethod.Put
                    }.body()

            when (val result = response.toResult()) {
                is Success -> {
                    val apiResponse = result.data
                    val relativeUrl = apiResponse.imageUrl ?: apiResponse.coverUrl ?: ""
                    ImageUploadResponse(imageUrl = buildFullUrl(relativeUrl))
                }

                is Failure -> {
                    throw result.exception
                }
            }
        }

    /**
     * Download cover image for a series.
     *
     * Returns raw image bytes which can be saved to local storage
     * via ImageStorage. Returns failure if cover doesn't exist (404).
     *
     * Endpoint: GET /api/v1/series/{seriesId}/cover
     * Auth: Not required (public access)
     * Response: image/jpeg (raw bytes)
     *
     * @param seriesId Unique identifier for the series
     * @return Result containing image bytes or error
     */
    override suspend fun downloadSeriesCover(seriesId: String): Result<ByteArray> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            client.get("/api/v1/series/$seriesId/cover").body<ByteArray>()
        }

    /**
     * Upload cover image for a series.
     *
     * Sends image as multipart form data with "file" field.
     * Server validates image format (JPEG, PNG, WebP, GIF) via magic bytes.
     *
     * Endpoint: PUT /api/v1/series/{seriesId}/cover
     * Auth: Required (Bearer token)
     * Request: multipart/form-data with "file" field
     *
     * @param seriesId Unique identifier for the series
     * @param imageData Raw image bytes
     * @param filename Original filename (used for content disposition)
     * @return Result containing the image URL or error
     */
    override suspend fun uploadSeriesCover(
        seriesId: String,
        imageData: ByteArray,
        filename: String,
    ): Result<ImageUploadResponse> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            val response: ApiResponse<ImageUploadApiResponse> =
                client
                    .submitFormWithBinaryData(
                        url = "/api/v1/series/$seriesId/cover",
                        formData =
                            formData {
                                append(
                                    "file",
                                    imageData,
                                    Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                                        append(HttpHeaders.ContentType, "image/*")
                                    },
                                )
                            },
                    ) {
                        method = io.ktor.http.HttpMethod.Put
                    }.body()

            when (val result = response.toResult()) {
                is Success -> {
                    val apiResponse = result.data
                    val relativeUrl = apiResponse.imageUrl ?: apiResponse.coverUrl ?: ""
                    ImageUploadResponse(imageUrl = buildFullUrl(relativeUrl))
                }

                is Failure -> {
                    throw result.exception
                }
            }
        }

    /**
     * Delete cover image for a series.
     *
     * Removes the cover image from the server.
     *
     * Endpoint: DELETE /api/v1/series/{seriesId}/cover
     * Auth: Required (Bearer token)
     * Response: 204 No Content
     *
     * @param seriesId Unique identifier for the series
     * @return Result with Unit on success or error
     */
    override suspend fun deleteSeriesCover(seriesId: String): Result<Unit> =
        suspendRunCatching {
            val client = clientFactory.getClient()
            client.delete("/api/v1/series/$seriesId/cover")
            Unit
        }
}

/**
 * API response for image upload.
 */
@Serializable
private data class ImageUploadApiResponse(
    @SerialName("cover_url")
    val coverUrl: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
)
