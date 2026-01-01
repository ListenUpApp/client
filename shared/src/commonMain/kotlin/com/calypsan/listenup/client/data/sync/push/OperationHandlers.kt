package com.calypsan.listenup.client.data.sync.push

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PendingOperationEntity
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.calypsan.listenup.client.data.remote.BookApiContract
import com.calypsan.listenup.client.data.remote.BookUpdateRequest
import com.calypsan.listenup.client.data.remote.ContributorApiContract
import com.calypsan.listenup.client.data.remote.ListeningEventRequest
import com.calypsan.listenup.client.data.remote.SeriesApiContract
import com.calypsan.listenup.client.data.remote.SeriesUpdateRequest
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.UpdateContributorRequest
import com.calypsan.listenup.client.data.remote.UserPreferencesApiContract
import com.calypsan.listenup.client.data.remote.UserPreferencesRequest
import kotlinx.serialization.json.Json

import io.github.oshai.kotlinlogging.KotlinLogging

private val json = Json { ignoreUnknownKeys = true }
private val logger = KotlinLogging.logger {}

/**
 * Handler for BOOK_UPDATE operations.
 * Coalesces updates to the same book, merging fields.
 */
class BookUpdateHandler(
    private val api: BookApiContract,
) : OperationHandler<BookUpdatePayload> {
    override val operationType = OperationType.BOOK_UPDATE

    override fun parsePayload(json: String): BookUpdatePayload = Json.decodeFromString(json)

    override fun serializePayload(payload: BookUpdatePayload): String = Json.encodeToString(payload)

    override fun tryCoalesce(
        existing: PendingOperationEntity,
        existingPayload: BookUpdatePayload,
        newPayload: BookUpdatePayload,
    ): BookUpdatePayload? {
        if (existing.operationType != OperationType.BOOK_UPDATE) return null
        return BookUpdatePayload(
            title = newPayload.title ?: existingPayload.title,
            subtitle = newPayload.subtitle ?: existingPayload.subtitle,
            description = newPayload.description ?: existingPayload.description,
            publisher = newPayload.publisher ?: existingPayload.publisher,
            publishYear = newPayload.publishYear ?: existingPayload.publishYear,
            language = newPayload.language ?: existingPayload.language,
            isbn = newPayload.isbn ?: existingPayload.isbn,
            asin = newPayload.asin ?: existingPayload.asin,
            abridged = newPayload.abridged ?: existingPayload.abridged,
        )
    }

    override suspend fun execute(
        operation: PendingOperationEntity,
        payload: BookUpdatePayload,
    ): Result<Unit> {
        val request =
            BookUpdateRequest(
                title = payload.title,
                subtitle = payload.subtitle,
                description = payload.description,
                publisher = payload.publisher,
                publishYear = payload.publishYear,
                language = payload.language,
                isbn = payload.isbn,
                asin = payload.asin,
                abridged = payload.abridged,
            )
        return when (val result = api.updateBook(operation.entityId!!, request)) {
            is Success -> Success(Unit)
            is Failure -> Failure(result.exception)
        }
    }
}

/**
 * Handler for CONTRIBUTOR_UPDATE operations.
 * Coalesces updates to the same contributor.
 */
class ContributorUpdateHandler(
    private val api: ContributorApiContract,
) : OperationHandler<ContributorUpdatePayload> {
    override val operationType = OperationType.CONTRIBUTOR_UPDATE

    override fun parsePayload(json: String): ContributorUpdatePayload = Json.decodeFromString(json)

    override fun serializePayload(payload: ContributorUpdatePayload): String = Json.encodeToString(payload)

    override fun tryCoalesce(
        existing: PendingOperationEntity,
        existingPayload: ContributorUpdatePayload,
        newPayload: ContributorUpdatePayload,
    ): ContributorUpdatePayload? {
        if (existing.operationType != OperationType.CONTRIBUTOR_UPDATE) return null
        return ContributorUpdatePayload(
            name = newPayload.name ?: existingPayload.name,
            biography = newPayload.biography ?: existingPayload.biography,
            website = newPayload.website ?: existingPayload.website,
            birthDate = newPayload.birthDate ?: existingPayload.birthDate,
            deathDate = newPayload.deathDate ?: existingPayload.deathDate,
            aliases = newPayload.aliases ?: existingPayload.aliases,
        )
    }

    override suspend fun execute(
        operation: PendingOperationEntity,
        payload: ContributorUpdatePayload,
    ): Result<Unit> {
        // API requires all fields, so we need the full update
        val request =
            UpdateContributorRequest(
                name = payload.name ?: "",
                biography = payload.biography,
                website = payload.website,
                birthDate = payload.birthDate,
                deathDate = payload.deathDate,
                aliases = payload.aliases ?: emptyList(),
            )
        return when (val result = api.updateContributor(operation.entityId!!, request)) {
            is Success -> Success(Unit)
            is Failure -> Failure(result.exception)
        }
    }
}

