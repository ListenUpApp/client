package com.calypsan.listenup.client.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Database migrations for ListenUp.
 *
 * Each migration transforms the schema from one version to the next.
 * Migrations MUST be additive and idempotent where possible.
 */
object Migrations {
    /**
     * v1 → v2: Add cover download queue table.
     *
     * Supports persistent, resumable cover downloads that survive app lifecycle.
     * Previously covers were downloaded in fire-and-forget coroutines that
     * would be lost on app kill.
     */
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cover_download_queue` (
                        `bookId` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        `lastAttemptAt` INTEGER,
                        `error` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`bookId`)
                    )
                    """.trimIndent(),
                )
            }
        }

    /** All migrations in order. Add new migrations here. */
    val ALL = arrayOf(MIGRATION_1_2)
}
