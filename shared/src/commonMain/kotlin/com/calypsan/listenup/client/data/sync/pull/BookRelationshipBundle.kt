package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.data.local.db.AudioFileEntity
import com.calypsan.listenup.client.data.local.db.BookContributorCrossRef
import com.calypsan.listenup.client.data.local.db.BookGenreCrossRef
import com.calypsan.listenup.client.data.local.db.BookSeriesCrossRef
import com.calypsan.listenup.client.data.local.db.BookTagCrossRef

/**
 * Per-book bundle of pre-collected relationship entities, ready for [BookRelationshipWriter]
 * to replace the book's existing junction rows with.
 *
 * Built by [BookPuller] during the pure-collection phase (outside the write transaction).
 *
 * For each relationship field:
 * - **Non-empty list** → full replace: DELETE existing rows, then INSERT these.
 * - **Empty list** → DELETE-only: prior rows for this book are removed, none inserted.
 *   Matches the pre-refactor `BookPuller` behaviour of clearing stale junctions when
 *   the server no longer carries entities for that relationship.
 */
data class BookRelationshipBundle(
    val bookId: BookId,
    val contributors: List<BookContributorCrossRef>,
    val series: List<BookSeriesCrossRef>,
    val tags: List<BookTagCrossRef>,
    val genres: List<BookGenreCrossRef>,
    val audioFiles: List<AudioFileEntity>,
)
