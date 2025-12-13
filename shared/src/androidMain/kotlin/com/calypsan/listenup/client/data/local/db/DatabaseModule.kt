package com.calypsan.listenup.client.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
                ).fallbackToDestructiveMigration(false)
                .build()
        }
    }
