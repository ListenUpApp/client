package com.calypsan.listenup.client.features.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.features.home.components.ContinueListeningRow
import com.calypsan.listenup.client.features.home.components.EmptyContinueListening
import com.calypsan.listenup.client.features.home.components.HomeHeader
import com.calypsan.listenup.client.features.home.components.HomeStatsSection
import com.calypsan.listenup.client.features.home.components.MyLensesRow
import com.calypsan.listenup.client.presentation.home.HomeViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.compose.viewmodel.koinViewModel

private val logger = KotlinLogging.logger {}

/**
 * Home screen - personalized landing page.
 *
 * Features:
 * - Time-aware greeting (Good morning/afternoon/evening/night)
 * - Weekly stats section with listening chart, streak, and genres
 * - Continue Listening section with in-progress audiobooks
 * - My Lenses section with user's personal curation lenses
 * - Pull-to-refresh to reload data
 * - Empty state with link to browse library
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
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Log state changes for debugging
    Log.d("HomeScreen", "State: isLoading=${state.isLoading}, continueListening=${state.continueListening.size}, hasContinueListening=${state.hasContinueListening}")

    // Refresh data when returning to Home screen (e.g., after playing a book)
    LifecycleResumeEffect(Unit) {
        Log.d("HomeScreen", "LifecycleResumeEffect triggered, calling refresh()")
        viewModel.refresh()
        onPauseOrDispose { }
    }

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

            // Continue Listening section or empty state
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

            // Stats section (below continue listening)
            HomeStatsSection()

            // My Lenses section
            if (state.hasMyLenses) {
                Spacer(modifier = Modifier.height(24.dp))
                MyLensesRow(
                    lenses = state.myLenses,
                    onLensClick = onLensClick,
                    onSeeAllClick = onSeeAllLenses,
                )
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
