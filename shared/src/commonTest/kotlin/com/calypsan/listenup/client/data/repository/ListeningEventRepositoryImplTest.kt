package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.AppResult
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.data.local.db.BookDuration
import com.calypsan.listenup.client.data.local.db.ListeningEventDao
import com.calypsan.listenup.client.data.local.db.ListeningEventEntity
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.TransactionRunner
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.sync.push.ListeningEventHandler
import com.calypsan.listenup.client.data.sync.push.ListeningEventPayload
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.test.db.passThroughTransactionRunner
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Tests for [ListeningEventRepositoryImpl].
 *
 * Covers:
 * - Happy path: both DAO upsert and pending-op queue happen inside atomically.
 * - Rollback on DAO failure: pending-op is NOT queued.
 * - Rollback on pending-op failure: DAO upsert was attempted but transaction rolls back.
 * - CancellationException is rethrown (EM-R1).
 * - Read methods delegate straight to the DAO.
 *
 * Uses Mokkery for [ListeningEventDao] and [PendingOperationRepositoryContract].
 * Uses [passThroughTransactionRunner] to execute the atomically block inline.
 */
class ListeningEventRepositoryImplTest {
    // ==================== Test Data Factories ====================

    private val testDeviceId = "device-test-123"
    private val testBookId = BookId("book-abc")

    private fun createMockDao(): ListeningEventDao = mock<ListeningEventDao>(MockMode.autoUnit)

    private fun createMockSyncApi(): SyncApiContract = mock<SyncApiContract>(MockMode.autoUnit)

    private fun createMockPositionDao(): PlaybackPositionDao = mock<PlaybackPositionDao>(MockMode.autoUnit)

    private fun createMockPendingOps(): PendingOperationRepositoryContract = mock<PendingOperationRepositoryContract>(MockMode.autoUnit)

    private fun createRepo(
        dao: ListeningEventDao = createMockDao(),
        pendingOps: PendingOperationRepositoryContract = createMockPendingOps(),
        handler: ListeningEventHandler = ListeningEventHandler(api = createMockSyncApi(), positionDao = createMockPositionDao()),
        transactionRunner: TransactionRunner = passThroughTransactionRunner(),
        deviceId: String = testDeviceId,
    ): ListeningEventRepositoryImpl =
        ListeningEventRepositoryImpl(
            listeningEventDao = dao,
            pendingOperationRepository = pendingOps,
            listeningEventHandler = handler,
            transactionRunner = transactionRunner,
            deviceId = deviceId,
        )

    private fun makeEntity(bookId: String = "book-abc"): ListeningEventEntity =
        ListeningEventEntity(
            id = "evt-test-1",
            bookId = bookId,
            startPositionMs = 0L,
            endPositionMs = 60_000L,
            startedAt = 1_000_000L,
            endedAt = 1_060_000L,
            playbackSpeed = 1.0f,
            deviceId = testDeviceId,
            syncState = SyncState.NOT_SYNCED,
            createdAt = 1_000_000L,
            source = "playback",
        )

    // ==================== queueListeningEvent() — Happy Path ====================

    @Test
    fun `queueListeningEvent writes both DAO and pending-op inside transaction`() =
        runTest {
            // Given
            val dao = createMockDao()
            val pendingOps = createMockPendingOps()
            val transactionRunner = passThroughTransactionRunner()
            val repo = createRepo(dao = dao, pendingOps = pendingOps, transactionRunner = transactionRunner)

            // When
            val result =
                repo.queueListeningEvent(
                    bookId = testBookId,
                    startPositionMs = 0L,
                    endPositionMs = 60_000L,
                    startedAt = 1_000_000L,
                    endedAt = 1_060_000L,
                    playbackSpeed = 1.5f,
                )

            // Then
            assertIs<AppResult.Success<Unit>>(result)
            verifySuspend(VerifyMode.exactly(1)) { dao.upsert(any()) }
            verifySuspend(VerifyMode.exactly(1)) {
                pendingOps.queue<ListeningEventPayload>(
                    type = OperationType.LISTENING_EVENT,
                    entityType = null,
                    entityId = null,
                    payload = any(),
                    handler = any(),
                )
            }
            verifySuspend(VerifyMode.exactly(1)) { transactionRunner.atomically(any<suspend () -> Any>()) }
        }

