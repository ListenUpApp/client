package com.calypsan.listenup.client.playback

/**
 * Picker / dialog overlay state. Mutually exclusive — at most one overlay open at a time.
 * Held by the VM in a private `MutableStateFlow<NowPlayingOverlay>`; tail-combined with
 * [NowPlayingState] into [NowPlayingScreenState].
 *
 * Replaces the pre-Phase-E2.2.1 boolean fields on the flat NowPlayingState
 * (`showChapterPicker`, `showSpeedPicker`, `showSleepTimer`, `showContributorPicker`).
 */
sealed interface NowPlayingOverlay {
    data object None : NowPlayingOverlay

    data object ChapterPicker : NowPlayingOverlay

    data object SpeedPicker : NowPlayingOverlay

    data object SleepTimer : NowPlayingOverlay

    data class ContributorPicker(
        val type: ContributorPickerType,
    ) : NowPlayingOverlay
}
