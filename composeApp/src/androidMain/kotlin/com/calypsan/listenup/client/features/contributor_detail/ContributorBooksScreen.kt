package com.calypsan.listenup.client.features.contributor_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.features.library.BookCard
import com.calypsan.listenup.client.presentation.contributor_detail.ContributorBooksUiState
import com.calypsan.listenup.client.presentation.contributor_detail.ContributorBooksViewModel
import com.calypsan.listenup.client.presentation.contributor_detail.SeriesGroup
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen showing all books for a contributor in a specific role.
 *
 * Books are grouped by series (with horizontal carousels per series)
 * followed by standalone books in a grid at the bottom.
 *
 * @param contributorId The contributor's unique ID
 * @param role The role to filter by (e.g., "author", "narrator")
 * @param onBackClick Callback when back button is clicked
 * @param onBookClick Callback when a book is clicked
 * @param viewModel The ViewModel for contributor books data
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributorBooksScreen(
    contributorId: String,
    role: String,
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    viewModel: ContributorBooksViewModel = koinViewModel()
) {
    LaunchedEffect(contributorId, role) {
        viewModel.loadBooks(contributorId, role)
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.roleDisplayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    ListenUpLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    ContributorBooksContent(
                        state = state,
                        bookProgress = state.bookProgress,
                        onBookClick = onBookClick
                    )
                }
            }
        }
    }
}

/**
 * Content for the contributor books screen.
 */
@Composable
private fun ContributorBooksContent(
    state: ContributorBooksUiState,
    bookProgress: Map<String, Float>,
    onBookClick: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Series sections (horizontal carousels)
        items(
            items = state.seriesGroups,
            key = { it.seriesName }
        ) { seriesGroup ->
            SeriesSection(
                seriesGroup = seriesGroup,
                bookProgress = bookProgress,
                onBookClick = onBookClick
            )
        }

        // Standalone books section (grid)
        if (state.hasStandaloneBooks) {
            item {
                StandaloneBooksSection(
                    books = state.standaloneBooks,
                    bookProgress = bookProgress,
                    onBookClick = onBookClick
                )
            }
        }
    }
}

/**
 * A series section with horizontal book carousel.
 */
@Composable
private fun SeriesSection(
    seriesGroup: SeriesGroup,
    bookProgress: Map<String, Float>,
    onBookClick: (String) -> Unit
) {
    Column {
        // Series header
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = seriesGroup.seriesName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${seriesGroup.books.size} ${if (seriesGroup.books.size == 1) "book" else "books"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal book carousel for series
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = seriesGroup.books,
                key = { it.id.value }
            ) { book ->
                BookCard(
                    book = book,
                    onClick = { onBookClick(book.id.value) },
                    progress = bookProgress[book.id.value],
                    modifier = Modifier.width(140.dp)
                )
            }
        }
    }
}

/**
 * Standalone books section displayed as a grid.
 */
@Composable
private fun StandaloneBooksSection(
    books: List<com.calypsan.listenup.client.domain.model.Book>,
    bookProgress: Map<String, Float>,
    onBookClick: (String) -> Unit
) {
    Column {
        // Section header
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Other Books",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${books.size} ${if (books.size == 1) "book" else "books"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Grid of standalone books
        // Using a fixed height grid within the lazy column
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            books.chunked(3).forEach { rowBooks ->
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowBooks.forEach { book ->
                        BookCard(
                            book = book,
                            onClick = { onBookClick(book.id.value) },
                            progress = bookProgress[book.id.value],
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill remaining slots with spacers to maintain grid alignment
                    repeat(3 - rowBooks.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
