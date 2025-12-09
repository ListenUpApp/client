package com.calypsan.listenup.client.features.bookdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.model.BookDownloadStatus
import com.calypsan.listenup.client.design.components.GenreChipRow
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.LocalSnackbarHostState
import com.calypsan.listenup.client.design.components.ProgressOverlay
import com.calypsan.listenup.client.domain.model.Contributor
import com.calypsan.listenup.client.download.DownloadManager
import com.calypsan.listenup.client.download.DownloadResult
import com.calypsan.listenup.client.playback.PlayerViewModel
import com.calypsan.listenup.client.presentation.bookdetail.BookDetailUiState
import com.calypsan.listenup.client.presentation.bookdetail.BookDetailViewModel
import com.calypsan.listenup.client.presentation.bookdetail.ChapterUiModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    onBackClick: () -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
    viewModel: BookDetailViewModel = koinViewModel(),
    playerViewModel: PlayerViewModel = koinViewModel(),
) {
    val downloadManager: DownloadManager = koinInject()
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    val state by viewModel.state.collectAsState()
    val downloadStatus by downloadManager
        .observeBookStatus(BookId(bookId))
        .collectAsState(initial = BookDownloadStatus.notDownloaded(bookId))

    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.book?.title ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
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
            if (state.isLoading) {
                ListenUpLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.error != null) {
                Text(
                    text = state.error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                BookDetailContent(
                    state = state,
                    downloadStatus = downloadStatus,
                    onPlayClick = { playerViewModel.playBook(BookId(bookId)) },
                    onDownloadClick = {
                        scope.launch {
                            when (val result = downloadManager.downloadBook(BookId(bookId))) {
                                is DownloadResult.Success -> { /* Download started */ }

                                is DownloadResult.AlreadyDownloaded -> { /* Nothing to do */ }

                                is DownloadResult.InsufficientStorage -> {
                                    val requiredMb = result.requiredBytes / 1_000_000
                                    val availableMb = result.availableBytes / 1_000_000
                                    snackbarHostState.showSnackbar(
                                        "Not enough storage. Need ${requiredMb}MB, have ${availableMb}MB available.",
                                    )
                                }

                                is DownloadResult.Error -> {
                                    snackbarHostState.showSnackbar(
                                        "Download failed: ${result.message}",
                                    )
                                }
                            }
                        }
                    },
                    onCancelClick = {
                        scope.launch {
                            downloadManager.cancelDownload(BookId(bookId))
                        }
                    },
                    onDeleteClick = { showDeleteDialog = true },
                    onSeriesClick = onSeriesClick,
                    onContributorClick = onContributorClick,
                )
            }
        }
    }

    // Delete download dialog
    if (showDeleteDialog) {
        DeleteDownloadDialog(
            bookTitle = state.book?.title ?: "",
            downloadSize = downloadStatus.downloadedBytes,
            onConfirm = {
                scope.launch {
                    downloadManager.deleteDownload(BookId(bookId))
                }
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
fun BookDetailContent(
    state: BookDetailUiState,
    downloadStatus: BookDownloadStatus,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
) {
    // Get window size class for adaptive layout
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isMediumOrLarger = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    if (isMediumOrLarger) {
        // Two-pane layout for foldables and larger
        TwoPaneBookDetail(
            state = state,
            downloadStatus = downloadStatus,
            onPlayClick = onPlayClick,
            onDownloadClick = onDownloadClick,
            onCancelClick = onCancelClick,
            onDeleteClick = onDeleteClick,
            onSeriesClick = onSeriesClick,
            onContributorClick = onContributorClick,
        )
    } else {
        // Single-pane layout for phones
        SinglePaneBookDetail(
            state = state,
            downloadStatus = downloadStatus,
            onPlayClick = onPlayClick,
            onDownloadClick = onDownloadClick,
            onCancelClick = onCancelClick,
            onDeleteClick = onDeleteClick,
            onSeriesClick = onSeriesClick,
            onContributorClick = onContributorClick,
        )
    }
}

/**
 * Two-pane layout for medium+ screens (foldables, tablets).
 * Left pane: Cover, title, stats, action buttons (fixed)
 * Right pane: Metadata, description, chapters (scrollable)
 */
@Composable
private fun TwoPaneBookDetail(
    state: BookDetailUiState,
    downloadStatus: BookDownloadStatus,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Left pane - fixed anchor
        BookDetailLeftPane(
            state = state,
            downloadStatus = downloadStatus,
            onPlayClick = onPlayClick,
            onDownloadClick = onDownloadClick,
            onCancelClick = onCancelClick,
            onDeleteClick = onDeleteClick,
            genres = state.genresList,
            modifier =
                Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
        )

        // Right pane - scrollable content
        BookDetailRightPane(
            state = state,
            onSeriesClick = onSeriesClick,
            onContributorClick = onContributorClick,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        )
    }
}

/**
 * Left pane: Cover image, title, stats, genres, action buttons
 */
@Composable
private fun BookDetailLeftPane(
    state: BookDetailUiState,
    downloadStatus: BookDownloadStatus,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    genres: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Cover Image with optional progress overlay
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = state.book?.coverPath,
                    contentDescription = state.book?.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Progress overlay (shown when book has progress)
                state.progress?.let { progress ->
                    ProgressOverlay(
                        progress = progress,
                        timeRemaining = state.timeRemainingFormatted,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        // Title
        Text(
            text = state.book?.title ?: "",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        // Subtitle
        state.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        // Stats Row (Rating, Duration, Year)
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.rating?.takeIf { it > 0 }?.let { rating ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = rating.toString(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Text(
                text = formatDuration(state.book?.duration ?: 0),
                style = MaterialTheme.typography.labelMedium,
            )

            state.year?.takeIf { it > 0 }?.let { year ->
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // Genre Chips
        if (genres.isNotEmpty()) {
            GenreChipRow(
                genres = genres,
                onGenreClick = null,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Play Button
        Button(
            onClick = onPlayClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Play Now", style = MaterialTheme.typography.titleMedium)
        }

        // Download Button (expanded with text)
        DownloadButtonExpanded(
            status = downloadStatus,
            onDownloadClick = onDownloadClick,
            onCancelClick = onCancelClick,
            onDeleteClick = onDeleteClick,
        )
    }
}

/**
 * Right pane: Metadata, description, chapters (scrollable)
 */
@Composable
private fun BookDetailRightPane(
    state: BookDetailUiState,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var isChaptersExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 8.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Metadata
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.book?.authors?.takeIf { it.isNotEmpty() }?.let { authors ->
                    ContributorMetadataRow(
                        label = if (authors.size == 1) "Author" else "Authors",
                        contributors = authors,
                        onContributorClick = onContributorClick,
                    )
                }

                state.book?.narrators?.takeIf { it.isNotEmpty() }?.let { narrators ->
                    ContributorMetadataRow(
                        label = if (narrators.size == 1) "Narrator" else "Narrators",
                        contributors = narrators,
                        onContributorClick = onContributorClick,
                    )
                }

                state.book?.seriesId?.let { seriesId ->
                    ClickableMetadataRow(
                        label = "Series",
                        value = state.series ?: state.book?.seriesName ?: "",
                        onClick = { onSeriesClick(seriesId) },
                    )
                }

                // Other contributor roles
                state.book?.allContributors?.let { contributors ->
                    val otherRoles =
                        contributors
                            .flatMap { it.roles }
                            .filter { it !in listOf("author", "narrator") }
                            .distinct()

                    otherRoles.forEach { role ->
                        val contributorsWithRole =
                            contributors
                                .filter { role in it.roles }
                                .map { Contributor(it.id, it.name) }

                        if (contributorsWithRole.isNotEmpty()) {
                            ContributorMetadataRow(
                                label = formatRoleLabel(role, contributorsWithRole.size),
                                contributors = contributorsWithRole,
                                onContributorClick = onContributorClick,
                            )
                        }
                    }
                }
            }
        }

        // Description
        state.description.takeIf { it.isNotBlank() }?.let { description ->
            item {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (description.length > 300) {
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

        // Tags
        if (state.tags.isNotEmpty()) {
            item {
                TagsSection(
                    tags = state.tags,
                    isLoading = state.isLoadingTags,
                )
            }
        }

        // Chapters Header
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Chapters (${state.chapters.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Chapter List
        val displayedChapters = if (isChaptersExpanded) state.chapters else state.chapters.take(10)

        items(
            items = displayedChapters,
            key = { it.id },
        ) { chapter ->
            ChapterListItemCompact(chapter)
        }

        if (state.chapters.size > 10 && !isChaptersExpanded) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
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
 * Compact chapter item for right pane (no horizontal padding - parent handles it)
 */
@Composable
private fun ChapterListItemCompact(chapter: ChapterUiModel) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
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

/**
 * Single-pane layout for phones (original layout)
 */
@Composable
private fun SinglePaneBookDetail(
    state: BookDetailUiState,
    downloadStatus: BookDownloadStatus,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
) {
    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var isChaptersExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Cover Image Section with optional progress overlay
        item {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
                    modifier =
                        Modifier
                            .width(200.dp)
                            .aspectRatio(1f),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = state.book?.coverPath,
                            contentDescription = state.book?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Progress overlay (shown when book has progress)
                        state.progress?.let { progress ->
                            ProgressOverlay(
                                progress = progress,
                                timeRemaining = state.timeRemainingFormatted,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
        }

        // Title, Subtitle & Series
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = state.book?.title ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                state.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }

                state.series?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        // Genre Chips
        if (state.genresList.isNotEmpty()) {
            item {
                GenreChipRow(
                    genres = state.genresList,
                    onGenreClick = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                )
            }
        }

        // Stats Row (Rating, Duration, Year)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.rating?.takeIf { it > 0 }?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = rating.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                val rating = state.rating
                val year = state.year
                if (rating != null && rating > 0 && year != null && year > 0) {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                Text(
                    text = formatDuration(state.book?.duration ?: 0),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )

                if (year != null && year > 0) {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                state.year?.takeIf { it > 0 }?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Action Buttons
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(
                    onClick = onPlayClick,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play Now", style = MaterialTheme.typography.titleMedium)
                }

                DownloadButton(
                    status = downloadStatus,
                    onDownloadClick = onDownloadClick,
                    onCancelClick = onCancelClick,
                    onDeleteClick = onDeleteClick,
                )
            }
        }

        // Metadata Details
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.book?.authors?.takeIf { it.isNotEmpty() }?.let { authors ->
                    ContributorMetadataRow(
                        label = if (authors.size == 1) "Author" else "Authors",
                        contributors = authors,
                        onContributorClick = onContributorClick,
                    )
                }

                state.book?.seriesId?.let { seriesId ->
                    ClickableMetadataRow(
                        label = "Series",
                        value = state.series ?: state.book?.seriesName ?: "",
                        onClick = { onSeriesClick(seriesId) },
                    )
                }

                state.book?.narrators?.takeIf { it.isNotEmpty() }?.let { narrators ->
                    ContributorMetadataRow(
                        label = if (narrators.size == 1) "Narrator" else "Narrators",
                        contributors = narrators,
                        onContributorClick = onContributorClick,
                    )
                }

                state.book?.allContributors?.let { contributors ->
                    val otherRoles =
                        contributors
                            .flatMap { it.roles }
                            .filter { it !in listOf("author", "narrator") }
                            .distinct()

                    otherRoles.forEach { role ->
                        val contributorsWithRole =
                            contributors
                                .filter { role in it.roles }
                                .map { Contributor(it.id, it.name) }

                        if (contributorsWithRole.isNotEmpty()) {
                            ContributorMetadataRow(
                                label = formatRoleLabel(role, contributorsWithRole.size),
                                contributors = contributorsWithRole,
                                onContributorClick = onContributorClick,
                            )
                        }
                    }
                }

                state.year?.takeIf { it > 0 }?.let { year ->
                    MetadataRow("Year", year.toString())
                }
            }
        }

        // Description
        state.description.takeIf { it.isNotBlank() }?.let { description ->
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(
                        onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(if (isDescriptionExpanded) "Read less" else "Read more")
                    }
                }
            }
        }

        // Tags Section
        if (state.tags.isNotEmpty()) {
            item {
                TagsSection(
                    tags = state.tags,
                    isLoading = state.isLoadingTags,
                )
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
        }

        // Chapters Header
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Chapters (${state.chapters.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
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
            key = { it.id },
        ) { chapter ->
            ChapterListItem(chapter)
        }

        if (state.chapters.size > 5 && !isChaptersExpanded) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    OutlinedButton(
                        onClick = { isChaptersExpanded = true },
                    ) {
                        Text("See More")
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
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
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
        )
    }
}

/**
 * Metadata row for contributors with individually clickable names.
 * Shows comma-separated list where each name is clickable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContributorMetadataRow(
    label: String,
    contributors: List<Contributor>,
    onContributorClick: (contributorId: String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // FlowRow wraps contributors to next line when they overflow
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Start,
        ) {
            contributors.forEachIndexed { index, contributor ->
                Text(
                    text = contributor.name + if (index < contributors.size - 1) ", " else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onContributorClick(contributor.id) },
                )
            }
        }
    }
}

@Composable
fun ChapterListItem(chapter: ChapterUiModel) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = chapter.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = chapter.duration,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Format a role name into a display label.
 * Maps common role names to user-friendly labels and handles pluralization.
 */
fun formatRoleLabel(
    role: String,
    count: Int,
): String =
    when (role.lowercase()) {
        "forward", "foreword" -> {
            if (count == 1) "Foreword By" else "Forewords By"
        }

        "translator" -> {
            if (count == 1) "Translated By" else "Translators"
        }

        "editor" -> {
            if (count == 1) "Editor" else "Editors"
        }

        "illustrator" -> {
            if (count == 1) "Illustrator" else "Illustrators"
        }

        "introduction" -> {
            if (count == 1) "Introduction By" else "Introductions By"
        }

        "afterword" -> {
            if (count == 1) "Afterword By" else "Afterwords By"
        }

        "contributor" -> {
            if (count == 1) "Contributor" else "Contributors"
        }

        "preface" -> {
            if (count == 1) "Preface By" else "Prefaces By"
        }

        else -> {
            // Capitalize and pluralize unknown roles
            val capitalized = role.replaceFirstChar { it.uppercase() }
            if (count == 1) capitalized else "${capitalized}s"
        }
    }

// Helper to format duration
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    return "${hours}hr ${minutes}min"
}
