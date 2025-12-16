package com.calypsan.listenup.client.data.local.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSHomeDirectory

/**
 * iOS-specific database module.
 * Provides Room database configured for iOS with proper file location in app container.
 *
 * Note: Uses Dispatchers.Default instead of Dispatchers.IO since IO is internal on Native platforms.
 */
actual val platformDatabaseModule: Module =
    module {
        single {
            val dbFile = "${NSHomeDirectory()}/listenup.db"

            Room
                .databaseBuilder<ListenUpDatabase>(
                    name = dbFile,
                ).setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Default)
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
                ).fallbackToDestructiveMigration(false)
                .build()
        }
    }
