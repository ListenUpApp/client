package com.calypsan.listenup.client.data.local.db

import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.domain.model.Book

/**
 * Extension function to convert BookEntity to domain Book model.
 *
 * Uses ImageStorage to resolve local cover file paths. If a cover exists
 * locally, provides the absolute file path for Coil to load. Otherwise,
 * returns null for coverPath.
 *
 * Note: narrator field is not in BookEntity yet, so defaults to null.
 * This can be added when the server provides narrator information.
 *
 * @param imageStorage Storage for resolving local cover paths
 * @return Domain Book model for UI consumption
 */
fun BookEntity.toDomain(imageStorage: ImageStorage): Book {
    val coverPath = if (imageStorage.exists(id)) {
        imageStorage.getCoverPath(id)
    } else {
        null
    }

    return Book(
        id = id,
        title = title,
        author = author,
        narrator = null, // TODO: Add narrator field to BookEntity when available
        duration = totalDuration,
        coverPath = coverPath,
        addedAt = createdAt,
        updatedAt = updatedAt
    )
}
