package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.domain.model.Collection
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for collection operations.
 *
 * Provides access to admin-managed book collections.
 * Collections are organizational groups created by administrators.
 *
 * Part of the domain layer - implementations live in the data layer.
 */
interface CollectionRepository {
    /**
     * Observe all collections reactively, ordered by name.
     *
     * @return Flow emitting list of all collections
     */
    fun observeAll(): Flow<List<Collection>>

    /**
     * Get all collections synchronously.
     *
     * @return List of all collections
     */
    suspend fun getAll(): List<Collection>

    /**
     * Get a collection by ID.
     *
     * @param id The collection ID
     * @return Collection if found, null otherwise
     */
    suspend fun getById(id: String): Collection?

    /**
     * Insert or update a collection.
     *
     * Used during sync operations to persist server data locally.
     *
     * @param collection The collection to save
     */
    suspend fun upsert(collection: Collection)

    /**
     * Delete a collection by ID from local storage.
     *
     * @param id The collection ID to delete
     */
    suspend fun deleteById(id: String)

    /**
     * Create a new collection on server and persist locally.
     *
     * @param name The collection name
     * @return The created collection
     */
    suspend fun create(name: String): Collection

    /**
     * Delete a collection on server and remove from local storage.
     *
     * @param id The collection ID to delete
     */
    suspend fun delete(id: String)

    /**
     * Add books to a collection via the server API.
     *
     * @param collectionId The collection to add books to
     * @param bookIds The book IDs to add
     */
    suspend fun addBooksToCollection(collectionId: String, bookIds: List<String>)

    /**
     * Refresh collections from the server.
     *
     * Fetches latest collections from server and syncs with local database.
     * Adds new collections, updates existing ones, and removes deleted ones.
     */
    suspend fun refreshFromServer()

    /**
     * Get collection details from the server.
     *
     * @param collectionId The collection ID
     * @return The collection details
     */
    suspend fun getCollectionFromServer(collectionId: String): Collection

    /**
     * Get books in a collection.
     *
     * @param collectionId The collection ID
     * @return List of book summaries in the collection
     */
    suspend fun getCollectionBooks(collectionId: String): List<CollectionBookSummary>

    /**
     * Update a collection's name.
     *
     * @param collectionId The collection ID
     * @param name The new name
     * @return The updated collection
     */
    suspend fun updateCollectionName(collectionId: String, name: String): Collection

    /**
     * Remove a book from a collection.
     *
     * @param collectionId The collection ID
     * @param bookId The book ID to remove
     */
    suspend fun removeBookFromCollection(collectionId: String, bookId: String)

    /**
     * Get shares for a collection.
     *
     * @param collectionId The collection ID
     * @return List of share summaries
     */
    suspend fun getCollectionShares(collectionId: String): List<CollectionShareSummary>

    /**
     * Share a collection with a user.
     *
     * @param collectionId The collection ID
     * @param userId The user to share with
     * @return The created share summary
     */
    suspend fun shareCollection(collectionId: String, userId: String): CollectionShareSummary

    /**
     * Remove a share (unshare).
     *
     * @param shareId The share ID to remove
     */
    suspend fun removeShare(shareId: String)
}

/**
 * Summary of a book in a collection.
 */
data class CollectionBookSummary(
    val id: String,
    val title: String,
    val coverPath: String?,
)

/**
 * Summary of a collection share.
 *
 * userName and userEmail may be empty when fetched from the API,
 * as they require a separate user lookup. Use cases can enrich
 * this data with user information from UserRepository or AdminRepository.
 */
data class CollectionShareSummary(
    val id: String,
    val userId: String,
    val userName: String = "",
    val userEmail: String = "",
    val permission: String,
)
