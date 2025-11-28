package com.calypsan.listenup.client.data.remote

import kotlin.test.Test
import kotlin.test.assertNotNull

class SyncApiTest {

    @Test
    fun syncApi_hasRequiredMethods() {
        val apiClass = SyncApi::class

        assertNotNull(apiClass.members.find { it.name == "getManifest" })
        assertNotNull(apiClass.members.find { it.name == "getBooks" })
    }
}
