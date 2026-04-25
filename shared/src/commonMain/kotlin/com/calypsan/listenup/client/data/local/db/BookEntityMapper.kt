package com.calypsan.listenup.client.data.local.db

import com.calypsan.listenup.client.core.ContributorId
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.BookContributor
import com.calypsan.listenup.client.domain.model.BookDetail
import com.calypsan.listenup.client.domain.model.BookListItem
import com.calypsan.listenup.client.domain.model.BookSeries
import com.calypsan.listenup.client.domain.model.Genre
import com.calypsan.listenup.client.domain.model.Tag
import com.calypsan.listenup.client.domain.repository.ImageStorage

private const val ROLE_AUTHOR = "author"
private const val ROLE_NARRATOR = "narrator"

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
    authors: List<BookContributor> = emptyList(),
    narrators: List<BookContributor> = emptyList(),
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
        sortTitle = sortTitle,
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

/**
 * Extract contributors by role from cross-references, preserving creditedAs attributions.
 *
 * When contributors are merged, their original credited name may differ from their canonical name.
 * This function uses creditedAs from the cross-reference when available, falling back to the
 * contributor's canonical name.
 *
 * @param role The role to filter by (e.g., "author", "narrator")
 * @param contributorsById Lookup map of contributor ID to ContributorEntity
 * @return List of Contributors for the specified role
 */
fun List<BookContributorCrossRef>.extractByRole(
    role: String,
    contributorsById: Map<ContributorId, ContributorEntity>,
): List<BookContributor> =
    filter { it.role == role }
        .mapNotNull { crossRef ->
            contributorsById[crossRef.contributorId]?.let { entity ->
                BookContributor(entity.id.value, crossRef.creditedAs ?: entity.name)
            }
        }.distinctBy { it.id }

/**
 * Convert BookWithContributors to domain Book model with proper attribution handling.
 *
 * This is the canonical mapper for BookWithContributors → Book conversion.
 * Use this instead of writing custom mappers in ViewModels to ensure consistent behavior.
 *
 * Features:
 * - Preserves creditedAs names from contributor merges
 * - Resolves cover path from ImageStorage
 * - Maps series with sequence information
 *
 * @param imageStorage Storage for resolving cover image paths
 * @param includeSeries Whether to include series information (default: true)
 * @return Domain Book model
 */
fun BookWithContributors.toDomain(
    imageStorage: ImageStorage,
    includeSeries: Boolean = true,
): Book {
    val contributorsById = contributors.associateBy { it.id }

    val authors = contributorRoles.extractByRole(ROLE_AUTHOR, contributorsById)
    val narrators = contributorRoles.extractByRole(ROLE_NARRATOR, contributorsById)

    val bookSeriesList =
        if (includeSeries) {
            val seriesById = series.associateBy { it.id }
            seriesSequences.mapNotNull { seq ->
                seriesById[seq.seriesId]?.let { seriesEntity ->
                    BookSeries(
                        seriesId = seriesEntity.id.value,
                        seriesName = seriesEntity.name,
                        sequence = seq.sequence,
                    )
                }
            }
        } else {
            emptyList()
        }

    return Book(
        id = book.id,
        title = book.title,
        sortTitle = book.sortTitle,
        subtitle = book.subtitle,
        authors = authors,
        narrators = narrators,
        duration = book.totalDuration,
        coverPath = if (imageStorage.exists(book.id)) imageStorage.getCoverPath(book.id) else null,
        coverBlurHash = book.coverBlurHash,
        dominantColor = book.dominantColor,
        darkMutedColor = book.darkMutedColor,
        vibrantColor = book.vibrantColor,
        addedAt = book.createdAt,
        updatedAt = book.updatedAt,
        description = book.description,
        genres = emptyList(), // Loaded on-demand when editing
        tags = emptyList(), // Loaded on-demand when editing
        series = bookSeriesList,
        publishYear = book.publishYear,
        publisher = book.publisher,
        language = book.language,
        isbn = book.isbn,
        asin = book.asin,
        abridged = book.abridged,
        rating = null,
    )
}

/**
 * Convert BookWithContributors to domain BookListItem for list/shelf/home surfaces.
 *
 * Returns the list-shaped projection — no genres, tags, or allContributors. Use
 * [toDetail] when those fields are required.
 */
