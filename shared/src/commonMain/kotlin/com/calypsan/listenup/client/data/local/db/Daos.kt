package com.calypsan.listenup.client.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series")
    fun observeAll(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getById(id: String): SeriesEntity?

    @Upsert
    suspend fun upsert(series: SeriesEntity)

    @Upsert
    suspend fun upsertAll(series: List<SeriesEntity>)

    @Query("UPDATE series SET syncState = ${SyncState.SYNCED_ORDINAL}, serverVersion = :version WHERE id = :id")
    suspend fun markSynced(id: String, version: Timestamp)

    @Query("UPDATE series SET syncState = ${SyncState.CONFLICT_ORDINAL}, serverVersion = :serverVersion WHERE id = :id")
    suspend fun markConflict(id: String, serverVersion: Timestamp)

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Observe all series with their book counts.
     * Returns series ordered by name with the count of books in each series.
     */
    @Query("""
        SELECT s.*, COUNT(b.id) as bookCount
        FROM series s
        LEFT JOIN books b ON s.id = b.seriesId
        GROUP BY s.id
        ORDER BY s.name ASC
    """)
    fun observeAllWithBookCount(): Flow<List<SeriesWithBookCount>>

    /**
     * Observe all series with their books.
     * Uses Room Relations to batch-load all books for each series.
     * Books are ordered by series sequence then title within each series.
     */
    @Transaction
    @Query("SELECT * FROM series ORDER BY name ASC")
    fun observeAllWithBooks(): Flow<List<SeriesWithBooks>>

    /**
     * Get a single series by ID with all its books.
     */
    @Transaction
    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getByIdWithBooks(id: String): SeriesWithBooks?

    /**
     * Observe a single series by ID with all its books.
     */
    @Transaction
    @Query("SELECT * FROM series WHERE id = :id")
    fun observeByIdWithBooks(id: String): Flow<SeriesWithBooks?>
}

@Dao
interface ContributorDao {
    @Query("SELECT * FROM contributors")
    fun observeAll(): Flow<List<ContributorEntity>>

    @Query("SELECT * FROM contributors WHERE id = :id")
    suspend fun getById(id: String): ContributorEntity?

    @Upsert
    suspend fun upsert(contributor: ContributorEntity)

    @Upsert
    suspend fun upsertAll(contributors: List<ContributorEntity>)

    @Query("UPDATE contributors SET syncState = ${SyncState.SYNCED_ORDINAL}, serverVersion = :version WHERE id = :id")
    suspend fun markSynced(id: String, version: Timestamp)

    @Query("UPDATE contributors SET syncState = ${SyncState.CONFLICT_ORDINAL}, serverVersion = :serverVersion WHERE id = :id")
    suspend fun markConflict(id: String, serverVersion: Timestamp)

    @Query("DELETE FROM contributors WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Observe contributors filtered by role with their book counts.
     * Returns contributors who have the specified role on at least one book,
     * ordered by name with the count of books they're associated with.
     *
     * @param role The role to filter by (e.g., "author", "narrator")
     */
    @Query("""
        SELECT c.*, COUNT(bc.bookId) as bookCount
        FROM contributors c
        INNER JOIN book_contributors bc ON c.id = bc.contributorId
        WHERE bc.role = :role
        GROUP BY c.id
        ORDER BY c.name ASC
    """)
    fun observeByRoleWithCount(role: String): Flow<List<ContributorWithBookCount>>
}
