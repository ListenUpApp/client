package com.calypsan.listenup.client.data.local.db

import com.calypsan.listenup.client.core.ContributorId
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
