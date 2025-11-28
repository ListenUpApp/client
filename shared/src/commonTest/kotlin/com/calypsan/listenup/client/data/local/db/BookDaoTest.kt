package com.calypsan.listenup.client.data.local.db

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BookDaoTest {

    // Note: These are contract tests. Integration tests with real Room DB
    // will be in androidTest. These verify the DAO interface contract.

    @Test
    fun bookDao_hasRequiredMethods() {
        // Verify DAO interface exists with expected methods
        val daoClass = BookDao::class

        assertNotNull(daoClass.members.find { it.name == "upsert" })
        assertNotNull(daoClass.members.find { it.name == "getById" })
        assertNotNull(daoClass.members.find { it.name == "observeAll" })
        assertNotNull(daoClass.members.find { it.name == "getPendingChanges" })
        assertNotNull(daoClass.members.find { it.name == "markSynced" })
        assertNotNull(daoClass.members.find { it.name == "markConflict" })
        assertNotNull(daoClass.members.find { it.name == "deleteById" })
    }
}