/**
 * Handler for SERIES_UPDATE operations.
 * Coalesces updates to the same series.
 */
class SeriesUpdateHandler(
    private val api: SeriesApiContract,
) : OperationHandler<SeriesUpdatePayload> {
    override val operationType = OperationType.SERIES_UPDATE

    override fun parsePayload(json: String): SeriesUpdatePayload = Json.decodeFromString(json)

    override fun serializePayload(payload: SeriesUpdatePayload): String = Json.encodeToString(payload)

    override fun tryCoalesce(
        existing: PendingOperationEntity,
        existingPayload: SeriesUpdatePayload,
        newPayload: SeriesUpdatePayload,
    ): SeriesUpdatePayload? {
        if (existing.operationType != OperationType.SERIES_UPDATE) return null
        return SeriesUpdatePayload(
            name = newPayload.name ?: existingPayload.name,
            description = newPayload.description ?: existingPayload.description,
        )
    }

    override suspend fun execute(
        operation: PendingOperationEntity,
        payload: SeriesUpdatePayload,
    ): Result<Unit> {
        val request =
            SeriesUpdateRequest(
                name = payload.name,
                description = payload.description,
            )
        return when (val result = api.updateSeries(operation.entityId!!, request)) {
            is Success -> Success(Unit)
            is Failure -> Failure(result.exception)
        }
    }
}

/**
 * Handler for SET_BOOK_CONTRIBUTORS operations.
 * Replaces entirely - newer operation wins.
 */
class SetBookContributorsHandler(
    private val api: BookApiContract,
) : OperationHandler<SetBookContributorsPayload> {
    override val operationType = OperationType.SET_BOOK_CONTRIBUTORS

    override fun parsePayload(json: String): SetBookContributorsPayload = Json.decodeFromString(json)

    override fun serializePayload(payload: SetBookContributorsPayload): String = Json.encodeToString(payload)

    override fun tryCoalesce(
        existing: PendingOperationEntity,
        existingPayload: SetBookContributorsPayload,
        newPayload: SetBookContributorsPayload,
    ): SetBookContributorsPayload? {
        if (existing.operationType != OperationType.SET_BOOK_CONTRIBUTORS) return null
        return newPayload // Full replacement - new wins
    }

    override suspend fun execute(
        operation: PendingOperationEntity,
        payload: SetBookContributorsPayload,
    ): Result<Unit> {
        val contributors =
            payload.contributors.map { c ->
                com.calypsan.listenup.client.data.remote.ContributorInput(
                    name = c.name,
                    roles = c.roles,
                )
            }
        return when (val result = api.setBookContributors(operation.entityId!!, contributors)) {
            is Success -> Success(Unit)
            is Failure -> Failure(result.exception)
        }
    }
}

/**
 * Handler for SET_BOOK_SERIES operations.
 * Replaces entirely - newer operation wins.
 */
class SetBookSeriesHandler(
    private val api: BookApiContract,
) : OperationHandler<SetBookSeriesPayload> {
    override val operationType = OperationType.SET_BOOK_SERIES

    override fun parsePayload(json: String): SetBookSeriesPayload = Json.decodeFromString(json)

    override fun serializePayload(payload: SetBookSeriesPayload): String = Json.encodeToString(payload)

    override fun tryCoalesce(
        existing: PendingOperationEntity,
        existingPayload: SetBookSeriesPayload,
        newPayload: SetBookSeriesPayload,
    ): SetBookSeriesPayload? {
        if (existing.operationType != OperationType.SET_BOOK_SERIES) return null
        return newPayload // Full replacement - new wins
    }

    override suspend fun execute(
        operation: PendingOperationEntity,
        payload: SetBookSeriesPayload,
    ): Result<Unit> {
        val series =
            payload.series.map { s ->
                com.calypsan.listenup.client.data.remote.SeriesInput(
                    name = s.name,
                    sequence = s.sequence,
                )
            }
        return when (val result = api.setBookSeries(operation.entityId!!, series)) {
            is Success -> Success(Unit)
            is Failure -> Failure(result.exception)
        }
    }
}

