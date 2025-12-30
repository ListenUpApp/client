@file:Suppress("LongMethod")

package com.calypsan.listenup.client.features.bookdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.data.model.BookDownloadStatus
import com.calypsan.listenup.client.design.components.CoverColors
import com.calypsan.listenup.client.design.components.ElevatedCoverCard
import com.calypsan.listenup.client.design.components.GenreChipRow
import com.calypsan.listenup.client.design.components.ProgressOverlay
import com.calypsan.listenup.client.design.components.rememberCoverColors
import com.calypsan.listenup.client.design.theme.GoogleSansDisplay
import com.calypsan.listenup.client.design.theme.LocalDarkTheme
import com.calypsan.listenup.client.features.bookdetail.TagsSection
import com.calypsan.listenup.client.presentation.bookdetail.BookDetailUiState
import com.calypsan.listenup.client.presentation.bookdetail.ChapterUiModel

/**
 * Two-pane layout for tablets and foldables.
 * Left pane: Hero section with cover and actions
 * Right pane: Content section with description, metadata, chapters
 */
@Suppress("LongParameterList")
@Composable
fun TwoPaneBookDetail(
    state: BookDetailUiState,
    downloadStatus: BookDownloadStatus,
    isComplete: Boolean,
    isAdmin: Boolean,
    isWaitingForWifi: Boolean,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onFindMetadataClick: () -> Unit,
    onMarkCompleteClick: () -> Unit,
    onAddToCollectionClick: () -> Unit,
    onDeleteBookClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
    onFindMetadataClick: () -> Unit = {},
    onMarkCompleteClick: () -> Unit = {},
    onAddToCollectionClick: () -> Unit = {},
) {
    val coverColors =
        rememberCoverColors(
            imagePath = state.book?.coverPath,
            cachedDominantColor = state.book?.dominantColor,
            cachedDarkMutedColor = state.book?.darkMutedColor,
            cachedVibrantColor = state.book?.vibrantColor,
        )
    val surfaceColor = MaterialTheme.colorScheme.surface

    Row(modifier = Modifier.fillMaxSize()) {
        // Left pane - Hero section with gradient background
        TwoPaneLeftPane(
            state = state,
            downloadStatus = downloadStatus,
            isWaitingForWifi = isWaitingForWifi,
            coverColors = coverColors,
            isComplete = isComplete,
            isAdmin = isAdmin,
            onBackClick = onBackClick,
            onEditClick = onEditClick,
            onFindMetadataClick = onFindMetadataClick,
            onMarkCompleteClick = onMarkCompleteClick,
            onAddToCollectionClick = onAddToCollectionClick,
            onDeleteBookClick = onDeleteBookClick,
            onPlayClick = onPlayClick,
            onDownloadClick = onDownloadClick,
            onCancelClick = onCancelClick,
            onDeleteClick = onDeleteClick,
            onContributorClick = onContributorClick,
            onFindMetadataClick = onFindMetadataClick,
            onMarkCompleteClick = onMarkCompleteClick,
            onAddToCollectionClick = onAddToCollectionClick,
            modifier =
                Modifier
                    .width(400.dp)
                    .fillMaxHeight(),
        )

        // Right pane - Content section with surface background
        TwoPaneRightPane(
            state = state,
            onSeriesClick = onSeriesClick,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(surfaceColor),
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun TwoPaneLeftPane(
    state: BookDetailUiState,
    downloadStatus: BookDownloadStatus,
    isWaitingForWifi: Boolean,
    coverColors: CoverColors,
    isComplete: Boolean,
    isAdmin: Boolean,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onFindMetadataClick: () -> Unit,
    onMarkCompleteClick: () -> Unit,
    onAddToCollectionClick: () -> Unit,
    onDeleteBookClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
    onFindMetadataClick: () -> Unit,
    onMarkCompleteClick: () -> Unit,
    onAddToCollectionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = LocalDarkTheme.current

    // Create premium gradient from extracted color (same as HeroSection)
    // In dark mode, use subtler alpha to avoid an oppressive feel
    val gradientColors =
        if (isDark) {
            listOf(
                coverColors.darkMuted.copy(alpha = 0.5f),
                coverColors.darkMuted.copy(alpha = 0.35f),
                coverColors.darkMuted.copy(alpha = 0.2f),
                surfaceColor.copy(alpha = 0.3f),
            )
        } else {
            listOf(
                coverColors.darkMuted.copy(alpha = 0.95f),
                coverColors.darkMuted.copy(alpha = 0.85f),
                coverColors.darkMuted.copy(alpha = 0.7f),
                surfaceColor.copy(alpha = 0.3f),
            )
        }

    Box(
        modifier =
            modifier
                .background(Brush.verticalGradient(gradientColors)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Navigation row with semi-transparent background
            var showMenu by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Back button
                IconButton(
                    onClick = onBackClick,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .background(
                                color = surfaceColor.copy(alpha = 0.5f),
                                shape = CircleShape,
                            ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Three-dot menu (matching phone HeroSection)
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier =
                            Modifier
                                .size(48.dp)
                                .background(
                                    color = surfaceColor.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                ),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            "More options",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    BookActionsMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        isComplete = isComplete,
                        isAdmin = isAdmin,
                        onEditClick = {
                            showMenu = false
                            onEditClick()
                        },
                        onFindMetadataClick = {
                            showMenu = false
                            onFindMetadataClick()
                        },
                        onMarkCompleteClick = {
                            showMenu = false
                            onMarkCompleteClick()
                        },
                        onAddToCollectionClick = {
                            showMenu = false
                            onAddToCollectionClick()
                        },
                        onDeleteClick = {
                            showMenu = false
                            onDeleteBookClick()
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cover with elevated card
            ElevatedCoverCard(
                path = state.book?.coverPath,
                contentDescription = state.book?.title,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
            ) {
                state.progress?.let { progress ->
                    ProgressOverlay(
                        progress = progress,
                        timeRemaining = state.timeRemainingFormatted,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title - Large editorial style (matching HeroSection)
            Text(
                text = state.book?.title ?: "",
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = GoogleSansDisplay,
                        fontWeight = FontWeight.Bold,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            // Subtitle
            state.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // Talent (with additional roles)
            TalentSectionWithRoles(
                authors = state.book?.authors ?: emptyList(),
                narrators = state.book?.narrators ?: emptyList(),
                allContributors = state.book?.allContributors ?: emptyList(),
                onContributorClick = onContributorClick,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Actions
            PrimaryActionsSection(
                downloadStatus = downloadStatus,
                onPlayClick = onPlayClick,
                onDownloadClick = onDownloadClick,
                onCancelClick = onCancelClick,
                onDeleteClick = onDeleteClick,
                isWaitingForWifi = isWaitingForWifi,
            )
        }
    }
}

@Composable
private fun TwoPaneRightPane(
    state: BookDetailUiState,
    onSeriesClick: (seriesId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var isChaptersExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding =
            PaddingValues(
                start = 32.dp,
                end = 32.dp,
                top = 32.dp,
                bottom = 32.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Description
        state.description.takeIf { it.isNotBlank() }?.let { description ->
            item {
                DescriptionSection(
                    description = description,
                    isExpanded = isDescriptionExpanded,
                    onToggleExpanded = { isDescriptionExpanded = !isDescriptionExpanded },
                )
            }
        }

        // Context metadata
        item {
            ContextMetadataSectionAligned(
                seriesId = state.book?.seriesId,
                seriesName = state.series ?: state.book?.seriesName,
                rating = state.rating,
                duration = state.book?.duration ?: 0,
                year = state.year,
                genres = state.genresList,
                onSeriesClick = onSeriesClick,
            )
        }

        // Tags
        if (state.tags.isNotEmpty()) {
            item {
                TagsSection(
                    tags = state.tags,
                    isLoading = state.isLoadingTags,
                )
            }
        }

        // Chapters
        item {
            ChaptersHeader(chapterCount = state.chapters.size)
        }

        val displayedChapters = if (isChaptersExpanded) state.chapters else state.chapters.take(10)
        itemsIndexed(
            items = displayedChapters,
            key = { _, chapter -> chapter.id },
        ) { index, chapter ->
            ChapterListItemCompact(chapter = chapter, chapterNumber = index + 1)
        }

        if (state.chapters.size > 10 && !isChaptersExpanded) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    TextButton(onClick = { isChaptersExpanded = true }) {
                        Text("Show all ${state.chapters.size} chapters")
                    }
                }
            }
        }
    }
}

/**
 * Context metadata section with left-aligned content for the right pane.
 */
@Composable
private fun ContextMetadataSectionAligned(
    seriesId: String?,
    seriesName: String?,
    rating: Double?,
    duration: Long,
    year: Int?,
    genres: List<String>,
    onSeriesClick: (seriesId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Series Badge (left-aligned)
        seriesId?.let { id ->
            seriesName?.let { name ->
                SeriesBadge(
                    seriesName = name,
                    onClick = { onSeriesClick(id) },
                )
            }
        }

        // Stats Row (left-aligned)
        StatsRow(
            rating = rating,
            duration = duration,
            year = year,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        )

        // Genres (left-aligned)
        if (genres.isNotEmpty()) {
            GenreChipRow(
                genres = genres,
                onGenreClick = null,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
            )
        }
    }
}

@Composable
private fun ChapterListItemCompact(
    chapter: ChapterUiModel,
    chapterNumber: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chapterNumber.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 20.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = chapter.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = chapter.duration,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
