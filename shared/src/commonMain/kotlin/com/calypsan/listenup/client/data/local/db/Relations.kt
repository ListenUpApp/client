package com.calypsan.listenup.client.data.local.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.Relation

/**
 * Cross-reference entity for the many-to-many relationship between Books and Contributors.
 *
 * A contributor (e.g., Stephen King) can be associated with multiple books,
 * and a book can have multiple contributors (e.g., Author + Narrator).
 *
 * @property bookId Foreign key to the book
 * @property contributorId Foreign key to the contributor
 * @property role The role of the contributor for this specific book (e.g., "author", "narrator")
 */
@Entity(
    tableName = "book_contributors",
    primaryKeys = ["bookId", "contributorId", "role"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE // If book is deleted, remove relation
        ),
        ForeignKey(
            entity = ContributorEntity::class,
            parentColumns = ["id"],
            childColumns = ["contributorId"],
            onDelete = ForeignKey.CASCADE // If contributor is deleted, remove relation
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["contributorId"])
    ]
)
data class BookContributorCrossRef(
    val bookId: BookId,
    val contributorId: String,
    val role: String // "author", "narrator", etc.
)

/**
 * Relation POJO for loading a book with all its contributors in a single query.
 *
 * This eliminates the N+1 query problem by using Room's @Relation annotation
 * to batch-load all contributors for all books in a single additional query.
 *
 * Contributors are loaded with their roles via the junction table, allowing
 * filtering by role (author, narrator, etc.) in the repository layer.
 */
data class BookWithContributors(
    @Embedded val book: BookEntity,

    @Relation(
        entity = ContributorEntity::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BookContributorCrossRef::class,
            parentColumn = "bookId",
            entityColumn = "contributorId"
        )
    )
    val contributors: List<ContributorEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "bookId"
    )
    val contributorRoles: List<BookContributorCrossRef>
)

/**
 * Data class for series with aggregated book count.
 *
 * Used by queries that join series with books to count related books.
 * Room can directly map query results with COUNT(*) to this class.
 */
data class SeriesWithBookCount(
    @Embedded val series: SeriesEntity,
    val bookCount: Int
)

/**
 * Data class for contributor with aggregated book count.
 *
 * Used by queries that join contributors with book_contributors
 * to count how many books a contributor is associated with.
 */
data class ContributorWithBookCount(
    @Embedded val contributor: ContributorEntity,
    val bookCount: Int
)

/**
 * Relation POJO for loading a series with all its books in a single query.
 *
 * Uses Room's @Relation to batch-load all books for each series,
 * avoiding N+1 query problems when displaying series with cover stacks.
 */
data class SeriesWithBooks(
    @Embedded val series: SeriesEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "seriesId"
    )
    val books: List<BookEntity>
)
