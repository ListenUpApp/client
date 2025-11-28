package com.calypsan.listenup.client.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY startTime ASC")
    fun observeChaptersForBook(bookId: BookId): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY startTime ASC")
    suspend fun getChaptersForBook(bookId: BookId): List<ChapterEntity>

    @Upsert
    suspend fun upsert(chapter: ChapterEntity)

    @Upsert
    suspend fun upsertAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersForBook(bookId: BookId)
}