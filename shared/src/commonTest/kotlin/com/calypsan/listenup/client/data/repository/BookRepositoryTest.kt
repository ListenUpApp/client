package com.calypsan.listenup.client.data.repository

import app.cash.turbine.test
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.sync.SyncManager
import dev.mokkery.MockMode
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

class BookRepositoryTest {

    @Test
    fun bookRepository_hasRequiredMethods() {
        val repositoryClass = BookRepository::class

        assertNotNull(repositoryClass.members.find { it.name == "observeBooks" })
        assertNotNull(repositoryClass.members.find { it.name == "refreshBooks" })
    }

    @Test
    fun observeBooks_returnsFlow() = runTest {
        val bookDao = mock<BookDao>(MockMode.autoUnit)
        val syncManager = mock<SyncManager>(MockMode.autoUnit)

        every { bookDao.observeAll() } returns flowOf(emptyList())

        val repository = BookRepository(bookDao, syncManager)

        repository.observeBooks().test {
            assertEquals(emptyList(), awaitItem())
            awaitComplete()
        }
    }
}
