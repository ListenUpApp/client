@file:Suppress("CognitiveComplexMethod")

package com.calypsan.listenup.client.features.seriesdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.calypsan.listenup.client.design.LocalDeviceContext
import com.calypsan.listenup.client.design.components.BookCoverImage
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.presentation.seriesdetail.SeriesDetailUiState
import com.calypsan.listenup.client.presentation.seriesdetail.SeriesDetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.stringResource
import listenup.composeapp.generated.resources.Res
import listenup.composeapp.generated.resources.common_back
import listenup.composeapp.generated.resources.common_about
import listenup.composeapp.generated.resources.series_book_sequence
import listenup.composeapp.generated.resources.series_books_in_series
import listenup.composeapp.generated.resources.series_edit_series
import listenup.composeapp.generated.resources.series_series_cover

/**
 * Screen displaying series details with its books.
 *
 * Adapts layout based on screen width:
 * - Narrow: single-column list with hero, description, and book items
 * - Wide: horizontal header (cover beside stats/description) + responsive book grid
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: String,
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onEditClick: (String) -> Unit,
    viewModel: SeriesDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(seriesId) {
        viewModel.loadSeries(seriesId)
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.seriesName.ifBlank { "Series" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back),
                        )
                    }
                },
                actions = {
                    if (!LocalDeviceContext.current.isLeanback) {
                        IconButton(onClick = { onEditClick(seriesId) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.series_edit_series),
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when {
                state.isLoading -> {
                    ListenUpLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.error != null -> {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
                    val useWideLayout =
                        windowSizeClass.isWidthAtLeastBreakpoint(
                            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
                        )

                    if (useWideLayout) {
                        WideSeriesDetailContent(
                            state = state,
                            onBookClick = onBookClick,
                        )
                    } else {
                        NarrowSeriesDetailContent(
                            state = state,
                            onBookClick = onBookClick,
                        )
                    }
                }
            }
        }
    }
}

// region Wide layout (header + grid)

/**
 * Wide layout: horizontal header row with cover beside stats/description,
 * then a responsive grid of book cover cards below.
 */
@Composable
private fun WideSeriesDetailContent(
    state: SeriesDetailUiState,
    onBookClick: (String) -> Unit,
) {
    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Full-span header: cover beside stats and description
        item(span = { GridItemSpan(maxLineSpan) }) {
            SeriesHeaderRow(
                coverPath = state.coverPath,
                featuredBookId = state.featuredBookId,
                bookCount = state.books.size,
                totalDuration = state.formatTotalDuration(),
                description = state.seriesDescription,
                isDescriptionExpanded = isDescriptionExpanded,
                onToggleDescription = { isDescriptionExpanded = !isDescriptionExpanded },
            )
        }

        // Full-span section header
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(Res.string.series_books_in_series),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        // Book grid cards
        items(
            items = state.books,
            key = { it.id.value },
        ) { book ->
            SeriesBookCard(
                book = book,
                onClick = { onBookClick(book.id.value) },
            )
        }
    }
}

/**
 * Horizontal header: cover on the left, stats and description on the right.
 */
@Composable
@Suppress("CognitiveComplexMethod")
private fun SeriesHeaderRow(
    coverPath: String?,
    featuredBookId: String?,
    bookCount: Int,
    totalDuration: String,
    description: String?,
    isDescriptionExpanded: Boolean,
    onToggleDescription: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Cover image
        Box(
            modifier =
                Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            BookCoverImage(
                bookId = featuredBookId ?: "",
                coverPath = coverPath,
                contentDescription = stringResource(Res.string.series_series_cover),
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
            )
        }

        // Stats and description
        Column(modifier = Modifier.weight(1f)) {
            // Stats row
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatItem(
                    icon = Icons.AutoMirrored.Filled.LibraryBooks,
                    value = "$bookCount",
                    label = if (bookCount == 1) "Book" else "Books",
                )
                StatItem(
                    icon = Icons.Default.Schedule,
                    value = totalDuration,
                    label = "Total",
                )
            }

            // Description
            description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (desc.length > 200) {
                    TextButton(
                        onClick = onToggleDescription,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(if (isDescriptionExpanded) "Read less" else "Read more")
                    }
                }
            }
        }
    }
}

/**
 * Grid card for a book in the series.
 * Shows cover, series position, title, and duration.
 */
@Composable
private fun SeriesBookCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            // Cover image
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (true) { // Always render — BookCoverImage handles server URL fallback
                    BookCoverImage(
                        bookId = book.id.value,
                        coverPath = book.coverPath,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            // Info section below cover
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                // Series position
                book.seriesSequence?.let { sequence ->
                    Text(
                        text = stringResource(Res.string.series_book_sequence, sequence),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Title
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Duration
                Text(
                    text = book.formatDuration(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// endregion

// region Narrow layout (single column)

/**
 * Narrow layout: vertical list with hero section, description, and book items.
 */
@Composable
private fun NarrowSeriesDetailContent(
    state: SeriesDetailUiState,
    onBookClick: (String) -> Unit,
) {
    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Hero section with cover and stats
        item {
            SeriesHeroSection(
                coverPath = state.coverPath,
                featuredBookId = state.featuredBookId,
                bookCount = state.books.size,
                totalDuration = state.formatTotalDuration(),
            )
        }

        // Description section (if available)
        state.seriesDescription?.takeIf { it.isNotBlank() }?.let { description ->
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.common_about),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (description.length > 150) {
                        TextButton(
                            onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(if (isDescriptionExpanded) "Read less" else "Read more")
                        }
                    }
                }
            }
        }

        // Books section header
        item {
            Text(
                text = stringResource(Res.string.series_books_in_series),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp, bottom = 8.dp),
            )
        }

        // Books list
        items(
            items = state.books,
            key = { it.id.value },
        ) { book ->
            SeriesBookItem(
                book = book,
                onClick = { onBookClick(book.id.value) },
            )
        }
    }
}

/**
 * Hero section with series cover and aggregate stats (narrow layout).
 */
@Composable
private fun SeriesHeroSection(
    coverPath: String?,
    featuredBookId: String?,
    bookCount: Int,
    totalDuration: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Cover image
        Box(
            modifier =
                Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            BookCoverImage(
                bookId = featuredBookId ?: "",
                coverPath = coverPath,
                contentDescription = stringResource(Res.string.series_series_cover),
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats row
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatItem(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                value = "$bookCount",
                label = if (bookCount == 1) "Book" else "Books",
            )
            StatItem(
                icon = Icons.Default.Schedule,
                value = totalDuration,
                label = "Total",
            )
        }
    }
}

/**
 * List item for a book in the series (narrow layout).
 */
@Composable
private fun SeriesBookItem(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Book cover
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (true) { // Always render — BookCoverImage handles server URL fallback
                    BookCoverImage(
                        bookId = book.id.value,
                        coverPath = book.coverPath,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Book info
            Column(modifier = Modifier.weight(1f)) {
                // Series position
                book.seriesSequence?.let { sequence ->
                    Text(
                        text = stringResource(Res.string.series_book_sequence, sequence),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Title
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Author
                Text(
                    text = book.authorNames,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Duration
                Text(
                    text = book.formatDuration(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// endregion

// region Shared components

/**
 * Single stat item with icon, value, and label.
 */
@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// endregion