    @Test
    fun `queueListeningEvent entity has correct bookId and deviceId`() =
        runTest {
            // Given
            val dao = createMockDao()
            val captured = mutableListOf<ListeningEventEntity>()
            everySuspend { dao.upsert(any()) } calls { args ->
                captured.add(args.arg(0) as ListeningEventEntity)
                Unit
            }
            val repo = createRepo(dao = dao, deviceId = "my-device")

            // When
            repo.queueListeningEvent(
                bookId = BookId("book-xyz"),
                startPositionMs = 100L,
                endPositionMs = 200L,
                startedAt = 500L,
                endedAt = 600L,
                playbackSpeed = 2.0f,
            )

            // Then
            val entity = captured.single()
            assertEquals("book-xyz", entity.bookId)
            assertEquals("my-device", entity.deviceId)
            assertEquals(SyncState.NOT_SYNCED, entity.syncState)
            assertEquals("playback", entity.source)
            assertNotNull(entity.id)
        }

    @Test
    fun `queueListeningEvent upsert happens before pending-op queue`() =
        runTest {
            // Given
            val callOrder = mutableListOf<String>()
            val dao = createMockDao()
            val pendingOps = createMockPendingOps()
            everySuspend { dao.upsert(any()) } calls { _ ->
                callOrder.add("upsert")
                Unit
            }
            everySuspend { pendingOps.queue<ListeningEventPayload>(any(), any(), any(), any(), any()) } calls { _ ->
                callOrder.add("queue")
                Unit
            }
            val repo = createRepo(dao = dao, pendingOps = pendingOps)

            // When
            repo.queueListeningEvent(testBookId, 0L, 60_000L, 1_000_000L, 1_060_000L, 1.0f)

            // Then — entity upsert must precede pending-op queue so the ID is committed first
            assertEquals(listOf("upsert", "queue"), callOrder)
        }

    // ==================== queueListeningEvent() — Rollback on DAO Failure ====================

    @Test
    fun `queueListeningEvent returns Failure when DAO upsert throws`() =
        runTest {
            // Given
            val dao = createMockDao()
            val pendingOps = createMockPendingOps()
            everySuspend { dao.upsert(any()) } throws RuntimeException("DB write failed")
            val repo = createRepo(dao = dao, pendingOps = pendingOps)

            // When
            val result = repo.queueListeningEvent(testBookId, 0L, 60_000L, 1_000_000L, 1_060_000L, 1.0f)

            // Then — failure, and pending-op was never queued (transaction rolled back)
            assertIs<AppResult.Failure>(result)
            verifySuspend(VerifyMode.exactly(0)) {
                pendingOps.queue<ListeningEventPayload>(any(), any(), any(), any(), any())
            }
        }

    // ==================== queueListeningEvent() — Rollback on Pending-Op Failure ====================

    @Test
    fun `queueListeningEvent returns Failure when pending-op queue throws`() =
        runTest {
            // Given
            val dao = createMockDao()
            val pendingOps = createMockPendingOps()
            everySuspend {
                pendingOps.queue<ListeningEventPayload>(any(), any(), any(), any(), any())
            } throws RuntimeException("Queue write failed")
            val repo = createRepo(dao = dao, pendingOps = pendingOps)

            // When
            val result = repo.queueListeningEvent(testBookId, 0L, 60_000L, 1_000_000L, 1_060_000L, 1.0f)

            // Then — upsert was attempted, failure propagated, atomically wraps both
            assertIs<AppResult.Failure>(result)
            verifySuspend(VerifyMode.exactly(1)) { dao.upsert(any()) }
        }

    // ==================== queueListeningEvent() — CancellationException Rethrow ====================

