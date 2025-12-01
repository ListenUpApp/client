package com.calypsan.listenup.client.features.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.playback.NowPlayingViewModel
import com.calypsan.listenup.client.playback.SleepTimerState
import org.koin.compose.viewmodel.koinViewModel

/**
 * Container that manages both NowPlayingBar and NowPlayingScreen.
 *
 * Handles expand/collapse animation between mini player and full screen.
 * Should be placed in the app shell, rendered above other content.
 *
 * Uses spring animations for natural, physical-feeling motion as per M3 Expressive.
 */
@Composable
fun NowPlayingHost(
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sleepTimerState by viewModel.sleepTimerState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        // Full screen (slides up when expanded)
        AnimatedVisibility(
            visible = state.isExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            NowPlayingScreen(
                state = state,
                sleepTimerState = sleepTimerState,
                onCollapse = viewModel::collapse,
                onPlayPause = viewModel::playPause,
                onSeek = viewModel::seekWithinChapter,
                onSkipBack = { viewModel.skipBack() },
                onSkipForward = { viewModel.skipForward() },
                onPreviousChapter = viewModel::previousChapter,
                onNextChapter = viewModel::nextChapter,
                onSpeedChange = viewModel::setSpeed,
                onChaptersClick = viewModel::showChapterPicker,
                onSleepTimerClick = viewModel::showSleepTimer
            )
        }

        // Mini player (visible when collapsed, positioned above bottom nav)
        // NavigationBar is 80dp + system navigation bar insets
        val navBarInsets = WindowInsets.navigationBars
        val density = LocalDensity.current
        val systemNavBarHeight = with(density) { navBarInsets.getBottom(density).toDp() }
        val bottomPadding = 80.dp + systemNavBarHeight  // NavigationBar (80dp) + system nav

        NowPlayingBar(
            state = state,
            onTap = viewModel::expand,
            onPlayPause = viewModel::playPause,
            onSkipBack = { viewModel.skipBack() },
            onSkipForward = { viewModel.skipForward() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding)
        )

        // Chapter picker sheet
        if (state.showChapterPicker) {
            ChapterPickerSheet(
                chapters = viewModel.getChapters(),
                currentChapterIndex = state.chapterIndex,
                onChapterSelected = viewModel::seekToChapter,
                onDismiss = viewModel::hideChapterPicker
            )
        }

        // Sleep timer sheet
        if (state.showSleepTimer) {
            SleepTimerSheet(
                currentState = sleepTimerState,
                onSetTimer = viewModel::setSleepTimer,
                onCancelTimer = viewModel::cancelSleepTimer,
                onExtendTimer = viewModel::extendSleepTimer,
                onDismiss = viewModel::hideSleepTimer
            )
        }

        // TODO: Speed picker sheet (Phase 2D)
    }
}
