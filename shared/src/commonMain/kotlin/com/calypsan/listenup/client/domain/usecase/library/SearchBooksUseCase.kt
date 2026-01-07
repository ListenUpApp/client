package com.calypsan.listenup.client.domain.usecase.library

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.core.validationError
import com.calypsan.listenup.client.domain.model.SearchHitType
import com.calypsan.listenup.client.domain.model.SearchResult
import com.calypsan.listenup.client.domain.repository.SearchRepository

/**
 * Use case for searching books in the library.
 *
 * Encapsulates all business logic for search:
 * - Query validation (minimum length, sanitization)
 * - Calling SearchRepository with proper parameters
 * - Result mapping and error handling
 *
 * The ViewModel becomes a thin coordinator that:
 * - Manages UI state (Loading, Success, Error)
 * - Handles debouncing and flow management
 * - Delegates to this use case for business logic
 *
 * Follows the operator invoke pattern for clean call-site syntax:
 * ```kotlin
 * when (val result = searchBooksUseCase(query, types, limit)) {
 *     is Success -> displayResults(result.data)
 *     is Failure -> showError(result.message)
 * }
 * ```
 */
open class SearchBooksUseCase(
    private val searchRepository: SearchRepository,
) {
    /**
     * Execute search with the provided query.
     *
     * @param query Search query string (will be trimmed)
     * @param types Types to search (null = all types)
     * @param genres Genre slugs to filter by
     * @param genrePath Genre path prefix for hierarchical filtering
     * @param limit Max results to return
     * @return Result containing SearchResult on success, or an error on failure
     */
    open suspend operator fun invoke(
        query: String,
        types: List<SearchHitType>? = null,
        genres: List<String>? = null,
        genrePath: String? = null,
        limit: Int = DEFAULT_RESULT_LIMIT,
    ): Result<SearchResult> {
        val trimmedQuery = query.trim()

        // Validate query length
        if (trimmedQuery.length < MIN_QUERY_LENGTH) {
            return if (trimmedQuery.isEmpty()) {
                // Empty query returns empty result (not an error)
                Success(
                    SearchResult(
                        query = trimmedQuery,
                        total = 0,
                        tookMs = 0,
                        hits = emptyList(),
                    ),
                )
            } else {
                validationError("Search query must be at least $MIN_QUERY_LENGTH characters")
            }
        }

        // Validate limit
        if (limit < 1 || limit > MAX_RESULT_LIMIT) {
            return validationError("Limit must be between 1 and $MAX_RESULT_LIMIT")
        }

        // Perform search
        return suspendRunCatching {
            searchRepository.search(
                query = trimmedQuery,
                types = types,
                genres = genres,
                genrePath = genrePath,
                limit = limit,
            )
        }
    }

    companion object {
        /** Minimum query length to trigger search. */
        const val MIN_QUERY_LENGTH = 2

        /** Maximum number of results to return per search. */
        const val DEFAULT_RESULT_LIMIT = 30

        /** Maximum allowed limit value. */
        const val MAX_RESULT_LIMIT = 100
    }
}
