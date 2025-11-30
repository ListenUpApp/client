package com.calypsan.listenup.client.features.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.data.sync.SyncStatus
import com.calypsan.listenup.client.features.library.components.AuthorsContent
import com.calypsan.listenup.client.features.library.components.BooksContent
import com.calypsan.listenup.client.features.library.components.LibraryTabRow
import com.calypsan.listenup.client.features.library.components.NarratorsContent
import com.calypsan.listenup.client.features.library.components.SeriesContent
import com.calypsan.listenup.client.presentation.library.LibraryUiEvent
import com.calypsan.listenup.client.presentation.library.LibraryViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Library screen displaying the user's audiobook collection.
 *
 * Features:
 * - Four tabs: Books, Series, Authors, Narrators
 * - Swipeable content via HorizontalPager
 * - Pull-to-refresh for manual sync (applies to all tabs)
 * - Intelligent auto-sync on first visibility
 *
 * This screen is designed to work within the AppShell scaffold,
 * so it does not include its own Scaffold or TopAppBar.
 *
 * @param onBookClick Callback when a book is clicked
 * @param onSeriesClick Callback when a series is clicked
 * @param onAuthorClick Callback when an author is clicked
 * @param onNarratorClick Callback when a narrator is clicked
 * @param topBarCollapseFraction Fraction of top bar collapse (0 = expanded, 1 = collapsed)
 * @param modifier Modifier from parent (includes scaffold padding)
 * @param viewModel The LibraryViewModel (injected via Koin)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onNarratorClick: (String) -> Unit,
    topBarCollapseFraction: Float = 0f,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel()
) {
    // Trigger intelligent auto-sync when screen becomes visible (only once)
    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    // Collect all state flows
    val books by viewModel.books.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()
    val authors by viewModel.authors.collectAsStateWithLifecycle()
    val narrators by viewModel.narrators.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    // Pager state for tab switching
    val pagerState = rememberPagerState(pageCount = { LibraryTab.entries.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        // Tab row - collapses icons when top bar collapses
        LibraryTabRow(
            selectedTabIndex = pagerState.currentPage,
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            collapseFraction = topBarCollapseFraction
        )

        // Pull-to-refresh wraps entire pager (syncs all data)
        PullToRefreshBox(
            isRefreshing = syncState is SyncStatus.Syncing,
            onRefresh = { viewModel.onEvent(LibraryUiEvent.RefreshRequested) },
            modifier = Modifier.fillMaxSize()
        ) {
            // Swipeable content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (LibraryTab.entries[page]) {
                    LibraryTab.Books -> BooksContent(
                        books = books,
                        syncState = syncState,
                        onBookClick = onBookClick,
                        onRetry = { viewModel.onEvent(LibraryUiEvent.RefreshRequested) }
                    )
                    LibraryTab.Series -> SeriesContent(
                        series = series,
                        onSeriesClick = onSeriesClick
                    )
                    LibraryTab.Authors -> AuthorsContent(
                        authors = authors,
                        onAuthorClick = onAuthorClick
                    )
                    LibraryTab.Narrators -> NarratorsContent(
                        narrators = narrators,
                        onNarratorClick = onNarratorClick
                    )
                }
            }
        }
    }
}
