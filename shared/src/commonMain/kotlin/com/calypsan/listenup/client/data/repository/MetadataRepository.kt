package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.data.remote.MetadataApiContract
import com.calypsan.listenup.client.data.remote.model.ApplyMatchRequest
import com.calypsan.listenup.client.data.remote.model.CoverOption
import com.calypsan.listenup.client.data.remote.model.MetadataBook
import com.calypsan.listenup.client.data.remote.model.MetadataSearchResult
import kotlinx.coroutines.withContext

/**
 * Contract interface for metadata repository operations.
 *
 * Provides Audible metadata search and book matching functionality.
 * Extracted to enable mocking in tests.
 */
interface MetadataRepositoryContract {
    /**
     * Search Audible for matching audiobooks.
     *
     * @param query Search query (title, author, etc.)
     * @param region Audible region code (default: "us")
     * @return List of matching results
     */
    suspend fun searchAudible(
        query: String,
        region: String = "us",
    ): List<MetadataSearchResult>

    /**
     * Get full metadata for a specific Audible book.
     *
     * @param asin Audible Standard Identification Number
     * @param region Audible region code (default: "us")
     * @return Full book metadata
     */
    suspend fun getMetadataPreview(
        asin: String,
        region: String = "us",
    ): MetadataBook

    /**
     * Apply Audible metadata match to a book.
     *
     * Downloads cover art and updates book metadata from the matched
     * Audible entry. After calling this, the caller should trigger
     * a sync to get the updated book data.
     *
     * @param bookId Local book ID to update
     * @param request Match request with field selections
     */
    suspend fun applyMatch(
        bookId: String,
        request: ApplyMatchRequest,
    )

    /**
     * Search for cover images from multiple sources (iTunes, Audible).
     *
     * @param title Book title to search for
     * @param author Author name (optional, improves results)
     * @return List of cover options sorted by resolution (highest first)
     */
    suspend fun searchCovers(
        title: String,
        author: String,
    ): List<CoverOption>
}

/**
 * Repository for Audible metadata operations.
 *
 * This is an online-only feature - there's no offline fallback
 * since we're fetching external metadata from Audible.
 *
 * @property metadataApi Audible metadata API client
 */
class MetadataRepository(
    private val metadataApi: MetadataApiContract,
) : MetadataRepositoryContract {
    /**
     * Search Audible for matching audiobooks.
     */
    override suspend fun searchAudible(
        query: String,
        region: String,
    ): List<MetadataSearchResult> =
        withContext(IODispatcher) {
            metadataApi.search(query, region)
        }

    /**
     * Get full metadata for a specific Audible book.
     */
    override suspend fun getMetadataPreview(
        asin: String,
        region: String,
    ): MetadataBook =
        withContext(IODispatcher) {
            metadataApi.getBook(asin, region)
        }

    /**
     * Apply Audible metadata match to a book.
     */
    override suspend fun applyMatch(
        bookId: String,
        request: ApplyMatchRequest,
    ) {
        withContext(IODispatcher) {
            metadataApi.applyMatch(bookId, request)
        }
    }

    /**
     * Search for cover images from multiple sources.
     */
    override suspend fun searchCovers(
        title: String,
        author: String,
    ): List<CoverOption> =
        withContext(IODispatcher) {
            metadataApi.searchCovers(title, author)
        }
}
