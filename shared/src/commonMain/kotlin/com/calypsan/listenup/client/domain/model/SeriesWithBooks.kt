package com.calypsan.listenup.client.domain.model

/**
 * Domain model for a series with all its books.
 *
 * Used for library series views and series detail pages.
 * Includes sequence information for proper ordering.
 */
data class SeriesWithBooks(
    val series: Series,
    val books: List<Book>,
    /** Maps bookId to sequence string (e.g., "1", "1.5") */
    val bookSequences: Map<String, String?>,
) {
    /**
     * Get the sequence for a specific book.
     */
    fun sequenceFor(bookId: String): String? = bookSequences[bookId]

    /**
     * Get books sorted by their sequence in this series.
     */
    fun booksSortedBySequence(): List<Book> =
        books.sortedBy { book ->
            bookSequences[book.id.value]?.toFloatOrNull() ?: Float.MAX_VALUE
        }
}
