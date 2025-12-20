package com.calypsan.listenup.client.data.sync.push

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PendingOperationEntity
import com.calypsan.listenup.client.data.remote.BookUpdateRequest
import com.calypsan.listenup.client.data.remote.ListenUpApiContract
import com.calypsan.listenup.client.data.remote.ListeningEventRequest
import com.calypsan.listenup.client.data.remote.SeriesUpdateRequest
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.UpdateContributorRequest
import com.calypsan.listenup.client.data.remote.UserPreferencesApiContract
import com.calypsan.listenup.client.data.remote.UserPreferencesRequest
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/**
 * Handler for BOOK_UPDATE operations.
 * Coalesces updates to the same book, merging fields.
 */
class BookUpdateHandler(
    private val api: ListenUpApiContract,
) : OperationHandler<BookUpdatePayload> {
    override val operationType = OperationType.BOOK_UPDATE

    override fun parsePayload(json: String): BookUpdatePayload = Json.decodeFromString(json)

    override fun serializePayload(payload: BookUpdatePayload): String = Json.encodeToString(payload)

    override fun shouldCoalesce(existing: PendingOperationEntity): Boolean =
        existing.operationType == OperationType.BOOK_UPDATE

    override fun coalesce(
        existing: BookUpdatePayload,
        new: BookUpdatePayload,
    ): BookUpdatePayload =
        BookUpdatePayload(
            title = new.title ?: existing.title,
            subtitle = new.subtitle ?: existing.subtitle,
            description = new.description ?: existing.description,
            publisher = new.publisher ?: existing.publisher,
            publishYear = new.publishYear ?: existing.publishYear,
            language = new.language ?: existing.language,
            isbn = new.isbn ?: existing.isbn,
            asin = new.asin ?: existing.asin,
            abridged = new.abridged ?: existing.abridged,
        )

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
    private val api: ListenUpApiContract,
) : OperationHandler<ContributorUpdatePayload> {
    override val operationType = OperationType.CONTRIBUTOR_UPDATE

    override fun parsePayload(json: String): ContributorUpdatePayload = Json.decodeFromString(json)

    override fun serializePayload(payload: ContributorUpdatePayload): String = Json.encodeToString(payload)

    override fun shouldCoalesce(existing: PendingOperationEntity): Boolean =
        existing.operationType == OperationType.CONTRIBUTOR_UPDATE

    override fun coalesce(
        existing: ContributorUpdatePayload,
        new: ContributorUpdatePayload,
    ): ContributorUpdatePayload =
        ContributorUpdatePayload(
            name = new.name ?: existing.name,
            biography = new.biography ?: existing.biography,
            website = new.website ?: existing.website,
            birthDate = new.birthDate ?: existing.birthDate,
            deathDate = new.deathDate ?: existing.deathDate,
            aliases = new.aliases ?: existing.aliases,
        )

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
    private val api: ListenUpApiContract,
) : OperationHandler<SeriesUpdatePayload> {
    override val operationType = OperationType.SERIES_UPDATE

    override fun parsePayload(json: String): SeriesUpdatePayload = Json.decodeFromString(json)

    override fun serializePayload(payload: SeriesUpdatePayload): String = Json.encodeToString(payload)

    override fun shouldCoalesce(existing: PendingOperationEntity): Boolean =
        existing.operationType == OperationType.SERIES_UPDATE

    override fun coalesce(
        existing: SeriesUpdatePayload,
        new: SeriesUpdatePayload,
    ): SeriesUpdatePayload =
        SeriesUpdatePayload(
            name = new.name ?: existing.name,
            description = new.description ?: existing.description,
        )

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
    private val api: ListenUpApiContract,
) : OperationHandler<SetBookContributorsPayload> {
    override val operationType = OperationType.SET_BOOK_CONTRIBUTORS

    override fun parsePayload(json: String): SetBookContributorsPayload = Json.decodeFromString(json)

    override fun serializePayload(payload: SetBookContributorsPayload): String = Json.encodeToString(payload)

    override fun shouldCoalesce(existing: PendingOperationEntity): Boolean =
        existing.operationType == OperationType.SET_BOOK_CONTRIBUTORS

    override fun coalesce(
        existing: SetBookContributorsPayload,
        new: SetBookContributorsPayload,
    ): SetBookContributorsPayload = new // Full replacement

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
    private val api: ListenUpApiContract,
) : OperationHandler<SetBookSeriesPayload> {
    override val operationType = OperationType.SET_BOOK_SERIES

    override fun parsePayload(json: String): SetBookSeriesPayload = Json.decodeFromString(json)

    override fun serializePayload(payload: SetBookSeriesPayload): String = Json.encodeToString(payload)

    override fun shouldCoalesce(existing: PendingOperationEntity): Boolean =
        existing.operationType == OperationType.SET_BOOK_SERIES

    override fun coalesce(
        existing: SetBookSeriesPayload,
        new: SetBookSeriesPayload,
    ): SetBookSeriesPayload = new // Full replacement

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
    private val api: ListenUpApiContract,
) : OperationHandler<MergeContributorPayload> {
    override val operationType = OperationType.MERGE_CONTRIBUTOR

    override fun parsePayload(json: String): MergeContributorPayload = Json.decodeFromString(json)

    override fun serializePayload(payload: MergeContributorPayload): String = Json.encodeToString(payload)

    override fun shouldCoalesce(existing: PendingOperationEntity): Boolean = false

    override fun coalesce(
        existing: MergeContributorPayload,
        new: MergeContributorPayload,
    ): MergeContributorPayload = throw UnsupportedOperationException("Merge operations don't coalesce")

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
    private val api: ListenUpApiContract,
) : OperationHandler<UnmergeContributorPayload> {
    override val operationType = OperationType.UNMERGE_CONTRIBUTOR

    override fun parsePayload(json: String): UnmergeContributorPayload = Json.decodeFromString(json)

    override fun serializePayload(payload: UnmergeContributorPayload): String = Json.encodeToString(payload)

    override fun shouldCoalesce(existing: PendingOperationEntity): Boolean = false

    override fun coalesce(
        existing: UnmergeContributorPayload,
        new: UnmergeContributorPayload,
    ): UnmergeContributorPayload = throw UnsupportedOperationException("Unmerge operations don't coalesce")

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
 */
class ListeningEventHandler(
    private val api: SyncApiContract,
) : OperationHandler<ListeningEventPayload> {
    override val operationType = OperationType.LISTENING_EVENT

    override fun parsePayload(json: String): ListeningEventPayload = Json.decodeFromString(json)

    override fun serializePayload(payload: ListeningEventPayload): String = Json.encodeToString(payload)

    override fun shouldCoalesce(existing: PendingOperationEntity): Boolean = false

    override fun coalesce(
        existing: ListeningEventPayload,
        new: ListeningEventPayload,
    ): ListeningEventPayload = throw UnsupportedOperationException("Listening events don't coalesce")

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
        val requests = operations.map { (_, payload) -> payload.toApiRequest() }

        return when (val result = api.submitListeningEvents(requests)) {
            is Success -> {
                val acknowledged = result.data.acknowledged.toSet()
                operations.associate { (op, payload) ->
                    op.id to
                        if (payload.id in acknowledged) {
                            Success(Unit)
                        } else {
                            Failure(Exception("Event not acknowledged"))
                        }
                }
            }

            is Failure -> {
                operations.associate { (op, _) -> op.id to Failure(result.exception) }
            }
        }
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

    override fun shouldCoalesce(existing: PendingOperationEntity): Boolean =
        existing.operationType == OperationType.PLAYBACK_POSITION

    override fun coalesce(
        existing: PlaybackPositionPayload,
        new: PlaybackPositionPayload,
    ): PlaybackPositionPayload = new // Latest position wins

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

    override fun shouldCoalesce(existing: PendingOperationEntity): Boolean = true

    override fun coalesce(
        existing: UserPreferencesPayload,
        new: UserPreferencesPayload,
    ): UserPreferencesPayload =
        UserPreferencesPayload(
            defaultPlaybackSpeed = new.defaultPlaybackSpeed ?: existing.defaultPlaybackSpeed,
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
