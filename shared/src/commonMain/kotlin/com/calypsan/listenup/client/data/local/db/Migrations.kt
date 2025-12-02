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

/**
 * Migration from version 5 to version 6.
 *
 * Changes:
 * - Add audioFilesJson column to books table for playback
 * - Add playback_positions table for local position persistence
 * - Add pending_listening_events table for event queue
 * - Clear sync checkpoint to force full re-sync (populates audioFilesJson)
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        // Add audioFilesJson column to books table
        connection.execSQL("""
            ALTER TABLE books ADD COLUMN audioFilesJson TEXT DEFAULT NULL
        """.trimIndent())

        // Create playback_positions table for local position persistence
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS playback_positions (
                bookId TEXT PRIMARY KEY NOT NULL,
                positionMs INTEGER NOT NULL,
                playbackSpeed REAL NOT NULL,
                updatedAt INTEGER NOT NULL,
                syncedAt INTEGER
            )
        """.trimIndent())

        // Create pending_listening_events table for event queue
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS pending_listening_events (
                id TEXT PRIMARY KEY NOT NULL,
                bookId TEXT NOT NULL,
                startPositionMs INTEGER NOT NULL,
                endPositionMs INTEGER NOT NULL,
                startedAt INTEGER NOT NULL,
                endedAt INTEGER NOT NULL,
                playbackSpeed REAL NOT NULL,
                deviceId TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                lastAttemptAt INTEGER
            )
        """.trimIndent())

        // Create index on bookId for pending events
        connection.execSQL("""
            CREATE INDEX IF NOT EXISTS index_pending_listening_events_bookId
            ON pending_listening_events(bookId)
        """.trimIndent())

        // Clear sync checkpoint to force full re-sync on next launch.
        // This ensures existing books get audioFilesJson populated.
        connection.execSQL("""
            DELETE FROM sync_metadata WHERE key = 'last_sync_books'
        """.trimIndent())
    }
}

/**
 * Migration from version 6 to version 7.
 *
 * Changes:
 * - Add downloads table for tracking downloaded audio files
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS downloads (
                audioFileId TEXT PRIMARY KEY NOT NULL,
                bookId TEXT NOT NULL,
                filename TEXT NOT NULL,
                fileIndex INTEGER NOT NULL,
                state INTEGER NOT NULL,
                localPath TEXT,
                totalBytes INTEGER NOT NULL,
                downloadedBytes INTEGER NOT NULL,
                queuedAt INTEGER NOT NULL,
                startedAt INTEGER,
                completedAt INTEGER,
                errorMessage TEXT,
                retryCount INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        connection.execSQL("""
            CREATE INDEX IF NOT EXISTS index_downloads_bookId ON downloads (bookId)
        """.trimIndent())
    }
}
