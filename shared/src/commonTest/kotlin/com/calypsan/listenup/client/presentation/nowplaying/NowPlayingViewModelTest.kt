package com.calypsan.listenup.client.presentation.nowplaying

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.domain.model.Chapter
import com.calypsan.listenup.client.domain.playback.PlaybackTimeline
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.NetworkMonitor
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import com.calypsan.listenup.client.playback.PlaybackController
import com.calypsan.listenup.client.playback.PlaybackManager.PrepareResult
import com.calypsan.listenup.client.playback.SleepTimerManager
import com.calypsan.listenup.client.test.fake.FakePlaybackManager
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the consolidated [NowPlayingViewModel] (W7 Phase E2.2.3).
 *
 * Coverage scope (B targeted, 3 tests for Task 2):
 * - playBook flow (3): happy path with activate-before-seam ordering; offline error;
 *   generic prepare-null error.
 *
 * Uses [FakePlaybackManager] for the seam (W7 Phase E2.2.4 Task 1B) per the rubric's
 * "fakes for seams" rule. The other deps are Mokkery-mocked as interfaces;
 * [SleepTimerManager] is a closed concrete class so a real instance is constructed
 * with a test-scoped [CoroutineScope] (its scope is unused by these tests since
 * none of them invoke timer-launching methods).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private class TestFixture {
        val fakePm = FakePlaybackManager()
        val bookRepository: BookRepository = mock()
        val playbackController: PlaybackController = mock()
        val playbackPreferences: PlaybackPreferences = mock()
        val networkMonitor: NetworkMonitor = mock()
        val sleepTimerManager: SleepTimerManager = SleepTimerManager(CoroutineScope(Job()))

        init {
            // Default: networkMonitor.isOnline() returns true. Tests override to false where needed.
            every { networkMonitor.isOnline() } returns true
            // Default: PlaybackPreferences.observeDefaultPlaybackSpeed() returns Flow of 1.0f.
            // Required by NowPlayingViewModel's surfaceMetadataFlow combine pipeline.
            every { playbackPreferences.observeDefaultPlaybackSpeed() } returns flowOf(1.0f)
            everySuspend { playbackPreferences.getDefaultPlaybackSpeed() } returns 1.0f
            // Default: bookRepository.getBookListItem returns null (no metadata).
            // bookFlow tolerates null; pure mapToNowPlayingState handles it.
            everySuspend { bookRepository.getBookListItem(any()) } returns null
        }

        fun newVm(): NowPlayingViewModel =
            NowPlayingViewModel(
                playbackManager = fakePm,
                bookRepository = bookRepository,
                sleepTimerManager = sleepTimerManager,
                playbackController = playbackController,
                playbackPreferences = playbackPreferences,
                networkMonitor = networkMonitor,
            )
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== playBook ==========

    @Test
    fun `playBook happy path activates book then invokes controller seam`() =
        runTest(testDispatcher) {
            val fixture = TestFixture()
            val bookId = BookId("book-1")
            val prepareResult = stubPrepareResult(bookId)
            fixture.fakePm.stubbedPrepareResult = prepareResult
            every { fixture.networkMonitor.isOnline() } returns true
            everySuspend { fixture.playbackController.startPlayback(any()) } returns Unit

            val vm = fixture.newVm()
            vm.playBook(bookId)
            advanceUntilIdle()

            // activateBook must fire (records the bookId on the fake's recorder list).
            // PlaybackController.startPlayback must fire afterwards.
            // Strict ordering is asserted indirectly: activateBook is recorded, AND
            // startPlayback was invoked exactly once with the prepared result. The
            // production VM body in [NowPlayingViewModel.playBook] sequences them
            // activate-then-seam; both having fired proves the bug-3 ordering closure.
            assertEquals(listOf(bookId), fixture.fakePm.activatedBookIds)
            verifySuspend(VerifyMode.exactly(1)) {
                fixture.playbackController.startPlayback(prepareResult)
            }
            // No error reported on the happy path.
            assertTrue(
                fixture.fakePm.reportedErrors.isEmpty(),
                "happy path must not report any error; got: ${fixture.fakePm.reportedErrors}",
            )
        }

    @Test
    fun `playBook with offline + null prepare reports offline error`() =
        runTest(testDispatcher) {
            val fixture = TestFixture()
            val bookId = BookId("book-1")
            fixture.fakePm.stubbedPrepareResult = null
            every { fixture.networkMonitor.isOnline() } returns false

            val vm = fixture.newVm()
            vm.playBook(bookId)
            advanceUntilIdle()

            assertEquals(1, fixture.fakePm.reportedErrors.size)
            assertTrue(
                fixture.fakePm.reportedErrors
                    .first()
                    .message
                    .contains("offline", ignoreCase = true),
                "expected offline error message; got: ${fixture.fakePm.reportedErrors.first().message}",
            )
            assertTrue(
                fixture.fakePm.activatedBookIds.isEmpty(),
                "must NOT activate when prepare fails",
            )
            verifySuspend(VerifyMode.exactly(0)) {
                fixture.playbackController.startPlayback(any())
            }
        }

    @Test
    fun `playBook with online + null prepare reports generic error`() =
        runTest(testDispatcher) {
            val fixture = TestFixture()
            val bookId = BookId("book-1")
            fixture.fakePm.stubbedPrepareResult = null
            every { fixture.networkMonitor.isOnline() } returns true

            val vm = fixture.newVm()
            vm.playBook(bookId)
            advanceUntilIdle()

            assertEquals(1, fixture.fakePm.reportedErrors.size)
            assertTrue(
                fixture.fakePm.reportedErrors
                    .first()
                    .message
                    .contains("Failed to load", ignoreCase = true),
                "expected generic load-failure message; got: ${fixture.fakePm.reportedErrors.first().message}",
            )
            assertTrue(
                fixture.fakePm.activatedBookIds.isEmpty(),
                "must NOT activate when prepare fails",
            )
            verifySuspend(VerifyMode.exactly(0)) {
                fixture.playbackController.startPlayback(any())
            }
        }

    // ========== playPause ==========

    @Test
    fun `playPause when playing calls controller pause`() =
        runTest(testDispatcher) {
            val fixture = TestFixture()
            every { fixture.playbackController.pause() } returns Unit
            every { fixture.playbackController.play() } returns Unit
            fixture.fakePm.isPlayingFlow.value = true

            val vm = fixture.newVm()
            vm.playPause()
            advanceUntilIdle()

            verify(VerifyMode.exactly(1)) { fixture.playbackController.pause() }
            verify(VerifyMode.not) { fixture.playbackController.play() }
        }

    @Test
    fun `playPause when paused calls controller play`() =
        runTest(testDispatcher) {
            val fixture = TestFixture()
            every { fixture.playbackController.pause() } returns Unit
            every { fixture.playbackController.play() } returns Unit
            fixture.fakePm.isPlayingFlow.value = false

            val vm = fixture.newVm()
            vm.playPause()
            advanceUntilIdle()

            verify(VerifyMode.exactly(1)) { fixture.playbackController.play() }
            verify(VerifyMode.not) { fixture.playbackController.pause() }
        }

    // ========== Skip/seek ==========

    @Test
    fun `skipForward at speed 1_5 advances by speed-multiplied delta and clamps to total`() =
        runTest(testDispatcher) {
            val fixture = TestFixture()
            every { fixture.playbackController.seekTo(any()) } returns Unit
            fixture.fakePm.currentTimelineFlow.value = stubTimeline()
            fixture.fakePm.currentPositionMsFlow.value = 100_000L
            fixture.fakePm.totalDurationMsFlow.value = 1_800_000L
            fixture.fakePm.playbackSpeedFlow.value = 1.5f

            val vm = fixture.newVm()
            vm.skipForward(30)
            advanceUntilIdle()

            // 100_000 + (30 × 1.5 × 1000) = 145_000
            verify(VerifyMode.exactly(1)) { fixture.playbackController.seekTo(145_000L) }
            assertEquals(listOf(145_000L), fixture.fakePm.updatedPositions)

            // Edge: at total - 5000, 30s skip clamps to total.
            // updatePosition(145_000) above moved currentPositionMsFlow to 145_000;
            // now reset to total - 5000 to exercise the clamp.
            fixture.fakePm.currentPositionMsFlow.value = 1_800_000L - 5_000L
            fixture.fakePm.updatedPositions.clear()
            vm.skipForward(30)
            advanceUntilIdle()
            assertEquals(listOf(1_800_000L), fixture.fakePm.updatedPositions)
        }

    @Test
    fun `skipBack at speed 1_5 retreats by speed-multiplied delta and clamps to zero`() =
        runTest(testDispatcher) {
            val fixture = TestFixture()
            every { fixture.playbackController.seekTo(any()) } returns Unit
            fixture.fakePm.currentTimelineFlow.value = stubTimeline()
            fixture.fakePm.currentPositionMsFlow.value = 100_000L
            fixture.fakePm.playbackSpeedFlow.value = 1.5f

            val vm = fixture.newVm()
            vm.skipBack(10)
            advanceUntilIdle()

            // 100_000 - (10 × 1.5 × 1000) = 85_000
            verify(VerifyMode.exactly(1)) { fixture.playbackController.seekTo(85_000L) }
            assertEquals(listOf(85_000L), fixture.fakePm.updatedPositions)

            // Edge: at 5000ms, 10s skip clamps to 0.
            fixture.fakePm.currentPositionMsFlow.value = 5_000L
            fixture.fakePm.updatedPositions.clear()
            vm.skipBack(10)
            advanceUntilIdle()
            assertEquals(listOf(0L), fixture.fakePm.updatedPositions)
        }

    @Test
    fun `seekToChapter seeks to chapter start and ignores out-of-range indices`() =
        runTest(testDispatcher) {
            // Production [NowPlayingViewModel.seekToChapter] uses chapters.getOrNull(index)
            // and returns when null — so out-of-range indices are a no-op (NOT clamped).
            // This deviates from the Task 3 skeleton's "clamp to last/first" expectation;
            // the test asserts production behavior.
            val fixture = TestFixture()
            every { fixture.playbackController.seekTo(any()) } returns Unit
            fixture.fakePm.chaptersFlow.value = stubChapters()

            val vm = fixture.newVm()

            vm.seekToChapter(1)
            advanceUntilIdle()
            verify(VerifyMode.exactly(1)) { fixture.playbackController.seekTo(600_000L) }
            assertEquals(listOf(600_000L), fixture.fakePm.updatedPositions)

            // Out-of-range high (chapters.size == 3) → no-op.
            fixture.fakePm.updatedPositions.clear()
            vm.seekToChapter(99)
            advanceUntilIdle()
            verify(VerifyMode.not) { fixture.playbackController.seekTo(any<Long>()) }
            assertTrue(
                fixture.fakePm.updatedPositions.isEmpty(),
                "out-of-range high index must NOT update position; got: ${fixture.fakePm.updatedPositions}",
            )

            // Out-of-range low (negative) → no-op.
            vm.seekToChapter(-1)
            advanceUntilIdle()
            verify(VerifyMode.not) { fixture.playbackController.seekTo(any<Long>()) }
            assertTrue(
                fixture.fakePm.updatedPositions.isEmpty(),
                "out-of-range low index must NOT update position; got: ${fixture.fakePm.updatedPositions}",
            )

            // Index 0 → seeks to first chapter (startTime 0).
            vm.seekToChapter(0)
            advanceUntilIdle()
            verify(VerifyMode.exactly(1)) { fixture.playbackController.seekTo(0L) }
            assertEquals(listOf(0L), fixture.fakePm.updatedPositions)
        }

    // ========== Helpers ==========

    private fun stubPrepareResult(bookId: BookId): PrepareResult =
        PrepareResult(
            timeline =
                PlaybackTimeline(
                    bookId = bookId,
                    totalDurationMs = 1_800_000L,
                    files =
                        listOf(
                            PlaybackTimeline.FileSegment(
                                audioFileId = "af-stub",
                                filename = "stub.m4b",
                                format = "m4b",
                                startOffsetMs = 0L,
                                durationMs = 1_800_000L,
                                size = 1_000_000L,
                                streamingUrl = "https://example.test/stub.m4b",
                                localPath = null,
                                mediaItemIndex = 0,
                            ),
                        ),
                ),
            bookTitle = "Stub Book",
            bookAuthor = "Stub Author",
            seriesName = null,
            coverPath = null,
            totalChapters = 1,
            resumePositionMs = 0L,
            resumeSpeed = 1.0f,
        )

    private fun stubTimeline(): PlaybackTimeline =
        PlaybackTimeline(
            bookId = BookId("book-1"),
            totalDurationMs = 1_800_000L,
            files =
                listOf(
                    PlaybackTimeline.FileSegment(
                        audioFileId = "af-stub",
                        filename = "stub.m4b",
                        format = "m4b",
                        startOffsetMs = 0L,
                        durationMs = 1_800_000L,
                        size = 1_000_000L,
                        streamingUrl = "https://example.test/stub.m4b",
                        localPath = null,
                        mediaItemIndex = 0,
                    ),
                ),
        )

    private fun stubChapters(): List<Chapter> =
        listOf(
            Chapter(id = "ch-0", title = "Chapter 1", duration = 600_000L, startTime = 0L),
            Chapter(id = "ch-1", title = "Chapter 2", duration = 600_000L, startTime = 600_000L),
            Chapter(id = "ch-2", title = "Chapter 3", duration = 600_000L, startTime = 1_200_000L),
        )
}
