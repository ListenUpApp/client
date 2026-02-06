package com.calypsan.listenup.client.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.calypsan.listenup.client.features.home.components.ContinueListeningRow
import com.calypsan.listenup.client.features.home.components.EmptyContinueListening
import com.calypsan.listenup.client.features.home.components.HomeHeader
import com.calypsan.listenup.client.features.home.components.HomeStatsSection
import com.calypsan.listenup.client.features.home.components.MyLensesRow
import com.calypsan.listenup.client.presentation.home.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home screen - personalized landing page.
 *
 * Adaptive layout:
 * - Compact: single column (header, continue listening, stats, lenses)
 * - Medium+: stats and lenses render side-by-side below continue listening
 *
 * @param onBookClick Callback when a book is clicked
 * @param onNavigateToLibrary Callback to navigate to the library
 * @param onLensClick Callback when a lens is clicked
 * @param onSeeAllLenses Callback when "See All" is clicked for lenses
 * @param modifier Modifier from parent
 * @param viewModel HomeViewModel injected via Koin
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBookClick: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
    onLensClick: (String) -> Unit,
    onSeeAllLenses: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isWide =
        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
        )

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.refresh() },
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            // Greeting header
            HomeHeader(greeting = state.greeting)

            // Continue Listening section or empty state (always full width)
            if (state.hasContinueListening) {
                ContinueListeningRow(
                    books = state.continueListening,
                    onBookClick = onBookClick,
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else if (!state.isLoading) {
                EmptyContinueListening(
                    onBrowseLibrary = onNavigateToLibrary,
                )
            }

            // Stats and Lenses: side-by-side on wider screens, stacked on compact
            if (isWide) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    HomeStatsSection()
                    if (state.hasMyLenses) {
                        Spacer(modifier = Modifier.width(16.dp))
                        MyLensesRow(
                            lenses = state.myLenses,
                            onLensClick = onLensClick,
                            onSeeAllClick = onSeeAllLenses,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                HomeStatsSection()

                if (state.hasMyLenses) {
                    Spacer(modifier = Modifier.height(24.dp))
                    MyLensesRow(
                        lenses = state.myLenses,
                        onLensClick = onLensClick,
                        onSeeAllClick = onSeeAllLenses,
                    )
                }
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
