package com.calypsan.listenup.client.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.data.sync.SyncStatus
import com.calypsan.listenup.client.design.components.ListenUpButton
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.presentation.library.LibraryUiEvent
import com.calypsan.listenup.client.presentation.library.LibraryViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Library screen displaying the user's audiobook collection.
 *
 * Features:
 * - Responsive grid layout (adapts to screen size)
 * - Pull-to-refresh for manual sync
 * - Empty state when no books
 * - Loading state during initial sync
 * - Error state with retry
 * - Sync status indicator in top bar
 *
 * @param viewModel The LibraryViewModel (injected via Koin)
 * @param onBookClick Callback when a book is clicked
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = koinViewModel(),
    onBookClick: (String) -> Unit = {}
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    LibraryScreenContent(
        books = books,
        syncState = syncState,
        onBookClick = onBookClick,
        onRefresh = { viewModel.onEvent(LibraryUiEvent.RefreshRequested) }
    )
}

/**
 * Stateless content component for LibraryScreen.
 *
 * Separated for easier testing and previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreenContent(
    books: List<Book>,
    syncState: SyncStatus,
    onBookClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            LibraryTopBar(
                syncState = syncState,
                onSearchClick = { /* TODO: Future search feature */ }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface // Base surface color from dynamic theme
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = syncState is SyncStatus.Syncing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                // Loading state: first sync, no books yet
                books.isEmpty() && syncState is SyncStatus.Syncing -> {
                    LoadingState()
                }

                // Error state: sync failed and no cached books
                books.isEmpty() && syncState is SyncStatus.Error -> {
                    ErrorState(
                        error = (syncState as SyncStatus.Error).exception,
                        onRetry = onRefresh
                    )
                }

                // Empty state: no books in library
                books.isEmpty() -> {
                    EmptyState()
                }

                // Success: show book grid
                else -> {
                    BookGrid(
                        books = books,
                        onBookClick = onBookClick
                    )
                }
            }
        }
    }
}

/**
 * Top app bar with sync status indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    syncState: SyncStatus,
    onSearchClick: () -> Unit
) {
    TopAppBar(
        title = { Text("Library") },
        actions = {
            // Sync status indicator
            when (syncState) {
                is SyncStatus.Syncing -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 12.dp),
                        strokeWidth = 2.dp
                    )
                }
                is SyncStatus.Error -> {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Sync error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                else -> {}
            }

            // Search button (future feature)
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            }
        }
    )
}

/**
 * Grid of book cards with responsive columns.
 */
@Composable
private fun BookGrid(
    books: List<Book>,
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = books,
            key = { it.id.value }
        ) { book ->
            BookCard(
                book = book,
                onClick = { onBookClick(book.id.value) },
                modifier = Modifier.animateItem() // Smooth reflow animation
            )
        }
    }
}

/**
 * Loading state shown during initial sync.
 */
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow), // Subtle tinted background
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading your library...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Empty state when no books in library.
 */
@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow), // Subtle tinted background
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No audiobooks yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Add audiobooks to your server to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Error state with retry button.
 */
@Composable
private fun ErrorState(
    error: Exception,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow), // Subtle tinted background
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Failed to load library",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = error.message ?: "Unknown error",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            ListenUpButton(
                text = "Retry",
                onClick = onRetry
            )
        }
    }
}
