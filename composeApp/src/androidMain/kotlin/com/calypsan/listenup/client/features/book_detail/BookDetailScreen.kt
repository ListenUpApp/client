package com.calypsan.listenup.client.features.book_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.domain.model.Contributor
import com.calypsan.listenup.client.presentation.book_detail.BookDetailUiState
import com.calypsan.listenup.client.presentation.book_detail.BookDetailViewModel
import com.calypsan.listenup.client.presentation.book_detail.ChapterUiModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    onBackClick: () -> Unit,
    onPlayClick: (bookId: String) -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
    viewModel: BookDetailViewModel = koinViewModel()
) {
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.book?.title ?: "",
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
            if (state.isLoading) {
                ListenUpLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.error != null) {
                Text(
                    text = state.error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                BookDetailContent(
                    state = state,
                    onPlayClick = { onPlayClick(bookId) },
                    onSeriesClick = onSeriesClick,
                    onContributorClick = onContributorClick
                )
            }
        }
    }
}

@Composable
fun BookDetailContent(
    state: BookDetailUiState,
    onPlayClick: () -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit
) {
    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var isChaptersExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Cover Image Section
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .width(200.dp)
                        .aspectRatio(1f)
                ) {
                    AsyncImage(
                        model = state.book?.coverPath,
                        contentDescription = state.book?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Title & Series
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = state.book?.title ?: "",
                    style = MaterialTheme.typography.headlineMedium, // Expressive: Headline Medium/Large
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                state.series?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Stats Row (Rating, Duration, Year)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rating
                state.rating?.takeIf { it > 0 }?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = rating.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (state.rating != null && state.rating > 0 && state.year != null && state.year > 0) {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // Duration
                Text(
                    text = formatDuration(state.book?.duration ?: 0),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )

                if (state.year != null && state.year > 0) {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // Year
                state.year?.takeIf { it > 0 }?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Action Buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp), // Expressive: Taller buttons
                    shape = RoundedCornerShape(28.dp), // Expressive: Rounder corners
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stream Now", style = MaterialTheme.typography.titleMedium)
                }

                FilledIconButton(
                    onClick = { /* TODO: Download */ },
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download")
                }
            }
        }

        // Metadata Details
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Authors (clickable)
                state.book?.authors?.takeIf { it.isNotEmpty() }?.let { authors ->
                    ContributorMetadataRow(
                        label = if (authors.size == 1) "Author" else "Authors",
                        contributors = authors,
                        onContributorClick = onContributorClick
                    )
                }

                // Series (clickable) - uses seriesId from book
                state.book?.seriesId?.let { seriesId ->
                    ClickableMetadataRow(
                        label = "Series",
                        value = state.series ?: state.book?.seriesName ?: "",
                        onClick = { onSeriesClick(seriesId) }
                    )
                }

                // Narrators (clickable)
                state.book?.narrators?.takeIf { it.isNotEmpty() }?.let { narrators ->
                    ContributorMetadataRow(
                        label = if (narrators.size == 1) "Narrator" else "Narrators",
                        contributors = narrators,
                        onContributorClick = onContributorClick
                    )
                }

                // Genres (not clickable)
                state.genres.takeIf { it.isNotBlank() }?.let { genres ->
                    MetadataRow("Genres", genres)
                }

                // Year (not clickable)
                state.year?.takeIf { it > 0 }?.let { year ->
                    MetadataRow("Year", year.toString())
                }
            }
        }

        // Description
        state.description.takeIf { it.isNotBlank() }?.let { description ->
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (isDescriptionExpanded) "Read less" else "Read more")
                    }
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
        }

        // Chapters Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chapters (${state.chapters.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { /* TODO: Filter */ }) {
                    Text("Filter")
                }
            }
        }

        // Chapter List
        val displayedChapters = if (isChaptersExpanded) state.chapters else state.chapters.take(5)
        
        items(
            items = displayedChapters,
            key = { it.id }
        ) { chapter ->
            ChapterListItem(chapter)
        }
        
        if (state.chapters.size > 5 && !isChaptersExpanded) {
            item {
                 Box(
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(vertical = 16.dp),
                     contentAlignment = Alignment.Center
                 ) {
                    OutlinedButton(
                        onClick = { isChaptersExpanded = true }
                    ) {
                         Text("See More")
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Clickable metadata row for simple text values like series.
 */
@Composable
fun ClickableMetadataRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}

/**
 * Metadata row for contributors with individually clickable names.
 * Shows comma-separated list where each name is clickable.
 */
@Composable
fun ContributorMetadataRow(
    label: String,
    contributors: List<Contributor>,
    onContributorClick: (contributorId: String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Flow layout would be better, but for now use inline text with clickable spans
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.weight(1f)
        ) {
            contributors.forEachIndexed { index, contributor ->
                Text(
                    text = contributor.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onContributorClick(contributor.id) }
                )
                if (index < contributors.size - 1) {
                    Text(
                        text = ", ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun ChapterListItem(chapter: ChapterUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chapter Image Placeholder
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
             // Placeholder Icon
             Icon(
                 imageVector = Icons.Default.PlayArrow,
                 contentDescription = null,
                 tint = MaterialTheme.colorScheme.onSurfaceVariant
             )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = chapter.duration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Helper to format duration
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return "${hours}hr ${minutes}min"
}