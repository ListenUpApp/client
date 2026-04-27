package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.core.AppResult
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.sync.push.EndPlaybackSessionHandler
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.data.sync.push.PushSyncOrchestratorContract
import com.calypsan.listenup.client.domain.repository.DownloadRepository
import com.calypsan.listenup.client.domain.repository.ListeningEventRepository
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

// Shared construction helpers for PlaybackManager jvmTests.
//
// ProgressTracker is a final class — Mokkery cannot synthesise a mock — so every
// PlaybackManager test that exercises a code path touching ProgressTracker must
// construct a real instance. These helpers centralise that 9-param boilerplate.

/** Constructs a [ProgressTracker] whose dependencies are all interface mocks. */
fun buildProgressTracker(
    positionDao: PlaybackPositionDao = defaultPositionDao(),
    scope: CoroutineScope = CoroutineScope(Job()),
    positionRepository: PlaybackPositionRepository = defaultPositionRepository(),
): ProgressTracker {
    val stubSyncApi = mock<SyncApiContract>()
    return ProgressTracker(
        positionDao = positionDao,
        downloadRepository = mock<DownloadRepository>(),
        listeningEventRepository = mock<ListeningEventRepository>(),
        syncApi = stubSyncApi,
        pushSyncOrchestrator = mock<PushSyncOrchestratorContract>(),
        positionRepository = positionRepository,
        pendingOperationRepository = mock<PendingOperationRepositoryContract>(MockMode.autoUnit),
        endPlaybackSessionHandler = EndPlaybackSessionHandler(stubSyncApi),
        scope = scope,
    )
}

/** Returns a [PlaybackPositionRepository] stub that returns success for all writes and null for reads. */
fun defaultPositionRepository(): PlaybackPositionRepository {
    val repo: PlaybackPositionRepository = mock()
    everySuspend { repo.savePlaybackState(any(), any()) } returns AppResult.Success(Unit)
    everySuspend { repo.getEntity(any<BookId>()) } returns AppResult.Success(null)
    return repo
}

/** Returns a [PlaybackPositionDao] stub that returns null for all reads and Unit for writes. */
fun defaultPositionDao(): PlaybackPositionDao {
    val dao: PlaybackPositionDao = mock()
    everySuspend { dao.get(any()) } returns null
    everySuspend { dao.save(any()) } returns Unit
    return dao
}
