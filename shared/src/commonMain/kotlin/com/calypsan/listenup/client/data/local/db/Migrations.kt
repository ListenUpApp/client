package com.calypsan.listenup.client.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Room database migrations for ListenUpDatabase.
 *
 * All migrations must be manually defined to ensure data preservation
 * across schema versions. Destructive migration is disabled.
 */

/**
 * Migration from version 1 to version 2.
 *
 * Changes:
 * - Add books table with sync fields
 * - Add sync_metadata table for tracking sync state
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        // Create books table
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS books (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                author TEXT NOT NULL,
                coverUrl TEXT,
                totalDuration INTEGER NOT NULL,
                syncState INTEGER NOT NULL,
                lastModified INTEGER NOT NULL,
                serverVersion INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())

        // Create sync_metadata table
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_metadata (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            )
        """.trimIndent())

        // Create index on syncState for efficient pending changes queries
        connection.execSQL("""
            CREATE INDEX IF NOT EXISTS index_books_syncState
            ON books(syncState)
        """.trimIndent())
    }
}

/**
 * Migration from version 2 to version 3.
 *
 * Changes:
 * - Add chapters table
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS chapters (
                id TEXT PRIMARY KEY NOT NULL,
                bookId TEXT NOT NULL,
                title TEXT NOT NULL,
                duration INTEGER NOT NULL,
                startTime INTEGER NOT NULL,
                syncState INTEGER NOT NULL,
                lastModified INTEGER NOT NULL,
                serverVersion INTEGER
            )
        """.trimIndent())

        connection.execSQL("""
            CREATE INDEX IF NOT EXISTS index_chapters_bookId
            ON chapters(bookId)
        """.trimIndent())
        
        connection.execSQL("""
            CREATE INDEX IF NOT EXISTS index_chapters_syncState
            ON chapters(syncState)
        """.trimIndent())
    }
}
