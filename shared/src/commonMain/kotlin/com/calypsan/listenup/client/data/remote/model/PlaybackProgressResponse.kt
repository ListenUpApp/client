package com.calypsan.listenup.client.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from GET /api/v1/listening/continue endpoint.
 *
 * Represents a user's playback progress on an audiobook.
 * Used to build the "Continue Listening" section on the Home screen.
 */
@Serializable
data class PlaybackProgressResponse(
    @SerialName("user_id")
    val userId: String,
    @SerialName("book_id")
    val bookId: String,
    @SerialName("current_position_ms")
    val currentPositionMs: Long,
    @SerialName("progress")
    val progress: Double, // 0.0 - 1.0
    @SerialName("is_finished")
    val isFinished: Boolean,
    @SerialName("started_at")
    val startedAt: String, // ISO 8601 timestamp
    @SerialName("last_played_at")
    val lastPlayedAt: String, // ISO 8601 timestamp
    @SerialName("total_listen_time_ms")
    val totalListenTimeMs: Long,
    // ISO 8601 timestamp
    @SerialName("updated_at")
    val updatedAt: String,
)
