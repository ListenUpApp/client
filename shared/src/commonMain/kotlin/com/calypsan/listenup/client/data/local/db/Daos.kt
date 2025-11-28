package com.calypsan.listenup.client.data.local.db

import androidx.room.Dao
import androidx.room.Query
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
}