/**
 * Handler for MERGE_CONTRIBUTOR operations.
 * Never coalesces - each merge is unique and order matters.
 */
class MergeContributorHandler(
    private val api: ContributorApiContract,
) : OperationHandler<MergeContributorPayload> {
    override val operationType = OperationType.MERGE_CONTRIBUTOR

    override fun parsePayload(json: String): MergeContributorPayload = Json.decodeFromString(json)

    override fun serializePayload(payload: MergeContributorPayload): String = Json.encodeToString(payload)

    override fun tryCoalesce(
        existing: PendingOperationEntity,
        existingPayload: MergeContributorPayload,
        newPayload: MergeContributorPayload,
    ): MergeContributorPayload? = null // Merge operations never coalesce

    override suspend fun execute(
        operation: PendingOperationEntity,
        payload: MergeContributorPayload,
    ): Result<Unit> =
        when (val result = api.mergeContributor(payload.targetId, payload.sourceId)) {
            is Success -> Success(Unit)
            is Failure -> Failure(result.exception)
        }
}

/**
 * Handler for UNMERGE_CONTRIBUTOR operations.
 * Never coalesces - each unmerge is unique and order matters.
 */
class UnmergeContributorHandler(
    private val api: ContributorApiContract,
) : OperationHandler<UnmergeContributorPayload> {
    override val operationType = OperationType.UNMERGE_CONTRIBUTOR

    override fun parsePayload(json: String): UnmergeContributorPayload = Json.decodeFromString(json)

    override fun serializePayload(payload: UnmergeContributorPayload): String = Json.encodeToString(payload)

    override fun tryCoalesce(
        existing: PendingOperationEntity,
        existingPayload: UnmergeContributorPayload,
        newPayload: UnmergeContributorPayload,
    ): UnmergeContributorPayload? = null // Unmerge operations never coalesce

    override suspend fun execute(
        operation: PendingOperationEntity,
        payload: UnmergeContributorPayload,
    ): Result<Unit> =
        when (val result = api.unmergeContributor(payload.contributorId, payload.aliasName)) {
            is Success -> Success(Unit)
            is Failure -> Failure(result.exception)
        }
}

/**
 * Handler for LISTENING_EVENT operations.
 * Never coalesces - each event is unique.
 * Batches together for efficient submission.
 *
 * After successful submission, marks the playback position as synced
 * so other parts of the system know the server has the latest data.
 */
