package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.data.remote.model.BookResponse
import com.calypsan.listenup.client.data.remote.model.SSEUserData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Polymorphic sealed hierarchy for SSE events dispatched by the server.
 *
 * Each subclass declares `@Serializable @SerialName("<wire type string>")` and carries
 * a `timestamp` field (envelope-level) plus a payload sub-object matching the server's
 * `data: {...}` shape. Deserialize via `json.decodeFromString<SSEEvent>(eventJson)` —
 * one call, compiler-checked exhaustiveness on the consumer `when`.
 *
 * Unknown discriminators decode to [Unknown] via the `polymorphicDefaultDeserializer`
 * registration on the shared `Json` instance; decode never throws on a well-formed
 * envelope with an unenumerated `type`.
 *
 * For consumer-side dispatch (wire events PLUS synthetic channel messages like
 * reconnect notifications) see [SSEChannelMessage].
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface SSEEvent {
    val timestamp: String

    // ===== Book events =====

    @Serializable
    @SerialName("book.created")
    data class BookCreated(
        override val timestamp: String,
        val data: BookPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("book.updated")
    data class BookUpdated(
        override val timestamp: String,
        val data: BookPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("book.deleted")
    data class BookDeleted(
        override val timestamp: String,
        val data: BookDeletedPayload,
    ) : SSEEvent

    // ===== Library scan events =====

    @Serializable
    @SerialName("library.scan_started")
    data class ScanStarted(
        override val timestamp: String,
        val data: ScanStartedPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("library.scan_completed")
    data class ScanCompleted(
        override val timestamp: String,
        val data: ScanCompletedPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("library.scan_progress")
    data class ScanProgress(
        override val timestamp: String,
        val data: ScanProgressPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("library.access_mode_changed")
    data class LibraryAccessModeChanged(
        override val timestamp: String,
        val data: LibraryAccessModeChangedPayload,
    ) : SSEEvent

    // ===== Heartbeat (no payload) =====

    @Serializable
    @SerialName("heartbeat")
    data class Heartbeat(
        override val timestamp: String,
    ) : SSEEvent

    // ===== User events =====

    @Serializable
    @SerialName("user.pending")
    data class UserPending(
        override val timestamp: String,
        val data: UserPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("user.approved")
    data class UserApproved(
        override val timestamp: String,
        val data: UserPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("user.deleted")
    data class UserDeleted(
        override val timestamp: String,
        val data: UserDeletedPayload,
    ) : SSEEvent

    // ===== Collection events =====

    @Serializable
    @SerialName("collection.created")
    data class CollectionCreated(
        override val timestamp: String,
        val data: CollectionPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("collection.updated")
    data class CollectionUpdated(
        override val timestamp: String,
        val data: CollectionPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("collection.deleted")
    data class CollectionDeleted(
        override val timestamp: String,
        val data: CollectionDeletedPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("collection.book_added")
    data class CollectionBookAdded(
        override val timestamp: String,
        val data: CollectionBookPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("collection.book_removed")
    data class CollectionBookRemoved(
        override val timestamp: String,
        val data: CollectionBookPayload,
    ) : SSEEvent

    // ===== Shelf events =====

    @Serializable
    @SerialName("shelf.created")
    data class ShelfCreated(
        override val timestamp: String,
        val data: ShelfPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("shelf.updated")
    data class ShelfUpdated(
        override val timestamp: String,
        val data: ShelfPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("shelf.deleted")
    data class ShelfDeleted(
        override val timestamp: String,
        val data: ShelfDeletedPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("shelf.book_added")
    data class ShelfBookAdded(
        override val timestamp: String,
        val data: ShelfBookPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("shelf.book_removed")
    data class ShelfBookRemoved(
        override val timestamp: String,
        val data: ShelfBookPayload,
    ) : SSEEvent

    // ===== Tag events =====

    @Serializable
    @SerialName("tag.created")
    data class TagCreated(
        override val timestamp: String,
        val data: TagPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("book.tag_added")
    data class BookTagAdded(
        override val timestamp: String,
        val data: BookTagPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("book.tag_removed")
    data class BookTagRemoved(
        override val timestamp: String,
        val data: BookTagPayload,
    ) : SSEEvent

    // ===== Inbox events =====

    @Serializable
    @SerialName("inbox.book_added")
    data class InboxBookAdded(
        override val timestamp: String,
        val data: InboxBookAddedPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("inbox.book_released")
    data class InboxBookReleased(
        override val timestamp: String,
        val data: InboxBookReleasedPayload,
    ) : SSEEvent

    // ===== Progress events =====
    //
    // NOTE: Wire type strings are `listening.progress_updated` / `listening.progress_deleted`
    // — verified against SSEManager.kt :598-623. The plan's "progress_updated" /
    // "progress_deleted" shorthand was a plan-writer oversight; corrected here.

    @Serializable
    @SerialName("listening.progress_updated")
    data class ProgressUpdated(
        override val timestamp: String,
        val data: ProgressPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("listening.progress_deleted")
    data class ProgressDeleted(
        override val timestamp: String,
        val data: ProgressDeletedPayload,
    ) : SSEEvent

    // ===== Session events =====

    @Serializable
    @SerialName("session.started")
    data class SessionStarted(
        override val timestamp: String,
        val data: SessionStartedPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("session.ended")
    data class SessionEnded(
        override val timestamp: String,
        val data: SessionEndedPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("reading_session.updated")
    data class ReadingSessionUpdated(
        override val timestamp: String,
        val data: ReadingSessionUpdatedPayload,
    ) : SSEEvent

    // ===== Listening events =====
    //
    // NOTE: Wire type string is `listening.event_created` — verified against
    // SSEManager.kt :640. Plan's "listening_event.created" was a plan-writer oversight.

    @Serializable
    @SerialName("listening.event_created")
    data class ListeningEventCreated(
        override val timestamp: String,
        val data: ListeningEventPayload,
    ) : SSEEvent

    // ===== Stats / profile =====

    @Serializable
    @SerialName("user_stats.updated")
    data class UserStatsUpdated(
        override val timestamp: String,
        val data: UserStatsPayload,
    ) : SSEEvent

    @Serializable
    @SerialName("profile.updated")
    data class ProfileUpdated(
        override val timestamp: String,
        val data: ProfilePayload,
    ) : SSEEvent

    // ===== Activity =====

    @Serializable
    @SerialName("activity.created")
    data class ActivityCreated(
        override val timestamp: String,
        val data: ActivityPayload,
    ) : SSEEvent

    // ===== Sentinel (default deserializer for unknown discriminators) =====

    /**
     * Fallback variant decoded when the `type` discriminator doesn't match any
     * enumerated [SerialName]. Installed via
     * `SerializersModule { polymorphic(SSEEvent::class) { defaultDeserializer { Unknown.serializer() } } }`
     * on the shared `Json` instance.
     *
     * @property rawType The unrecognised `type` string from the wire payload;
     *   preserved for logging and future-debugging visibility.
     */
    @Serializable
    data class Unknown(
        override val timestamp: String,
        @SerialName("type") val rawType: String,
    ) : SSEEvent
}

// ===== Payload sub-types =====

@Serializable
data class BookPayload(
    val book: BookResponse,
)

@Serializable
data class BookDeletedPayload(
    @SerialName("book_id") val bookId: String,
    @SerialName("deleted_at") val deletedAt: String,
)

@Serializable
data class ScanStartedPayload(
    @SerialName("library_id") val libraryId: String,
    @SerialName("started_at") val startedAt: String,
)

@Serializable
data class ScanCompletedPayload(
    @SerialName("library_id") val libraryId: String,
    @SerialName("books_added") val booksAdded: Int,
    @SerialName("books_updated") val booksUpdated: Int,
    @SerialName("books_removed") val booksRemoved: Int,
)

@Serializable
data class ScanProgressPayload(
    @SerialName("library_id") val libraryId: String,
    val phase: String,
    val current: Int,
    val total: Int,
    val added: Int,
    val updated: Int,
    val removed: Int,
)

@Serializable
data class LibraryAccessModeChangedPayload(
    @SerialName("library_id") val libraryId: String,
    @SerialName("access_mode") val accessMode: String,
)

@Serializable
data class UserPayload(
    val user: SSEUserData,
)

@Serializable
data class UserDeletedPayload(
    @SerialName("user_id") val userId: String,
    val reason: String? = null,
)

@Serializable
data class CollectionPayload(
    val id: String,
    val name: String,
    @SerialName("book_count") val bookCount: Int,
)

@Serializable
data class CollectionDeletedPayload(
    val id: String,
    val name: String,
)

@Serializable
data class CollectionBookPayload(
    @SerialName("collection_id") val collectionId: String,
    @SerialName("collection_name") val collectionName: String,
    @SerialName("book_id") val bookId: String,
)

@Serializable
data class ShelfPayload(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val description: String? = null,
    @SerialName("book_count") val bookCount: Int,
    @SerialName("owner_display_name") val ownerDisplayName: String,
    @SerialName("owner_avatar_color") val ownerAvatarColor: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ShelfDeletedPayload(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
)

@Serializable
data class ShelfBookPayload(
    @SerialName("shelf_id") val shelfId: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("book_count") val bookCount: Int,
)

@Serializable
data class TagPayload(
    val id: String,
    val slug: String,
    @SerialName("book_count") val bookCount: Int,
)

/**
 * Payload for `book.tag_added` / `book.tag_removed`.
 *
 * Wire shape is nested `{"book_id": ..., "tag": {"id", "slug", "book_count", ...}}` —
 * verified against `SSEBookTagAddedEvent` in `SyncModels.kt :648` (the plan's flat
 * field list was a plan-writer oversight; corrected here).
 */
@Serializable
data class BookTagPayload(
    @SerialName("book_id") val bookId: String,
    val tag: BookTagInnerPayload,
)

@Serializable
data class BookTagInnerPayload(
    val id: String,
    val slug: String,
    @SerialName("book_count") val bookCount: Int,
)

/**
 * Payload for `inbox.book_added`.
 *
 * Wire shape is `{"book": {"id", "title", "author", "cover_url", "duration"}}` —
 * verified against `SSEInboxBookAddedEvent` / `SSEInboxBookData` in
 * `SyncModels.kt :676-697`. The plan's flat `{book_id, title}` shape was a
 * plan-writer oversight; corrected here.
 */
@Serializable
data class InboxBookAddedPayload(
    val book: InboxBookData,
)

@Serializable
data class InboxBookData(
    val id: String,
    val title: String,
    val author: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    val duration: Long = 0,
)

@Serializable
data class InboxBookReleasedPayload(
    @SerialName("book_id") val bookId: String,
)

@Serializable
data class ProgressPayload(
    @SerialName("book_id") val bookId: String,
    @SerialName("current_position_ms") val currentPositionMs: Long,
    val progress: Double,
    @SerialName("total_listen_time_ms") val totalListenTimeMs: Long,
    @SerialName("is_finished") val isFinished: Boolean,
    @SerialName("last_played_at") val lastPlayedAt: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
)

@Serializable
data class ProgressDeletedPayload(
    @SerialName("book_id") val bookId: String,
)

@Serializable
data class SessionStartedPayload(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("started_at") val startedAt: String,
)

@Serializable
data class SessionEndedPayload(
    @SerialName("session_id") val sessionId: String,
)

@Serializable
data class ReadingSessionUpdatedPayload(
    @SerialName("session_id") val sessionId: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("is_completed") val isCompleted: Boolean,
    @SerialName("listen_time_ms") val listenTimeMs: Long,
    @SerialName("finished_at") val finishedAt: String? = null,
)

@Serializable
data class ListeningEventPayload(
    val id: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("start_position_ms") val startPositionMs: Long,
    @SerialName("end_position_ms") val endPositionMs: Long,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    @SerialName("playback_speed") val playbackSpeed: Float,
    @SerialName("device_id") val deviceId: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class UserStatsPayload(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_type") val avatarType: String,
    @SerialName("avatar_value") val avatarValue: String? = null,
    @SerialName("avatar_color") val avatarColor: String,
    @SerialName("total_time_ms") val totalTimeMs: Long,
    @SerialName("total_books") val totalBooks: Int,
    @SerialName("current_streak") val currentStreak: Int,
)

@Serializable
data class ProfilePayload(
    @SerialName("user_id") val userId: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("avatar_type") val avatarType: String,
    @SerialName("avatar_value") val avatarValue: String? = null,
    @SerialName("avatar_color") val avatarColor: String,
    val tagline: String? = null,
) {
    val displayName: String get() = "$firstName $lastName".trim()
}

@Serializable
data class ActivityPayload(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("user_display_name") val userDisplayName: String,
    @SerialName("user_avatar_color") val userAvatarColor: String,
    @SerialName("user_avatar_type") val userAvatarType: String = "auto",
    @SerialName("user_avatar_value") val userAvatarValue: String? = null,
    @SerialName("book_id") val bookId: String? = null,
    @SerialName("book_title") val bookTitle: String? = null,
    @SerialName("book_author_name") val bookAuthorName: String? = null,
    @SerialName("book_cover_path") val bookCoverPath: String? = null,
    @SerialName("is_reread") val isReread: Boolean = false,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("milestone_value") val milestoneValue: Int = 0,
    @SerialName("milestone_unit") val milestoneUnit: String? = null,
    @SerialName("shelf_id") val shelfId: String? = null,
    @SerialName("shelf_name") val shelfName: String? = null,
)
