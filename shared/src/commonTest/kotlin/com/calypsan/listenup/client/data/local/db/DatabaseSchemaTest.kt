package com.calypsan.listenup.client.data.local.db

import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Integration test for database schema.
 * Verifies the database class has required DAO methods.
 */
class DatabaseSchemaTest {

    @Test
    fun database_hasBookDao() {
        val dbClass = ListenUpDatabase::class
        assertNotNull(dbClass.members.find { it.name == "bookDao" })
    }

    @Test
    fun database_hasSyncDao() {
        val dbClass = ListenUpDatabase::class
        assertNotNull(dbClass.members.find { it.name == "syncDao" })
    }
}
