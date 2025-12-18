package com.calypsan.listenup.client.data.local.db

import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Contributor

/**
 * Extension function to convert BookEntity to domain Book model.
 *
 * Uses ImageStorage to resolve local cover file paths. If a cover exists
 * locally, provides the absolute file path for Coil to load. Otherwise,
 * returns null for coverPath.
 *
 * @param imageStorage Storage for resolving local cover paths
 * @param authors List of author contributors
 * @param narrators List of narrator contributors
 * @return Domain Book model for UI consumption
 */
fun BookEntity.toDomain(
    imageStorage: ImageStorage,
    authors: List<Contributor> = emptyList(),
    narrators: List<Contributor> = emptyList(),
): Book {
    val coverPath =
        if (imageStorage.exists(id)) {
            imageStorage.getCoverPath(id)
        } else {
            null
        }

    return Book(
        id = id,
        title = title,
        subtitle = subtitle,
        authors = authors,
        narrators = narrators,
        duration = totalDuration,
        coverPath = coverPath,
        coverBlurHash = coverBlurHash,
        addedAt = createdAt,
        updatedAt = updatedAt,
        description = description,
        genres = emptyList(), // Loaded on-demand when editing
        tags = emptyList(), // Loaded on-demand when editing
        // Series loaded via junction table - not available in this simple mapper
        series = emptyList(),
        publishYear = publishYear,
        rating = null,
    )
}
