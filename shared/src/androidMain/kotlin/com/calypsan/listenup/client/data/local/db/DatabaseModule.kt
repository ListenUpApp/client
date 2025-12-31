package com.calypsan.listenup.client.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific database module.
 * Provides Room database configured for Android with proper file location.
 */
actual val platformDatabaseModule: Module =
    module {
        single {
            val context: Context = get()
            val dbFile = context.getDatabasePath("listenup.db")

            Room
                .databaseBuilder<ListenUpDatabase>(
                    context = context.applicationContext,
                    name = dbFile.absolutePath,
                ).setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                ).addCallback(FtsTableCallback())
                .fallbackToDestructiveMigration(false)
                .build()
        }
    }

/**
 * Room callback that ensures FTS5 tables exist.
 *
 * This handles fresh installs where Room creates the database at the current version
 * without running migrations. The FTS tables are defined in MIGRATION_8_9, but that
 * migration only runs when upgrading from version 8 to 9.
 *
 * Using onOpen instead of onCreate ensures tables exist even if database was created
 * before this callback was added.
 */
private class FtsTableCallback : RoomDatabase.Callback() {
    override fun onOpen(connection: SQLiteConnection) {
        super.onOpen(connection)

        // Create FTS5 tables if they don't exist
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
