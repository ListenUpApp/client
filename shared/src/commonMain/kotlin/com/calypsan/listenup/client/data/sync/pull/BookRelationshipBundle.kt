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
 * Passing a populated list for a relationship type causes a full replace
 * (DELETE existing → INSERT these) for that book. An empty list causes a DELETE-only —
 * the book's prior rows in that table are removed, none are inserted — matching the
 * pre-refactor `BookPuller` behaviour of clearing stale junctions when the server
 * no longer carries entities for that relationship.
 */
data class BookRelationshipBundle(
    val bookId: BookId,
    val contributors: List<BookContributorCrossRef>,
    val series: List<BookSeriesCrossRef>,
    val tags: List<BookTagCrossRef>,
    val genres: List<BookGenreCrossRef>,
    val audioFiles: List<AudioFileEntity>,
)
