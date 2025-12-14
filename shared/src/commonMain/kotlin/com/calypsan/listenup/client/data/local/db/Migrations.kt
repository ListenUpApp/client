package com.calypsan.listenup.client.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/*
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
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            // Create books table
            connection.execSQL(
                """
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
                """.trimIndent(),
            )

            // Create sync_metadata table
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_metadata (
                    key TEXT PRIMARY KEY NOT NULL,
                    value TEXT NOT NULL
                )
                """.trimIndent(),
            )

            // Create index on syncState for efficient pending changes queries
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_books_syncState
                ON books(syncState)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 2 to version 3.
 *
 * Changes:
 * - Add chapters table
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
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
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_chapters_bookId
                ON chapters(bookId)
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_chapters_syncState
                ON chapters(syncState)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 3 to version 4.
 *
 * Bridge migration - no schema changes.
 * Versions 3→4→5 were internal development versions that may exist
 * on some devices. This migration ensures a clean upgrade path.
 */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            // No schema changes - bridge migration only
        }
    }

/**
 * Migration from version 4 to version 5.
 *
 * Bridge migration - no schema changes.
 */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(connection: SQLiteConnection) {
            // No schema changes - bridge migration only
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
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(connection: SQLiteConnection) {
            // Add audioFilesJson column to books table
            connection.execSQL(
                """
                ALTER TABLE books ADD COLUMN audioFilesJson TEXT DEFAULT NULL
                """.trimIndent(),
            )

            // Create playback_positions table for local position persistence
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS playback_positions (
                    bookId TEXT PRIMARY KEY NOT NULL,
                    positionMs INTEGER NOT NULL,
                    playbackSpeed REAL NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    syncedAt INTEGER
                )
                """.trimIndent(),
            )

            // Create pending_listening_events table for event queue
            connection.execSQL(
                """
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
                """.trimIndent(),
            )

            // Create index on bookId for pending events
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_pending_listening_events_bookId
                ON pending_listening_events(bookId)
                """.trimIndent(),
            )

            // Clear sync checkpoint to force full re-sync on next launch.
            // This ensures existing books get audioFilesJson populated.
            connection.execSQL(
                """
                DELETE FROM sync_metadata WHERE key = 'last_sync_books'
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 6 to version 7.
 *
 * Changes:
 * - Add downloads table for tracking downloaded audio files
 */
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
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
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_downloads_bookId ON downloads (bookId)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 7 to version 8.
 *
 * Changes:
 * - Add subtitle column to books table
 */
val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                ALTER TABLE books ADD COLUMN subtitle TEXT DEFAULT NULL
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 8 to version 9.
 *
 * Changes:
 * - Add FTS5 virtual tables for full-text search
 * - books_fts, contributors_fts, series_fts
 *
 * Note: Using FTS5 for better ranking (bm25) and modern features.
 * Tables are standalone (not external content) for simpler sync.
 * Porter tokenizer enables stemming (e.g., "running" matches "run").
 */
val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(connection: SQLiteConnection) {
            // Create books FTS5 table
            connection.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS books_fts USING fts5(
                    bookId,
                    title,
                    subtitle,
                    description,
                    author,
                    narrator,
                    seriesName,
                    genres,
                    tokenize='porter'
                )
                """.trimIndent(),
            )

            // Create contributors FTS5 table
            connection.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS contributors_fts USING fts5(
                    contributorId,
                    name,
                    description,
                    tokenize='porter'
                )
                """.trimIndent(),
            )

            // Create series FTS5 table
            connection.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS series_fts USING fts5(
                    seriesId,
                    name,
                    description,
                    tokenize='porter'
                )
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 9 to version 10.
 *
 * Changes:
 * - Add book_series junction table for many-to-many book-series relationships
 * - Migrate existing seriesId/sequence data from books to book_series
 * - Remove seriesId, seriesName, sequence columns from books table
 *
 * A book can now belong to multiple series (e.g., "Mistborn", "Mistborn Era 1", "The Cosmere").
 */
val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override fun migrate(connection: SQLiteConnection) {
            // Create book_series junction table
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS book_series (
                    bookId TEXT NOT NULL,
                    seriesId TEXT NOT NULL,
                    sequence TEXT,
                    PRIMARY KEY (bookId, seriesId),
                    FOREIGN KEY (bookId) REFERENCES books(id) ON DELETE CASCADE,
                    FOREIGN KEY (seriesId) REFERENCES series(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )

            // Create indices for efficient lookups
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_book_series_bookId ON book_series(bookId)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_book_series_seriesId ON book_series(seriesId)
                """.trimIndent(),
            )

            // Migrate existing book-series relationships from books table
            connection.execSQL(
                """
                INSERT INTO book_series (bookId, seriesId, sequence)
                SELECT id, seriesId, sequence FROM books WHERE seriesId IS NOT NULL AND seriesId != ''
                """.trimIndent(),
            )

            // SQLite doesn't support DROP COLUMN in older versions, so we rebuild the table
            // Create new books table without series columns
            connection.execSQL(
                """
                CREATE TABLE books_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    subtitle TEXT,
                    coverUrl TEXT,
                    totalDuration INTEGER NOT NULL,
                    description TEXT,
                    genres TEXT,
                    publishYear INTEGER,
                    audioFilesJson TEXT,
                    syncState INTEGER NOT NULL,
                    lastModified INTEGER NOT NULL,
                    serverVersion INTEGER,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            // Copy data to new table (excluding series columns)
            connection.execSQL(
                """
                INSERT INTO books_new (id, title, subtitle, coverUrl, totalDuration, description, genres, publishYear, audioFilesJson, syncState, lastModified, serverVersion, createdAt, updatedAt)
                SELECT id, title, subtitle, coverUrl, totalDuration, description, genres, publishYear, audioFilesJson, syncState, lastModified, serverVersion, createdAt, updatedAt FROM books
                """.trimIndent(),
            )

            // Drop old table and rename new one
            connection.execSQL("DROP TABLE books")
            connection.execSQL("ALTER TABLE books_new RENAME TO books")

            // Recreate index on syncState
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_books_syncState ON books(syncState)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 10 to version 11.
 *
 * Changes:
 * - Add publisher column to books table (if not exists)
 * - Add language column to books table (if not exists)
 * - Add isbn column to books table
 * - Add asin column to books table
 * - Add abridged column to books table
 *
 * Note: publisher and language may already exist from earlier development builds,
 * so we check for column existence before adding.
 */
val MIGRATION_10_11 =
    object : Migration(10, 11) {
        override fun migrate(connection: SQLiteConnection) {
            // Get existing columns in books table
            val existingColumns = mutableSetOf<String>()
            connection.prepare("PRAGMA table_info(books)").use { stmt ->
                while (stmt.step()) {
                    existingColumns.add(stmt.getText(1)) // Column name is at index 1
                }
            }

            // Add publisher column if not exists
            if ("publisher" !in existingColumns) {
                connection.execSQL(
                    """
                    ALTER TABLE books ADD COLUMN publisher TEXT DEFAULT NULL
                    """.trimIndent(),
                )
            }

            // Add language column if not exists
            if ("language" !in existingColumns) {
                connection.execSQL(
                    """
                    ALTER TABLE books ADD COLUMN language TEXT DEFAULT NULL
                    """.trimIndent(),
                )
            }

            // Add isbn column if not exists
            if ("isbn" !in existingColumns) {
                connection.execSQL(
                    """
                    ALTER TABLE books ADD COLUMN isbn TEXT DEFAULT NULL
                    """.trimIndent(),
                )
            }

            // Add asin column if not exists
            if ("asin" !in existingColumns) {
                connection.execSQL(
                    """
                    ALTER TABLE books ADD COLUMN asin TEXT DEFAULT NULL
                    """.trimIndent(),
                )
            }

            // Add abridged column if not exists (defaults to false/0)
            if ("abridged" !in existingColumns) {
                connection.execSQL(
                    """
                    ALTER TABLE books ADD COLUMN abridged INTEGER NOT NULL DEFAULT 0
                    """.trimIndent(),
                )
            }
        }
    }

/**
 * Migration from version 11 to version 12.
 *
 * Changes:
 * - Add website column to contributors table
 * - Add birthDate column to contributors table
 * - Add deathDate column to contributors table
 * - Add aliases column to contributors table (comma-separated pen names)
 * - Add creditedAs column to book_contributors table (original attribution name)
 */
val MIGRATION_11_12 =
    object : Migration(11, 12) {
        override fun migrate(connection: SQLiteConnection) {
            // Get existing columns in contributors table
            val contributorColumns = mutableSetOf<String>()
            connection.prepare("PRAGMA table_info(contributors)").use { stmt ->
                while (stmt.step()) {
                    contributorColumns.add(stmt.getText(1)) // Column name is at index 1
                }
            }

            // Add website column if not exists
            if ("website" !in contributorColumns) {
                connection.execSQL(
                    """
                    ALTER TABLE contributors ADD COLUMN website TEXT DEFAULT NULL
                    """.trimIndent(),
                )
            }

            // Add birthDate column if not exists
            if ("birthDate" !in contributorColumns) {
                connection.execSQL(
                    """
                    ALTER TABLE contributors ADD COLUMN birthDate TEXT DEFAULT NULL
                    """.trimIndent(),
                )
            }

            // Add deathDate column if not exists
            if ("deathDate" !in contributorColumns) {
                connection.execSQL(
                    """
                    ALTER TABLE contributors ADD COLUMN deathDate TEXT DEFAULT NULL
                    """.trimIndent(),
                )
            }

            // Add aliases column if not exists (comma-separated pen names)
            if ("aliases" !in contributorColumns) {
                connection.execSQL(
                    """
                    ALTER TABLE contributors ADD COLUMN aliases TEXT DEFAULT NULL
                    """.trimIndent(),
                )
            }

            // Get existing columns in book_contributors table
            val bookContributorColumns = mutableSetOf<String>()
            connection.prepare("PRAGMA table_info(book_contributors)").use { stmt ->
                while (stmt.step()) {
                    bookContributorColumns.add(stmt.getText(1))
                }
            }

            // Add creditedAs column if not exists (original attribution name)
            if ("creditedAs" !in bookContributorColumns) {
                connection.execSQL(
                    """
                    ALTER TABLE book_contributors ADD COLUMN creditedAs TEXT DEFAULT NULL
                    """.trimIndent(),
                )
            }
        }
    }
