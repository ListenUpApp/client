package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.domain.model.Contributor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * UI state for Now Playing components (mini player and full screen).
 *
 * Separates book-level progress (mini player) from chapter-level progress (full screen).
 * Designed for M3 Expressive UI with dynamic color and chapter-aware seek.
 */
data class NowPlayingState(
    // Visibility
    val isVisible: Boolean = false,
    // Book info
    val bookId: String = "",
    val title: String = "",
    val author: String = "",
    val coverUrl: String? = null,
    // Contributors (for navigation menu)
    val authors: List<Contributor> = emptyList(),
    val narrators: List<Contributor> = emptyList(),
    // Series info (for navigation menu)
    val seriesId: String? = null,
    val seriesName: String? = null,
    // Chapter info
    val chapterTitle: String? = null,
    val chapterIndex: Int = 0,
    val totalChapters: Int = 0,
    // Playback state
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    // Transcode preparation state
    val isPreparing: Boolean = false,
    val prepareProgress: Int = 0, // 0-100
    val prepareMessage: String? = null,
    // Book-level progress (for mini player progress bar)
    val bookProgress: Float = 0f, // 0.0 - 1.0
    val bookPositionMs: Long = 0,
    val bookDurationMs: Long = 0,
    // Chapter-level progress (for full screen seek bar)
    val chapterProgress: Float = 0f, // 0.0 - 1.0
    val chapterPositionMs: Long = 0,
    val chapterDurationMs: Long = 0,
    // UI state
    val isExpanded: Boolean = false,
    val showChapterPicker: Boolean = false,
    val showSpeedPicker: Boolean = false,
    val showSleepTimer: Boolean = false,
    val showContributorPicker: ContributorPickerType? = null,
) {
    val chapterPosition: Duration get() = chapterPositionMs.milliseconds
    val chapterDuration: Duration get() = chapterDurationMs.milliseconds

    val chapterLabel: String get() =
        if (totalChapters > 0) {
            "Chapter ${chapterIndex + 1} of $totalChapters"
        } else {
            ""
        }

    val hasSeries: Boolean get() = seriesId != null
    val hasMultipleAuthors: Boolean get() = authors.size > 1
    val hasMultipleNarrators: Boolean get() = narrators.size > 1
}

/**
 * Type of contributor picker to show.
 */
enum class ContributorPickerType {
    AUTHORS,
    NARRATORS,
}