    @Test
    fun `queueListeningEvent rethrows CancellationException from DAO upsert`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.upsert(any()) } throws CancellationException("cancelled")
            val repo = createRepo(dao = dao)

            // When / Then — suspendRunCatching must rethrow CancellationException (EM-R1)
            var caught: Throwable? = null
            try {
                repo.queueListeningEvent(testBookId, 0L, 60_000L, 1_000_000L, 1_060_000L, 1.0f)
            } catch (e: CancellationException) {
                caught = e
            }
            assertNotNull(caught)
        }

    @Test
    fun `queueListeningEvent rethrows CancellationException from pending-op queue`() =
        runTest {
            // Given
            val dao = createMockDao()
            val pendingOps = createMockPendingOps()
            everySuspend {
                pendingOps.queue<ListeningEventPayload>(any(), any(), any(), any(), any())
            } throws CancellationException("cancelled")
            val repo = createRepo(dao = dao, pendingOps = pendingOps)

            // When / Then
            var caught: Throwable? = null
            try {
                repo.queueListeningEvent(testBookId, 0L, 60_000L, 1_000_000L, 1_060_000L, 1.0f)
            } catch (e: CancellationException) {
                caught = e
            }
            assertNotNull(caught)
        }

    // ==================== Read Method Delegation Tests ====================

    @Test
    fun `observeEventsForBook delegates to DAO`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entities = listOf(makeEntity())
            every { dao.observeEventsForBook("book-abc") } returns flowOf(entities)
            val repo = createRepo(dao = dao)

            // When
            val result = repo.observeEventsForBook("book-abc").first()

            // Then
            assertEquals(entities, result)
        }

    @Test
    fun `observeEventsInRange delegates to DAO`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entities = listOf(makeEntity())
            every { dao.observeEventsInRange(100L, 200L) } returns flowOf(entities)
            val repo = createRepo(dao = dao)

            // When
            val result = repo.observeEventsInRange(100L, 200L).first()

            // Then
            assertEquals(entities, result)
        }

    @Test
    fun `observeEventsSince delegates to DAO`() =
        runTest {
            // Given
            val dao = createMockDao()
            val entities = listOf(makeEntity())
            every { dao.observeEventsSince(500L) } returns flowOf(entities)
            val repo = createRepo(dao = dao)

            // When
            val result = repo.observeEventsSince(500L).first()

            // Then
            assertEquals(entities, result)
        }

    @Test
    fun `getTotalDurationSince delegates to DAO`() =
        runTest {
            // Given
            val dao = createMockDao()
            everySuspend { dao.getTotalDurationSince(1_000L) } returns 3_600_000L
            val repo = createRepo(dao = dao)

            // When
            val result = repo.getTotalDurationSince(1_000L)

            // Then
            assertEquals(3_600_000L, result)
        }

    @Test
    fun `observeTotalDurationSince delegates to DAO`() =
        runTest {
            // Given
            val dao = createMockDao()
            every { dao.observeTotalDurationSince(0L) } returns flowOf(7_200_000L)
            val repo = createRepo(dao = dao)

            // When
            val result = repo.observeTotalDurationSince(0L).first()

            // Then
            assertEquals(7_200_000L, result)
        }

    @Test
    fun `observeDistinctBooksSince delegates to DAO`() =
        runTest {
            // Given
            val dao = createMockDao()
            every { dao.observeDistinctBooksSince(0L) } returns flowOf(5)
            val repo = createRepo(dao = dao)

            // When
            val result = repo.observeDistinctBooksSince(0L).first()

            // Then
            assertEquals(5, result)
        }

    @Test
    fun `observeDistinctDaysSince delegates to DAO`() =
        runTest {
            // Given
            val dao = createMockDao()
            val days = listOf(1_000_000L, 2_000_000L)
            every { dao.observeDistinctDaysSince(0L) } returns flowOf(days)
            val repo = createRepo(dao = dao)

            // When
            val result = repo.observeDistinctDaysSince(0L).first()

            // Then
            assertEquals(days, result)
        }

    @Test
    fun `getDistinctDaysWithActivity delegates to DAO`() =
        runTest {
            // Given
            val dao = createMockDao()
            val days = listOf(1_000_000L, 2_000_000L)
            everySuspend { dao.getDistinctDaysWithActivity(0L) } returns days
            val repo = createRepo(dao = dao)

            // When
            val result = repo.getDistinctDaysWithActivity(0L)

            // Then
            assertEquals(days, result)
        }

    @Test
    fun `getDurationByBook delegates to DAO`() =
        runTest {
            // Given
            val dao = createMockDao()
            val durations = listOf(BookDuration(bookId = "book-abc", totalMs = 900_000L))
            everySuspend { dao.getDurationByBook(100L, 200L) } returns durations
            val repo = createRepo(dao = dao)

            // When
            val result = repo.getDurationByBook(100L, 200L)

            // Then
            assertEquals(durations, result)
        }
}
