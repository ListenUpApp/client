package com.calypsan.listenup.client.features.library.components

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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.data.sync.SyncStatus
import com.calypsan.listenup.client.design.components.AlphabetIndex
import com.calypsan.listenup.client.design.components.AlphabetScrollbar
import com.calypsan.listenup.client.design.components.ListenUpButton
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.SortSplitButton
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.features.library.BookCard
import com.calypsan.listenup.client.features.nowplaying.MiniPlayerReservedHeight
import com.calypsan.listenup.client.presentation.library.SortCategory
import com.calypsan.listenup.client.presentation.library.SortState
import kotlinx.coroutines.launch

/**
 * Content for the Books tab in the Library screen.
 *
 * @param books List of books to display
 * @param syncState Current sync status for loading/error states
 * @param sortState Current sort state (category + direction)
 * @param onCategorySelected Called when user selects a new category
 * @param onDirectionToggle Called when user toggles sort direction
 * @param onBookClick Callback when a book is clicked
 * @param onRetry Callback when retry is clicked in error state
 * @param modifier Optional modifier
 */
@Composable
fun BooksContent(
    books: List<Book>,
    syncState: SyncStatus,
    sortState: SortState,
    onCategorySelected: (SortCategory) -> Unit,
    onDirectionToggle: () -> Unit,
    onBookClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            books.isEmpty() && syncState is SyncStatus.Syncing -> {
                BooksLoadingState()
            }
            books.isEmpty() && syncState is SyncStatus.Error -> {
                BooksErrorState(error = syncState.exception, onRetry = onRetry)
            }
            books.isEmpty() -> {
                BooksEmptyState()
            }
            else -> {
                BookGrid(
                    books = books,
                    sortState = sortState,
                    onCategorySelected = onCategorySelected,
                    onDirectionToggle = onDirectionToggle,
                    onBookClick = onBookClick
                )
            }
        }
    }
}

/**
 * Grid of book cards with sort split button and alphabet scrollbar.
 */
@Composable
private fun BookGrid(
    books: List<Book>,
    sortState: SortState,
    onCategorySelected: (SortCategory) -> Unit,
    onDirectionToggle: () -> Unit,
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // Build alphabet index based on current sort category
    val alphabetIndex = remember(books, sortState) {
        when (sortState.category) {
            SortCategory.TITLE -> AlphabetIndex.build(books) { it.title }
            SortCategory.AUTHOR -> AlphabetIndex.build(books) { it.authorNames }
            SortCategory.SERIES -> AlphabetIndex.build(books) { it.seriesName ?: "\uFFFF" }
            else -> null // Numeric sorts don't benefit from alphabet navigation
        }
    }

    val isScrolling by remember {
        derivedStateOf { gridState.isScrollInProgress }
    }

    // Track scroll for sort button visibility
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    val showSortButton by remember {
        derivedStateOf {
            val firstVisible = gridState.firstVisibleItemIndex
            val currentOffset = gridState.firstVisibleItemScrollOffset
            val isAtTop = firstVisible == 0 && currentOffset < 50
            val isScrollingUp = currentOffset < previousScrollOffset
            previousScrollOffset = currentOffset
            isAtTop || isScrollingUp || !gridState.isScrollInProgress
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 48.dp,
                bottom = 16.dp + MiniPlayerReservedHeight
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items = books, key = { it.id.value }) { book ->
                BookCard(
                    book = book,
                    onClick = { onBookClick(book.id.value) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Sort split button
        SortSplitButton(
            state = sortState,
            categories = SortCategory.booksCategories,
            onCategorySelected = onCategorySelected,
            onDirectionToggle = onDirectionToggle,
            visible = showSortButton,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 8.dp)
        )

        // Alphabet scrollbar (only for text-based sorts)
        if (alphabetIndex != null) {
            AlphabetScrollbar(
                alphabetIndex = alphabetIndex,
                onLetterSelected = { index ->
                    scope.launch { gridState.animateScrollToItem(index) }
                },
                isScrolling = isScrolling,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, bottom = MiniPlayerReservedHeight)
            )
        }
    }
}

@Composable
private fun BooksLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ListenUpLoadingIndicator()
            Text(
                text = "Loading your library...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BooksEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
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

@Composable
private fun BooksErrorState(error: Exception, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
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
            ListenUpButton(text = "Retry", onClick = onRetry)
        }
    }
}
