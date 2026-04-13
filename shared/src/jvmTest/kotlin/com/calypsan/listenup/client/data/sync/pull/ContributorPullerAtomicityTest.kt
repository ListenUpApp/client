package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.ContributorAliasDao
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.RoomTransactionRunner
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.ContributorResponse
import com.calypsan.listenup.client.data.remote.model.SyncContributorsResponse
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves [ContributorPuller.pull] is atomic — when the alias-junction write
 * step fails, the contributor upsert and any prior alias rows roll back so
 * the DB never holds a partial contributor/alias state.
 *
 * Uses a real in-memory [ListenUpDatabase]; the alias DAO is mocked to throw
 * on insert, forcing the failure after the contributor upsert has landed.
 */
class ContributorPullerAtomicityTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `rollback when alias insert throws mid-batch`() =
        runTest {
            val syncApi: SyncApiContract = mock()
            val imageDownloader: ImageDownloaderContract = mock()
            val failingAliasDao: ContributorAliasDao = mock()

            val contributorResponse =
                ContributorResponse(
                    id = "contrib-rollback",
                    name = "Rollback Test",
                    sortName = null,
                    asin = null,
                    biography = null,
                    imageUrl = null,
                    imageBlurHash = null,
                    aliases = listOf("Alias One", "Alias Two"),
                    website = null,
                    birthDate = null,
                    deathDate = null,
                    createdAt = "2024-01-01T00:00:00Z",
                    updatedAt = "2024-01-01T00:00:00Z",
                )

            everySuspend { syncApi.getContributors(any(), any(), any()) } returns
                Success(
                    SyncContributorsResponse(
                        contributors = listOf(contributorResponse),
                        deletedContributorIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            everySuspend { failingAliasDao.deleteForContributor(any()) } returns Unit
            everySuspend { failingAliasDao.insertAll(any()) } throws
                RuntimeException("boom — alias insert failed")

            val puller =
                ContributorPuller(
                    transactionRunner = RoomTransactionRunner(db),
                    syncApi = syncApi,
                    contributorDao = db.contributorDao(),
                    contributorAliasDao = failingAliasDao,
                    imageDownloader = imageDownloader,
                    scope = CoroutineScope(Job()),
                )

            assertFailsWith<RuntimeException> { puller.pull(null) {} }

            assertEquals(
                0,
                db.contributorDao().getAll().size,
                "contributor upsert must roll back when alias insert throws",
            )
        }
}
