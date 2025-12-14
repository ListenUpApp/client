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
 * The `creditedAs` field preserves the original attribution name when an alias
 * is merged into a primary contributor. For example, when "Richard Bachman" is
 * merged into "Stephen King", books originally by Bachman keep creditedAs = "Richard Bachman".
 * This allows:
 * - Book detail page to show "by Richard Bachman" (original credit)
 * - Clicking the name navigates to Stephen King (the real contributor)
 * - Stephen King's page shows "The Running Man (as Richard Bachman)"
 *
 * @property bookId Foreign key to the book
 * @property contributorId Foreign key to the contributor
 * @property role The role of the contributor for this specific book (e.g., "author", "narrator")
 * @property creditedAs The name shown on this book (null = use contributor's name)
 */
@Entity(
    tableName = "book_contributors",
    primaryKeys = ["bookId", "contributorId", "role"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE, // If book is deleted, remove relation
        ),
        ForeignKey(
            entity = ContributorEntity::class,
            parentColumns = ["id"],
            childColumns = ["contributorId"],
            onDelete = ForeignKey.CASCADE, // If contributor is deleted, remove relation
        ),
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["contributorId"]),
    ],
)
data class BookContributorCrossRef(
    val bookId: BookId,
    val contributorId: String,
    // "author", "narrator", etc.
    val role: String,
    // Original attribution name (e.g., "Richard Bachman" even when linked to Stephen King)
    val creditedAs: String? = null,
)

/**
 * Cross-reference entity for the many-to-many relationship between Books and Series.
 *
 * A book can belong to multiple series (e.g., "Mistborn", "Mistborn Era 1", "The Cosmere"),
 * and a series can contain multiple books.
 *
 * @property bookId Foreign key to the book
 * @property seriesId Foreign key to the series
 * @property sequence Position in this series (e.g., "1", "1.5", "Book Zero")
 */
@Entity(
    tableName = "book_series",
    primaryKeys = ["bookId", "seriesId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE, // If book is deleted, remove relation
        ),
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE, // If series is deleted, remove relation
        ),
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["seriesId"]),
    ],
)
data class BookSeriesCrossRef(
    val bookId: BookId,
    val seriesId: String,
    val sequence: String? = null,
)

/**
 * Relation POJO for loading a book with all its contributors and series in a single query.
 *
 * This eliminates the N+1 query problem by using Room's @Relation annotation
 * to batch-load all contributors and series for all books in additional queries.
 *
 * Contributors are loaded with their roles via the junction table, allowing
 * filtering by role (author, narrator, etc.) in the repository layer.
 *
 * Series are loaded with sequence info for display (e.g., "Mistborn #1").
 */
data class BookWithContributors(
    @Embedded val book: BookEntity,
    @Relation(
        entity = ContributorEntity::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy =
            Junction(
                value = BookContributorCrossRef::class,
                parentColumn = "bookId",
                entityColumn = "contributorId",
            ),
    )
    val contributors: List<ContributorEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "bookId",
    )
    val contributorRoles: List<BookContributorCrossRef>,
    @Relation(
        entity = SeriesEntity::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy =
            Junction(
                value = BookSeriesCrossRef::class,
                parentColumn = "bookId",
                entityColumn = "seriesId",
            ),
    )
    val series: List<SeriesEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "bookId",
    )
    val seriesSequences: List<BookSeriesCrossRef>,
)

/**
 * Data class for series with aggregated book count.
 *
 * Used by queries that join series with books to count related books.
 * Room can directly map query results with COUNT(*) to this class.
 */
data class SeriesWithBookCount(
    @Embedded val series: SeriesEntity,
    val bookCount: Int,
)

/**
 * Data class for contributor with aggregated book count.
 *
 * Used by queries that join contributors with book_contributors
 * to count how many books a contributor is associated with.
 */
data class ContributorWithBookCount(
    @Embedded val contributor: ContributorEntity,
    val bookCount: Int,
)

/**
 * Relation POJO for loading a series with all its books in a single query.
 *
 * Uses Room's @Relation with Junction to handle the many-to-many relationship
 * through the book_series table, avoiding N+1 query problems when displaying
 * series with cover stacks.
 */
data class SeriesWithBooks(
    @Embedded val series: SeriesEntity,
    @Relation(
        entity = BookEntity::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy =
            Junction(
                value = BookSeriesCrossRef::class,
                parentColumn = "seriesId",
                entityColumn = "bookId",
            ),
    )
    val books: List<BookEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "seriesId",
    )
    val bookSequences: List<BookSeriesCrossRef>,
)

/**
 * Relation POJO for loading a book with all its series in a single query.
 *
 * Uses Room's @Relation with Junction to handle the many-to-many relationship
 * through the book_series table.
 */
data class BookWithSeries(
    @Embedded val book: BookEntity,
    @Relation(
        entity = SeriesEntity::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy =
            Junction(
                value = BookSeriesCrossRef::class,
                parentColumn = "bookId",
                entityColumn = "seriesId",
            ),
    )
    val series: List<SeriesEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "bookId",
    )
    val seriesSequences: List<BookSeriesCrossRef>,
)

/**
 * Data class for a contributor role with book count.
 *
 * Used by queries that count books per role for a specific contributor.
 */
data class RoleWithBookCount(
    val role: String,
    val bookCount: Int,
)
