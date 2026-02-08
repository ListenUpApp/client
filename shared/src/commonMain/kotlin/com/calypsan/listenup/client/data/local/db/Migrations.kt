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

/**
 * Migration from version 12 to version 13.
 *
 * Changes:
 * - Add servers table for multi-server support
 *
 * The servers table stores discovered ListenUp servers and their per-server
 * authentication state, enabling:
 * - Zero-config server discovery via mDNS
 * - Instant context switching between servers without re-authentication
 * - Offline server persistence (remembered even when not on local network)
 */
val MIGRATION_12_13 =
    object : Migration(12, 13) {
        override fun migrate(connection: SQLiteConnection) {
            // Create servers table for multi-server support
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS servers (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    apiVersion TEXT NOT NULL,
                    serverVersion TEXT NOT NULL,
                    localUrl TEXT,
                    remoteUrl TEXT,
                    accessToken TEXT,
                    refreshToken TEXT,
                    sessionId TEXT,
                    userId TEXT,
                    isActive INTEGER NOT NULL DEFAULT 0,
                    lastSeenAt INTEGER NOT NULL DEFAULT 0,
                    lastConnectedAt INTEGER
                )
                """.trimIndent(),
            )

            // Create index on isActive for efficient active server lookup
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_servers_isActive ON servers(isActive)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 13 to version 14.
 *
 * Bridge migration - no schema changes.
 * Required to maintain sequential migration path.
 */
val MIGRATION_13_14 =
    object : Migration(13, 14) {
        override fun migrate(connection: SQLiteConnection) {
            // No schema changes - bridge migration only
        }
    }

/**
 * Migration from version 14 to version 15.
 *
 * Changes:
 * - Add dominantColor column to books table (cached palette color as ARGB int)
 * - Add darkMutedColor column to books table (cached palette color for gradients)
 * - Add vibrantColor column to books table (cached palette color for accents)
 *
 * These cached colors enable instant gradient rendering when viewing a book,
 * eliminating the need to extract colors from the cover image at runtime.
 */
val MIGRATION_14_15 =
    object : Migration(14, 15) {
        override fun migrate(connection: SQLiteConnection) {
            // Add dominantColor column
            connection.execSQL(
                """
                ALTER TABLE books ADD COLUMN dominantColor INTEGER DEFAULT NULL
                """.trimIndent(),
            )

            // Add darkMutedColor column
            connection.execSQL(
                """
                ALTER TABLE books ADD COLUMN darkMutedColor INTEGER DEFAULT NULL
                """.trimIndent(),
            )

            // Add vibrantColor column
            connection.execSQL(
                """
                ALTER TABLE books ADD COLUMN vibrantColor INTEGER DEFAULT NULL
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 15 to version 16.
 *
 * Changes:
 * - Add hasCustomSpeed column to playback_positions table (whether user set custom speed)
 *
 * This enables per-book speed preference tracking - distinguishing between a book
 * that was played at 1.0x because that's the global default vs one where the user
 * explicitly set 1.0x for that specific book.
 */
val MIGRATION_15_16 =
    object : Migration(15, 16) {
        override fun migrate(connection: SQLiteConnection) {
            // Add hasCustomSpeed column to playback_positions table
            connection.execSQL(
                """
                ALTER TABLE playback_positions ADD COLUMN hasCustomSpeed INTEGER NOT NULL DEFAULT 0
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 16 to version 17.
 *
 * Changes:
 * - Add collections table for admin collection management
 *
 * Collections are admin-only features that allow grouping books
 * for organizational purposes.
 */
val MIGRATION_16_17 =
    object : Migration(16, 17) {
        override fun migrate(connection: SQLiteConnection) {
            // Create collections table
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS collections (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    bookCount INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    syncState INTEGER NOT NULL DEFAULT 0,
                    serverVersion INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 17 to version 18.
 *
 * Changes:
 * - Add shelves table for personal curation and social discovery
 *
 * Shelves are user-created curated lists of books. Unlike collections
 * (admin-managed access boundaries), shelves are personal - each belongs
 * to one user and can contain books from any library/collection.
 */
val MIGRATION_17_18 =
    object : Migration(17, 18) {
        override fun migrate(connection: SQLiteConnection) {
            // Create shelves table
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS shelves (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT,
                    ownerId TEXT NOT NULL,
                    ownerDisplayName TEXT NOT NULL,
                    ownerAvatarColor TEXT NOT NULL,
                    bookCount INTEGER NOT NULL,
                    totalDurationSeconds INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            // Create index on ownerId for efficient "my shelves" queries
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_shelves_ownerId ON shelves(ownerId)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 18 to version 19.
 *
 * Changes:
 * - Add tags table for community-wide content descriptors
 * - Add book_tags junction table for many-to-many book-tag relationships
 *
 * Tags are community-wide content descriptors (e.g., "found-family", "slow-burn")
 * that any user can apply to books they can access.
 */
val MIGRATION_18_19 =
    object : Migration(18, 19) {
        override fun migrate(connection: SQLiteConnection) {
            // Create tags table
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tags (
                    id TEXT PRIMARY KEY NOT NULL,
                    slug TEXT NOT NULL,
                    bookCount INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            // Create unique index on slug
            connection.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_tags_slug ON tags(slug)
                """.trimIndent(),
            )

            // Create book_tags junction table
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS book_tags (
                    bookId TEXT NOT NULL,
                    tagId TEXT NOT NULL,
                    PRIMARY KEY (bookId, tagId),
                    FOREIGN KEY (bookId) REFERENCES books(id) ON DELETE CASCADE,
                    FOREIGN KEY (tagId) REFERENCES tags(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )

            // Create indices for efficient lookups
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_book_tags_bookId ON book_tags(bookId)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_book_tags_tagId ON book_tags(tagId)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 19 to version 20.
 *
 * Changes:
 * - Add lastPlayedAt column to playback_positions table
 *
 * This enables proper tracking of when the user actually last played a book,
 * separate from when the local entity was modified. Used for:
 * - Ordering "Continue Listening" by actual play time
 * - Social features showing "last read" timestamps to other users
 *
 * For existing data, lastPlayedAt will be NULL and code falls back to updatedAt.
 */
val MIGRATION_19_20 =
    object : Migration(19, 20) {
        override fun migrate(connection: SQLiteConnection) {
            // Add lastPlayedAt column (nullable for existing rows)
            connection.execSQL(
                """
                ALTER TABLE playback_positions ADD COLUMN lastPlayedAt INTEGER DEFAULT NULL
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 20 to version 21.
 *
 * Changes:
 * - Add listening_events table for offline-first stats
 *
 * Listening events are append-only records of time spent listening.
 * Used to compute stats locally and sync bidirectionally with server.
 */
val MIGRATION_20_21 =
    object : Migration(20, 21) {
        override fun migrate(connection: SQLiteConnection) {
            // Create listening_events table
            // Note: syncState is stored as INTEGER (enum ordinal)
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS listening_events (
                    id TEXT PRIMARY KEY NOT NULL,
                    bookId TEXT NOT NULL,
                    startPositionMs INTEGER NOT NULL,
                    endPositionMs INTEGER NOT NULL,
                    startedAt INTEGER NOT NULL,
                    endedAt INTEGER NOT NULL,
                    playbackSpeed REAL NOT NULL,
                    deviceId TEXT NOT NULL,
                    syncState INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            // Create indices for efficient queries
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_listening_events_bookId
                ON listening_events(bookId)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_listening_events_endedAt
                ON listening_events(endedAt)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_listening_events_syncState
                ON listening_events(syncState)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 21 to version 22.
 *
 * Changes:
 * - Add avatarType column to users table (defaults to "auto")
 * - Add avatarValue column to users table (image path, nullable)
 * - Add avatarColor column to users table (hex color, defaults to gray)
 *
 * These columns store the user's avatar configuration:
 * - avatarType: "auto" for generated initials, "image" for uploaded photo
 * - avatarValue: Path to avatar image (only set when type is "image")
 * - avatarColor: Hex color for auto-generated avatar background
 */
val MIGRATION_21_22 =
    object : Migration(21, 22) {
        override fun migrate(connection: SQLiteConnection) {
            // Add avatarType column (defaults to "auto")
            connection.execSQL(
                """
                ALTER TABLE users ADD COLUMN avatarType TEXT NOT NULL DEFAULT 'auto'
                """.trimIndent(),
            )

            // Add avatarValue column (nullable - only set for image avatars)
            connection.execSQL(
                """
                ALTER TABLE users ADD COLUMN avatarValue TEXT DEFAULT NULL
                """.trimIndent(),
            )

            // Add avatarColor column (defaults to a neutral gray)
            connection.execSQL(
                """
                ALTER TABLE users ADD COLUMN avatarColor TEXT NOT NULL DEFAULT '#6B7280'
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 22 to version 23.
 *
 * Changes:
 * - Add tagline column to users table for profile bio
 */
val MIGRATION_22_23 =
    object : Migration(22, 23) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                ALTER TABLE users ADD COLUMN tagline TEXT DEFAULT NULL
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 23 to version 24.
 *
 * Changes:
 * - Add user_profiles table for caching other users' profile data
 *
 * Purpose:
 * Enables offline display of user avatars and names throughout the app.
 * Profiles are cached from activity feed, discovery, book details, and SSE events.
 */
val MIGRATION_23_24 =
    object : Migration(23, 24) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS user_profiles (
                    id TEXT NOT NULL PRIMARY KEY,
                    displayName TEXT NOT NULL,
                    avatarType TEXT NOT NULL DEFAULT 'auto',
                    avatarValue TEXT DEFAULT NULL,
                    avatarColor TEXT NOT NULL DEFAULT '#6B7280',
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 24 to version 25.
 *
 * Changes:
 * - Add active_sessions table for "What Others Are Listening To" feature
 */
val MIGRATION_24_25 =
    object : Migration(24, 25) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS active_sessions (
                    sessionId TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    bookId TEXT NOT NULL,
                    startedAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_active_sessions_userId
                ON active_sessions(userId)
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_active_sessions_bookId
                ON active_sessions(bookId)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 25 to version 26.
 *
 * Changes:
 * - Add activities table for offline activity feed
 *
 * Activities are denormalized records of user actions (started book, finished book,
 * milestones, etc.) for the social activity feed. Synced via SSE events.
 */
val MIGRATION_25_26 =
    object : Migration(25, 26) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS activities (
                    id TEXT NOT NULL PRIMARY KEY,
                    userId TEXT NOT NULL,
                    type TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    userDisplayName TEXT NOT NULL,
                    userAvatarColor TEXT NOT NULL,
                    userAvatarType TEXT NOT NULL,
                    userAvatarValue TEXT,
                    bookId TEXT,
                    bookTitle TEXT,
                    bookAuthorName TEXT,
                    bookCoverPath TEXT,
                    isReread INTEGER NOT NULL DEFAULT 0,
                    durationMs INTEGER NOT NULL DEFAULT 0,
                    milestoneValue INTEGER NOT NULL DEFAULT 0,
                    milestoneUnit TEXT,
                    shelfId TEXT,
                    shelfName TEXT
                )
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_activities_userId
                ON activities(userId)
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_activities_createdAt
                ON activities(createdAt)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 26 to version 27.
 *
 * Changes:
 * - Add user_stats table for caching all-time leaderboard totals
 *
 * Week/Month stats are calculated locally from activities table.
 * All-time totals come from server and are cached here for offline display.
 * Updated via SSE user_stats.updated events.
 */
val MIGRATION_26_27 =
    object : Migration(26, 27) {
        override fun migrate(connection: SQLiteConnection) {
            // Create user_stats table for caching all-time leaderboard totals
            // Primary key oduserId is automatically indexed and unique
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS user_stats (
                    oduserId TEXT NOT NULL PRIMARY KEY,
                    displayName TEXT NOT NULL,
                    avatarColor TEXT NOT NULL,
                    avatarType TEXT NOT NULL,
                    avatarValue TEXT,
                    totalTimeMs INTEGER NOT NULL,
                    totalBooks INTEGER NOT NULL,
                    currentStreak INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 27 to version 28.
 *
 * Changes:
 * - Add firstName column to users table
 * - Add lastName column to users table
 *
 * These fields store the user's first and last name separately,
 * enabling pre-population of the edit profile form fields.
 */
val MIGRATION_27_28 =
    object : Migration(27, 28) {
        override fun migrate(connection: SQLiteConnection) {
            // Add firstName column (nullable)
            connection.execSQL(
                """
                ALTER TABLE users ADD COLUMN firstName TEXT DEFAULT NULL
                """.trimIndent(),
            )

            // Add lastName column (nullable)
            connection.execSQL(
                """
                ALTER TABLE users ADD COLUMN lastName TEXT DEFAULT NULL
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 28 to 29.
 *
 * Changes:
 * - Add genres table for offline genre support
 * - Add book_genres junction table for book-genre relationships
 *
 * Genres are now synced during initial sync (via GenrePuller) and book sync.
 * This enables offline-first genre display without API calls.
 */
val MIGRATION_28_29 =
    object : Migration(28, 29) {
        override fun migrate(connection: SQLiteConnection) {
            // Create genres table
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS genres (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    slug TEXT NOT NULL,
                    path TEXT NOT NULL,
                    bookCount INTEGER NOT NULL DEFAULT 0,
                    parentId TEXT,
                    depth INTEGER NOT NULL DEFAULT 0,
                    sortOrder INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )

            // Create indices for genres
            connection.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_genres_slug ON genres (slug)
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_genres_path ON genres (path)
                """.trimIndent(),
            )

            // Create book_genres junction table
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS book_genres (
                    bookId TEXT NOT NULL,
                    genreId TEXT NOT NULL,
                    PRIMARY KEY (bookId, genreId),
                    FOREIGN KEY (bookId) REFERENCES books(id) ON DELETE CASCADE,
                    FOREIGN KEY (genreId) REFERENCES genres(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )

            // Create indices for book_genres
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_book_genres_bookId ON book_genres (bookId)
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_book_genres_genreId ON book_genres (genreId)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 29 to 30.
 *
 * Changes:
 * - Add isFinished column to playback_positions table
 *
 * isFinished is the authoritative "finished" flag from the server.
 * Unlike deriving finished status from position (>= 99%), this flag
 * honors books marked complete in ABS even if position < 99%.
 * This fixes Continue Listening showing finished books from ABS import.
 */
val MIGRATION_29_30 =
    object : Migration(29, 30) {
        override fun migrate(connection: SQLiteConnection) {
            // Add isFinished column (defaults to false for existing rows)
            connection.execSQL(
                """
                ALTER TABLE playback_positions ADD COLUMN isFinished INTEGER NOT NULL DEFAULT 0
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 30 to 31.
 *
 * Changes:
 * - Add finishedAt column to playback_positions table
 * - Add startedAt column to playback_positions table
 *
 * These timestamps enable:
 * - finishedAt: When the book was marked finished (for stats/activity)
 * - startedAt: When the user started this book (first listening event)
 */
val MIGRATION_30_31 =
    object : Migration(30, 31) {
        override fun migrate(connection: SQLiteConnection) {
            // Add finishedAt column (nullable - only set when finished)
            connection.execSQL(
                """
                ALTER TABLE playback_positions ADD COLUMN finishedAt INTEGER DEFAULT NULL
                """.trimIndent(),
            )

            // Add startedAt column (nullable - only set on new positions after this migration)
            connection.execSQL(
                """
                ALTER TABLE playback_positions ADD COLUMN startedAt INTEGER DEFAULT NULL
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 31 to 32.
 *
 * Changes:
 * - Add source column to listening_events table
 *
 * The source field tracks the origin of listening events:
 * - "playback": Normal listening activity from the app
 * - "import": Imported from external systems (e.g., Audiobookshelf)
 * - "manual": Created by manual user actions
 */
val MIGRATION_31_32 =
    object : Migration(31, 32) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                ALTER TABLE listening_events ADD COLUMN source TEXT NOT NULL DEFAULT 'playback'
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 32 to 33.
 *
 * Changes:
 * - Add reading_sessions table for offline-first "Readers" section
 *
 * The reading_sessions table caches reader data for each book, enabling:
 * - Offline display of who's reading a book
 * - Real-time updates via SSE events
 * - Immediate display without network round-trips
 *
 * User info is denormalized for immediate display without joins.
 */
val MIGRATION_32_33 =
    object : Migration(32, 33) {
        override fun migrate(connection: SQLiteConnection) {
            // Create reading_sessions table
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reading_sessions (
                    id TEXT PRIMARY KEY NOT NULL,
                    bookId TEXT NOT NULL,
                    oduserId TEXT NOT NULL,
                    userDisplayName TEXT NOT NULL,
                    userAvatarColor TEXT NOT NULL,
                    userAvatarType TEXT NOT NULL,
                    userAvatarValue TEXT,
                    isCurrentlyReading INTEGER NOT NULL,
                    currentProgress REAL NOT NULL,
                    startedAt INTEGER NOT NULL,
                    finishedAt INTEGER,
                    completionCount INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            // Create indices for efficient queries
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_reading_sessions_bookId ON reading_sessions (bookId)
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_reading_sessions_oduserId ON reading_sessions (oduserId)
                """.trimIndent(),
            )

            connection.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS index_reading_sessions_bookId_oduserId ON reading_sessions (bookId, oduserId)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 33 to version 34.
 *
 * Changes:
 * - Add sortTitle column to books table
 *
 * The sortTitle field allows users to specify a custom title for sorting purposes.
 * For example, "The Lord of the Rings" can have sortTitle "Lord of the Rings, The"
 * to sort under "L" instead of "T".
 */
val MIGRATION_33_34 =
    object : Migration(33, 34) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                ALTER TABLE books ADD COLUMN sortTitle TEXT DEFAULT NULL
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 35 to version 36.
 *
 * Changes:
 * - Add syncState column to shelves table for offline-first shelf CRUD
 */
val MIGRATION_35_36 =
    object : Migration(35, 36) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                ALTER TABLE shelves ADD COLUMN syncState INTEGER NOT NULL DEFAULT 0
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_shelves_syncState ON shelves (syncState)
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 34 to version 35.
 *
 * Changes:
 * - Add coverPaths column to shelves table for book cover grid display
 */
val MIGRATION_34_35 =
    object : Migration(34, 35) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                ALTER TABLE shelves ADD COLUMN coverPaths TEXT NOT NULL DEFAULT ''
                """.trimIndent(),
            )
        }
    }

/**
 * Migration from version 36 to version 37.
 *
 * Changes:
 * - Add shelf_books junction table for offline-first shelf-book relationships
 * - This enables displaying shelf contents without hitting the server
 * - Replaces the problematic coverPaths field with proper JOIN queries
 */
val MIGRATION_36_37 =
    object : Migration(36, 37) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS shelf_books (
                    shelfId TEXT NOT NULL,
                    bookId TEXT NOT NULL,
                    addedAt INTEGER NOT NULL,
                    PRIMARY KEY (shelfId, bookId),
                    FOREIGN KEY (shelfId) REFERENCES shelves(id) ON DELETE CASCADE,
                    FOREIGN KEY (bookId) REFERENCES books(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_shelf_books_shelfId ON shelf_books (shelfId)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_shelf_books_bookId ON shelf_books (bookId)
                """.trimIndent(),
            )
        }
    }
