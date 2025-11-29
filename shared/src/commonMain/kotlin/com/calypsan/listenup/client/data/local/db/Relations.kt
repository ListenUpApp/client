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
