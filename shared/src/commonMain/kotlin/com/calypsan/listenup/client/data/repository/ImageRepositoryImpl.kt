package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.map
import com.calypsan.listenup.client.domain.repository.ImageStorage
import com.calypsan.listenup.client.data.remote.ImageApiContract
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.domain.repository.ImageRepository

/**
 * Implementation of ImageRepository that delegates to data layer components.
 *
 * This adapter bridges the domain ImageRepository interface to the existing
 * data layer implementations:
 * - ImageDownloaderContract for download operations
 * - ImageStorage for local file operations
 * - ImageApiContract for upload operations
 */
class ImageRepositoryImpl(
    private val imageDownloader: ImageDownloaderContract,
    private val imageStorage: ImageStorage,
    private val imageApi: ImageApiContract,
) : ImageRepository {
    // ========== Book Cover Operations ==========

    override suspend fun deleteBookCover(bookId: BookId): Result<Unit> =
        imageDownloader.deleteCover(bookId)

    override suspend fun downloadBookCover(bookId: BookId): Result<Boolean> =
        imageDownloader.downloadCover(bookId)

    override suspend fun saveBookCoverStaging(
        bookId: BookId,
        imageData: ByteArray,
    ): Result<Unit> =
        imageStorage.saveCoverStaging(bookId, imageData)

    override fun getBookCoverStagingPath(bookId: BookId): String =
        imageStorage.getCoverStagingPath(bookId)

    override suspend fun deleteBookCoverStaging(bookId: BookId): Result<Unit> =
        imageStorage.deleteCoverStaging(bookId)

    override suspend fun commitBookCoverStaging(bookId: BookId): Result<Unit> =
        imageStorage.commitCoverStaging(bookId)

    override suspend fun uploadBookCover(
        bookId: String,
        imageData: ByteArray,
        filename: String,
    ): Result<String> =
        imageApi.uploadBookCover(bookId, imageData, filename).map { it.imageUrl }

    // ========== Series Cover Operations ==========

    override suspend fun saveSeriesCoverStaging(
        seriesId: String,
        imageData: ByteArray,
    ): Result<Unit> =
        imageStorage.saveSeriesCoverStaging(seriesId, imageData)

    override fun getSeriesCoverStagingPath(seriesId: String): String =
        imageStorage.getSeriesCoverStagingPath(seriesId)

    override suspend fun deleteSeriesCoverStaging(seriesId: String): Result<Unit> =
        imageStorage.deleteSeriesCoverStaging(seriesId)

    override suspend fun commitSeriesCoverStaging(seriesId: String): Result<Unit> =
        imageStorage.commitSeriesCoverStaging(seriesId)

    override suspend fun uploadSeriesCover(
        seriesId: String,
        imageData: ByteArray,
        filename: String,
    ): Result<String> =
        imageApi.uploadSeriesCover(seriesId, imageData, filename).map { it.imageUrl }

    // ========== Contributor Image Operations ==========

    override suspend fun downloadContributorImage(contributorId: String): Result<ByteArray> =
        imageApi.downloadContributorImage(contributorId)

    override suspend fun saveContributorImage(
        contributorId: String,
        imageData: ByteArray,
    ): Result<Unit> =
        imageStorage.saveContributorImage(contributorId, imageData)

    override fun getContributorImagePath(contributorId: String): String =
        imageStorage.getContributorImagePath(contributorId)

    override suspend fun uploadContributorImage(
        contributorId: String,
        imageData: ByteArray,
        filename: String,
    ): Result<String> =
        imageApi.uploadContributorImage(contributorId, imageData, filename).map { it.imageUrl }

    // ========== Book Cover Path Operations ==========

    override fun bookCoverExists(bookId: BookId): Boolean =
        imageStorage.exists(bookId)

    override fun getBookCoverPath(bookId: BookId): String =
        imageStorage.getCoverPath(bookId)

    // ========== Series Cover Path Operations ==========

    override fun seriesCoverExists(seriesId: String): Boolean =
        imageStorage.seriesCoverExists(seriesId)

    override fun getSeriesCoverPath(seriesId: String): String =
        imageStorage.getSeriesCoverPath(seriesId)

    // ========== User Avatar Operations ==========

    override fun userAvatarExists(userId: String): Boolean =
        imageStorage.userAvatarExists(userId)

    override fun getUserAvatarPath(userId: String): String =
        imageStorage.getUserAvatarPath(userId)

    override suspend fun downloadUserAvatar(
        userId: String,
        forceRefresh: Boolean,
    ): Result<Boolean> =
        imageDownloader.downloadUserAvatar(userId, forceRefresh)
}
