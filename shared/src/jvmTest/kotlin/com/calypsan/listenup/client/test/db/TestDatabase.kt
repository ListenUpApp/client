package com.calypsan.listenup.client.test.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import kotlinx.coroutines.Dispatchers

/**
 * Builds a fresh in-memory [ListenUpDatabase] for a single test. Uses [BundledSQLiteDriver]
 * to match production, so anything the schema/constraints/cascades enforce in the app also
 * holds in tests.
 *
 * Each call returns an isolated database — tests share no state.
 *
 * Scope: jvmTest only. Promoted to commonTest in W4 once cross-platform migration tests
 * need the same seam — see the W1 plan's checkpoint resolution on in-memory Room placement.
 *
 * Source: Room KMP testing guide — https://developer.android.com/kotlin/multiplatform/room.
 */
fun createInMemoryTestDatabase(): ListenUpDatabase =
    Room.inMemoryDatabaseBuilder<ListenUpDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
