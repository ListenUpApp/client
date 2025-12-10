package com.calypsan.listenup.client.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.features.home.components.ContinueListeningRow
import com.calypsan.listenup.client.features.home.components.EmptyContinueListening
import com.calypsan.listenup.client.features.home.components.HomeHeader
import com.calypsan.listenup.client.presentation.home.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home screen - personalized landing page.
 *
 * Features:
 * - Time-aware greeting (Good morning/afternoon/evening/night)
 * - Continue Listening section with in-progress audiobooks
 * - Pull-to-refresh to reload data
 * - Empty state with link to browse library
 *
 * @param onBookClick Callback when a book is clicked
 * @param onNavigateToLibrary Callback to navigate to the library
 * @param modifier Modifier from parent
 * @param viewModel HomeViewModel injected via Koin
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBookClick: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            } else if (!state.isLoading) {
                EmptyContinueListening(
                    onBrowseLibrary = onNavigateToLibrary,
                )
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
