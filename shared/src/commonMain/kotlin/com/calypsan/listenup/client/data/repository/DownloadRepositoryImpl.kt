package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.local.db.DownloadState
import com.calypsan.listenup.client.domain.model.DownloadedBookSummary
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Reads downloaded-book summaries from Room, enriched with book metadata from [BookRepository].
 *
 * Uses the batched [BookRepository.getBookListItems] variant so the common case (N downloaded
 * books) is one DAO query + one Room relation query, not N+1 per-book lookups.
 */
class DownloadRepositoryImpl(
    private val downloadDao: DownloadDao,
    private val bookRepository: BookRepository,
) : DownloadRepository {
    override suspend fun deleteForBook(bookId: String) {
        downloadDao.deleteForBook(bookId)
    }

    override fun observeDownloadedBooks(): Flow<List<DownloadedBookSummary>> =
        downloadDao.observeAll().map { downloads ->
            val completedByBook =
                downloads
                    .filter { it.state == DownloadState.COMPLETED }
                    .groupBy { it.bookId }

            if (completedByBook.isEmpty()) return@map emptyList()

            // BookListItem.id is a BookId value class — associate by .value to match String keys.
            val books =
                bookRepository
                    .getBookListItems(completedByBook.keys.toList())
                    .associateBy { it.id.value }

            completedByBook
                .mapNotNull { (bookId, files) ->
                    val book = books[bookId] ?: return@mapNotNull null
                    DownloadedBookSummary(
                        bookId = bookId,
                        title = book.title,
                        authorNames = book.authorNames,
                        coverBlurHash = book.coverBlurHash,
                        sizeBytes = files.sumOf { it.downloadedBytes },
                        fileCount = files.size,
                    )
                }.sortedByDescending { it.sizeBytes }
        }
}