fun BookWithContributors.toListItem(imageStorage: ImageStorage): BookListItem {
    val contributorsById = contributors.associateBy { it.id }

    val authors = contributorRoles.extractByRole(ROLE_AUTHOR, contributorsById)
    val narrators = contributorRoles.extractByRole(ROLE_NARRATOR, contributorsById)

    val seriesById = series.associateBy { it.id }
    val bookSeriesList =
        seriesSequences.mapNotNull { seq ->
            seriesById[seq.seriesId]?.let { seriesEntity ->
                BookSeries(
                    seriesId = seriesEntity.id.value,
                    seriesName = seriesEntity.name,
                    sequence = seq.sequence,
                )
            }
        }

    return BookListItem(
        id = book.id,
        title = book.title,
        sortTitle = book.sortTitle,
        subtitle = book.subtitle,
        authors = authors,
        narrators = narrators,
        duration = book.totalDuration,
        coverPath = if (imageStorage.exists(book.id)) imageStorage.getCoverPath(book.id) else null,
        coverBlurHash = book.coverBlurHash,
        dominantColor = book.dominantColor,
        darkMutedColor = book.darkMutedColor,
        vibrantColor = book.vibrantColor,
        addedAt = book.createdAt,
        updatedAt = book.updatedAt,
        description = book.description,
        series = bookSeriesList,
        publishYear = book.publishYear,
        publisher = book.publisher,
        language = book.language,
        isbn = book.isbn,
        asin = book.asin,
        abridged = book.abridged,
        rating = null,
    )
}

/**
 * Convert BookWithContributors to domain BookDetail for the detail screen.
 *
 * Computes [BookDetail.allContributors] via dedup-and-role-aggregation — same
 * algorithm as the (deleted in Task 12) private mapper in BookRepositoryImpl.
 * Genres and tags are loaded externally and passed through.
 */
fun BookWithContributors.toDetail(
    imageStorage: ImageStorage,
    genres: List<Genre>,
    tags: List<Tag>,
): BookDetail {
    val contributorsById = contributors.associateBy { it.id }

    val authors = contributorRoles.extractByRole(ROLE_AUTHOR, contributorsById)
    val narrators = contributorRoles.extractByRole(ROLE_NARRATOR, contributorsById)

    // allContributors: dedupe by id, group all roles, prefer creditedAs name.
    val rolesByContributorId = contributorRoles.groupBy({ it.contributorId }, { it.role })
    val creditedAsByContributorId = contributorRoles.associate { it.contributorId to it.creditedAs }
    val allContributors =
        contributors
            .distinctBy { it.id }
            .map { entity ->
                BookContributor(
                    id = entity.id.value,
                    name = creditedAsByContributorId[entity.id] ?: entity.name,
                    roles = rolesByContributorId[entity.id] ?: emptyList(),
                )
            }

    val seriesById = series.associateBy { it.id }
    val bookSeriesList =
        seriesSequences.mapNotNull { seq ->
            seriesById[seq.seriesId]?.let { seriesEntity ->
                BookSeries(
                    seriesId = seriesEntity.id.value,
                    seriesName = seriesEntity.name,
                    sequence = seq.sequence,
                )
            }
        }

    return BookDetail(
        id = book.id,
        title = book.title,
        sortTitle = book.sortTitle,
        subtitle = book.subtitle,
        authors = authors,
        narrators = narrators,
        duration = book.totalDuration,
        coverPath = if (imageStorage.exists(book.id)) imageStorage.getCoverPath(book.id) else null,
        coverBlurHash = book.coverBlurHash,
        dominantColor = book.dominantColor,
        darkMutedColor = book.darkMutedColor,
        vibrantColor = book.vibrantColor,
        addedAt = book.createdAt,
        updatedAt = book.updatedAt,
        description = book.description,
        series = bookSeriesList,
        publishYear = book.publishYear,
        publisher = book.publisher,
        language = book.language,
        isbn = book.isbn,
        asin = book.asin,
        abridged = book.abridged,
        rating = null,
        allContributors = allContributors,
        genres = genres,
        tags = tags,
    )
}
