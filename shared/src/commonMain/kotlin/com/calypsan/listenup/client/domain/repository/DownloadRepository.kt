package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.domain.model.DownloadedBookSummary
import kotlinx.coroutines.flow.Flow

/**
 * Repository for queries about the user's downloaded books.
 *
 * This is the domain-level surface for download-state reads. The per-book download
 * commands (start, cancel, delete) continue to live on
 * [com.calypsan.listenup.client.download.DownloadService]. W8 will unify these into
 * one repository; for now, this interface exposes only the reads that
 * [com.calypsan.listenup.client.presentation.storage.StorageViewModel] needs.
 */
interface DownloadRepository {
    /**
     * Observe all fully-downloaded books as domain summaries, sorted largest first.
     */
    fun observeDownloadedBooks(): Flow<List<DownloadedBookSummary>>
}
