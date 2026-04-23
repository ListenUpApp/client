package com.calypsan.listenup.client.data.sync.sse

import com.calypsan.listenup.client.data.local.db.AudioFileDao
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.GenreDao
import com.calypsan.listenup.client.data.local.db.TagDao

/**
 * Bundle of junction-table DAOs BookPuller writes to when replacing a book's
 * relationships on each sync. Grouped to keep the puller's constructor focused
 * on distinct collaborators rather than individual relationship tables.
 */
data class BookRelationshipDaos(
    val bookContributorDao: BookContributorDao,
    val bookSeriesDao: BookSeriesDao,
    val tagDao: TagDao,
    val genreDao: GenreDao,
    val audioFileDao: AudioFileDao,
)
