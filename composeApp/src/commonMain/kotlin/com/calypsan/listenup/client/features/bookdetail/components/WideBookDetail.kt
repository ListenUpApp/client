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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.LocalDeviceContext
import com.calypsan.listenup.client.design.components.CoverColors
import com.calypsan.listenup.client.design.components.ElevatedCoverCard
import com.calypsan.listenup.client.design.components.GenreChipRow
import com.calypsan.listenup.client.design.components.ProgressOverlay
import com.calypsan.listenup.client.design.components.rememberCoverColors
import com.calypsan.listenup.client.design.theme.DisplayFontFamily
import com.calypsan.listenup.client.design.theme.LocalDarkTheme
import com.calypsan.listenup.client.domain.model.BookDownloadStatus
import com.calypsan.listenup.client.features.bookdetail.TagsSection
import com.calypsan.listenup.client.presentation.bookdetail.BookDetailUiState
import com.calypsan.listenup.client.presentation.bookdetail.ChapterUiModel

/**
 * Wide book detail layout for tablets, foldables, desktop, and TV.
 * Single unified LazyColumn with a horizontal hero Row at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun WideBookDetail(
    bookId: String,
    state: BookDetailUiState,
    downloadStatus: BookDownloadStatus,
    isComplete: Boolean,
    hasProgress: Boolean,
    isAdmin: Boolean,
    isWaitingForWifi: Boolean,
    showPlaybackActions: Boolean = true,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onFindMetadataClick: () -> Unit,
    onMarkCompleteClick: () -> Unit,
    onDiscardProgressClick: () -> Unit,
    onAddToShelfClick: () -> Unit,
    onAddToCollectionClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onDeleteBookClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    playEnabled: Boolean = true,
    onPlayDisabledClick: () -> Unit = {},
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
    onTagClick: (tagId: String) -> Unit,
    onUserProfileClick: (userId: String) -> Unit,
) {
    val deviceContext = LocalDeviceContext.current
    val coverColors = rememberCoverColors(
        imagePath = state.book?.coverPath,
        cachedDominantColor = state.book?.dominantColor,
        cachedDarkMutedColor = state.book?.darkMutedColor,
        cachedVibrantColor = state.book?.vibrantColor,
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = LocalDarkTheme.current

    val gradientColors = if (isDark) {
        listOf(
            coverColors.darkMuted.copy(alpha = 0.5f),
            coverColors.darkMuted.copy(alpha = 0.35f),
            coverColors.darkMuted.copy(alpha = 0.2f),
            surfaceColor,
        )
    } else {
        listOf(
            coverColors.darkMuted.copy(alpha = 0.95f),
            coverColors.darkMuted.copy(alpha = 0.85f),
            coverColors.darkMuted.copy(alpha = 0.7f),
            surfaceColor,
        )
    }

    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var isChaptersExpanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top app bar
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            actions = {
                if (!deviceContext.isLeanback) {
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
                if (deviceContext.canEdit) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        BookActionsMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            isComplete = isComplete,
                            hasProgress = hasProgress,
                            isAdmin = isAdmin,
                            onEditClick = { showMenu = false; onEditClick() },
                            onFindMetadataClick = { showMenu = false; onFindMetadataClick() },
                            onMarkCompleteClick = { showMenu = false; onMarkCompleteClick() },
                            onDiscardProgressClick = { showMenu = false; onDiscardProgressClick() },
                            onAddToShelfClick = { showMenu = false; onAddToShelfClick() },
                            onAddToCollectionClick = { showMenu = false; onAddToCollectionClick() },
                            onShareClick = { showMenu = false; onShareClick() },
                            onDeleteClick = { showMenu = false; onDeleteBookClick() },
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Hero section with gradient background
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(gradientColors))
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        // Cover
                        ElevatedCoverCard(
                            path = state.book?.coverPath,
                            contentDescription = state.book?.title,
                            modifier = Modifier
                                .width(300.dp)
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

                        // Metadata + actions
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Title
                            Text(
                                text = state.book?.title ?: "",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = DisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )

                            // Subtitle
                            state.subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            // Talent
                            TalentSectionWithRoles(
                                authors = state.book?.authors ?: emptyList(),
                                narrators = state.book?.narrators ?: emptyList(),
                                allContributors = state.book?.allContributors ?: emptyList(),
                                onContributorClick = onContributorClick,
                                horizontalAlignment = Alignment.Start,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Actions
                            if (showPlaybackActions) {
                                PrimaryActionsSection(
                                    downloadStatus = downloadStatus,
                                    onPlayClick = onPlayClick,
                                    onDownloadClick = onDownloadClick,
                                    onCancelClick = onCancelClick,
                                    onDeleteClick = onDeleteClick,
                                    modifier = Modifier.widthIn(max = 400.dp),
                                    isWaitingForWifi = isWaitingForWifi,
                                    playEnabled = playEnabled,
                                    onPlayDisabledClick = onPlayDisabledClick,
                                    requestFocus = LocalDeviceContext.current.hasDpad,
                                )
                            }
                        }
                    }
                }
            }

            // Content below hero — standard surface background
            // Description
            state.description.takeIf { it.isNotBlank() }?.let { description ->
                item {
                    DescriptionSection(
                        description = description,
                        isExpanded = isDescriptionExpanded,
                        onToggleExpanded = { isDescriptionExpanded = !isDescriptionExpanded },
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                    )
                }
            }

            // Context metadata
            item {
                ContextMetadataSection(
                    seriesId = state.book?.seriesId,
                    seriesName = state.series ?: state.book?.seriesName,
                    rating = state.rating,
                    duration = state.book?.duration ?: 0,
                    year = state.year,
                    addedAt = state.addedAt,
                    genres = state.genresList,
                    onSeriesClick = onSeriesClick,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                )
            }

            // Tags
            if (state.tags.isNotEmpty()) {
                item {
                    TagsSection(
                        tags = state.tags,
                        isLoading = state.isLoadingTags,
                        onTagClick = { tag -> onTagClick(tag.id) },
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                    )
                }
            }

            // Readers
            item {
                BookReadersSection(
                    bookId = bookId,
                    onUserClick = onUserProfileClick,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                )
            }

            // Chapters
            item {
                ChaptersHeader(
                    chapterCount = state.chapters.size,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                )
            }

            val displayedChapters = if (isChaptersExpanded) state.chapters else state.chapters.take(10)
            itemsIndexed(
                items = displayedChapters,
                key = { _, chapter -> chapter.id },
            ) { index, chapter ->
                ChapterListItemCompact(
                    chapter = chapter,
                    chapterNumber = index + 1,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }

            if (state.chapters.size > 10 && !isChaptersExpanded) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
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
}

@Composable
private fun ChapterListItemCompact(
    chapter: ChapterUiModel,
    chapterNumber: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