class ListeningEventHandler(
    private val api: SyncApiContract,
    private val positionDao: PlaybackPositionDao,
) : OperationHandler<ListeningEventPayload> {
    override val operationType = OperationType.LISTENING_EVENT

    override fun parsePayload(json: String): ListeningEventPayload = Json.decodeFromString(json)

    override fun serializePayload(payload: ListeningEventPayload): String = Json.encodeToString(payload)

    override fun tryCoalesce(
        existing: PendingOperationEntity,
        existingPayload: ListeningEventPayload,
        newPayload: ListeningEventPayload,
    ): ListeningEventPayload? = null // Listening events never coalesce

    override fun batchKey(payload: ListeningEventPayload): String = "listening_events"

    override suspend fun execute(
        operation: PendingOperationEntity,
        payload: ListeningEventPayload,
    ): Result<Unit> {
        // Individual execution (fallback) - submit as single-item batch
        val request = listOf(payload.toApiRequest())
        return when (val result = api.submitListeningEvents(request)) {
            is Success -> {
                if (payload.id in result.data.acknowledged) {
                    // Mark position as synced so other parts of the system know
                    // the server has the latest data for this book
                    markPositionSynced(payload.bookId)
                    Success(Unit)
                } else {
                    Failure(Exception("Event not acknowledged by server"))
                }
            }

            is Failure -> {
                Failure(result.exception)
            }
        }
    }

    override suspend fun executeBatch(
        operations: List<Pair<PendingOperationEntity, ListeningEventPayload>>,
    ): Map<String, Result<Unit>> {
        logger.info { "📤 LISTENING EVENTS: Submitting ${operations.size} events to server" }
        val requests = operations.map { (_, payload) -> payload.toApiRequest() }
        logger.info { "📤 LISTENING EVENTS: Event IDs: ${requests.map { it.id }}" }

        return when (val result = api.submitListeningEvents(requests)) {
            is Success -> {
                logger.info { "📤 LISTENING EVENTS: Server acknowledged ${result.data.acknowledged.size} events, failed ${result.data.failed.size}" }
                val acknowledged = result.data.acknowledged.toSet()
                operations.associate { (op, payload) ->
                    op.id to
                        if (payload.id in acknowledged) {
                            // Mark position as synced for this book
                            markPositionSynced(payload.bookId)
                            Success(Unit)
                        } else {
                            Failure(Exception("Event not acknowledged"))
                        }
                }
            }

            is Failure -> {
                logger.error(result.exception) { "📤 LISTENING EVENTS: Submission failed" }
                operations.associate { (op, _) -> op.id to Failure(result.exception) }
            }
        }
    }

    /**
     * Marks the playback position for a book as synced.
     * Called after successful event submission to indicate the server has the latest data.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun markPositionSynced(bookId: String) {
        positionDao.markSynced(
            bookId = BookId(bookId),
            syncedAt = Clock.System.now().toEpochMilliseconds(),
        )
    }

    private fun ListeningEventPayload.toApiRequest() =
        ListeningEventRequest(
            id = id,
            book_id = bookId,
            start_position_ms = startPositionMs,
            end_position_ms = endPositionMs,
            started_at = startedAt,
            ended_at = endedAt,
            playback_speed = playbackSpeed,
            device_id = deviceId,
        )
}

/**
 * Handler for PLAYBACK_POSITION operations.
 * Coalesces by book - only the latest position matters.
 */
class PlaybackPositionHandler(
    private val api: SyncApiContract,
) : OperationHandler<PlaybackPositionPayload> {
    override val operationType = OperationType.PLAYBACK_POSITION

    override fun parsePayload(json: String): PlaybackPositionPayload = Json.decodeFromString(json)

    override fun serializePayload(payload: PlaybackPositionPayload): String = Json.encodeToString(payload)

    override fun tryCoalesce(
        existing: PendingOperationEntity,
        existingPayload: PlaybackPositionPayload,
        newPayload: PlaybackPositionPayload,
    ): PlaybackPositionPayload? {
        if (existing.operationType != OperationType.PLAYBACK_POSITION) return null
        return newPayload // Latest position wins
    }

    override suspend fun execute(
        operation: PendingOperationEntity,
        payload: PlaybackPositionPayload,
    ): Result<Unit> {
        // Position sync happens via listening events - the position is derived server-side
        // from the events. This is a proactive hint to the server.
        // For now, we submit as a minimal listening event.
        val event =
            ListeningEventRequest(
                id = operation.id,
                book_id = payload.bookId,
                start_position_ms = payload.positionMs,
                end_position_ms = payload.positionMs,
                started_at = payload.updatedAt,
                ended_at = payload.updatedAt,
                playback_speed = payload.playbackSpeed,
                device_id = "position_sync",
            )
        return when (val result = api.submitListeningEvents(listOf(event))) {
            is Success -> Success(Unit)
            is Failure -> Failure(result.exception)
        }
    }
}

/**
 * Handler for USER_PREFERENCES operations.
 * Always coalesces - only care about final state.
 */
class UserPreferencesHandler(
    private val api: UserPreferencesApiContract,
) : OperationHandler<UserPreferencesPayload> {
    override val operationType = OperationType.USER_PREFERENCES

    override fun parsePayload(json: String): UserPreferencesPayload = Json.decodeFromString(json)

    override fun serializePayload(payload: UserPreferencesPayload): String = Json.encodeToString(payload)

    override fun tryCoalesce(
        existing: PendingOperationEntity,
        existingPayload: UserPreferencesPayload,
        newPayload: UserPreferencesPayload,
    ): UserPreferencesPayload =
        // Always coalesces - only care about final state
        UserPreferencesPayload(
            defaultPlaybackSpeed = newPayload.defaultPlaybackSpeed ?: existingPayload.defaultPlaybackSpeed,
        )

    override suspend fun execute(
        operation: PendingOperationEntity,
        payload: UserPreferencesPayload,
    ): Result<Unit> {
        val request =
            UserPreferencesRequest(
                defaultPlaybackSpeed = payload.defaultPlaybackSpeed,
            )
        return when (val result = api.updatePreferences(request)) {
            is Success -> Success(Unit)
            is Failure -> Failure(result.exception)
        }
    }
}
