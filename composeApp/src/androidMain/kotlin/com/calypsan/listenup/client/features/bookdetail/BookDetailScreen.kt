package com.calypsan.listenup.client.features.bookdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.model.BookDownloadState
import com.calypsan.listenup.client.data.model.BookDownloadStatus
import com.calypsan.listenup.client.data.repository.LocalPreferencesContract
import com.calypsan.listenup.client.data.repository.NetworkMonitor
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.LocalSnackbarHostState
import com.calypsan.listenup.client.design.components.rememberCoverColors
import com.calypsan.listenup.client.download.DownloadManager
import com.calypsan.listenup.client.download.DownloadResult
import com.calypsan.listenup.client.features.bookdetail.components.ChapterListItem
import com.calypsan.listenup.client.features.bookdetail.components.ChaptersHeader
import com.calypsan.listenup.client.features.bookdetail.components.ContextMetadataSection
import com.calypsan.listenup.client.features.bookdetail.components.DescriptionSection
import com.calypsan.listenup.client.features.bookdetail.components.HeroSection
import com.calypsan.listenup.client.features.bookdetail.components.PrimaryActionsSection
import com.calypsan.listenup.client.features.bookdetail.components.TalentSection
import com.calypsan.listenup.client.features.bookdetail.components.TwoPaneBookDetail
import com.calypsan.listenup.client.playback.PlayerViewModel
import com.calypsan.listenup.client.presentation.bookdetail.BookDetailUiState
import com.calypsan.listenup.client.presentation.bookdetail.BookDetailViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Immersive book detail screen following Material 3 Expressive Design.
 *
 * Design Philosophy: "Identity -> Talent -> Action -> Story -> Details"
 * Uses Palette API to extract colors from cover art for dynamic theming.
 *
 * Layout Hierarchy (Strict Order):
 * 1. Hero Section - Identity (Cover, Title, Subtitle) with color-extracted gradient
 * 2. The Talent - Who made this (Authors, Narrators - clickable)
 * 3. Primary Actions - What can I do (Play, Download)
 * 4. Context Metadata - Series, Stats, Genres
 * 5. The Hook - What's it about (Description)
 * 6. Tags - User categorization
 * 7. Chapters - Deep dive content
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    onBackClick: () -> Unit,
    onEditClick: (bookId: String) -> Unit,
    onMetadataSearchClick: (bookId: String) -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
    onTagClick: (tagId: String) -> Unit,
    viewModel: BookDetailViewModel = koinViewModel(),
    playerViewModel: PlayerViewModel = koinViewModel(),
) {
    val downloadManager: DownloadManager = koinInject()
    val userDao: UserDao = koinInject()
    val localPreferences: LocalPreferencesContract = koinInject()
    val networkMonitor: NetworkMonitor = koinInject()
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    val state by viewModel.state.collectAsState()
    val currentUser by userDao.observeCurrentUser().collectAsStateWithLifecycle(initialValue = null)
    val downloadStatus by downloadManager
        .observeBookStatus(BookId(bookId))
        .collectAsState(initial = BookDownloadStatus.notDownloaded(bookId))

    val isAdmin = currentUser?.isRoot == true

    // WiFi-only download state detection
    val wifiOnlyDownloads by localPreferences.wifiOnlyDownloads.collectAsState()
    val isOnUnmeteredNetwork by networkMonitor.isOnUnmeteredNetworkFlow.collectAsState()

    // Show "Waiting for WiFi" when:
    // - Download is queued AND
    // - WiFi-only is enabled AND
    // - Not currently on WiFi/unmetered network
    val isWaitingForWifi = downloadStatus.state == BookDownloadState.QUEUED &&
        wifiOnlyDownloads &&
        !isOnUnmeteredNetwork

    var showDeleteDialog by remember { mutableStateOf(false) }

    // Callback for opening metadata search
    val onFindMetadataClick: () -> Unit = {
        onMetadataSearchClick(bookId)
    }

    // The man in black fled across the desert, and the gunslinger followed. (The Dark Tower)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    ListenUpLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            state.error != null -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            else -> {
                BookDetailContent(
                    state = state,
                    downloadStatus = downloadStatus,
                    isComplete = false, // TODO: Add completion tracking
                    isAdmin = isAdmin,
                    isWaitingForWifi = isWaitingForWifi,
                    onBackClick = onBackClick,
                    onEditClick = { onEditClick(bookId) },
                    onFindMetadataClick = onFindMetadataClick,
                    onMarkCompleteClick = { /* TODO: Implement */ },
                    onAddToCollectionClick = { /* TODO: Implement */ },
                    onDeleteBookClick = { /* TODO: Implement */ },
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
                    onTagClick = onTagClick,
                )
            }
        }
    }

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

/**
 * Main content container that handles responsive layout.
 * Uses TwoPaneLayout for tablets, ImmersiveBookDetail for phones.
 */
@Suppress("LongParameterList")
@Composable
fun BookDetailContent(
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
    onTagClick: (tagId: String) -> Unit,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    // Use two-pane layout for medium+ width devices (tablets, foldables)
    val useTwoPane =
        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
        )

    if (useTwoPane) {
        TwoPaneBookDetail(
            state = state,
            downloadStatus = downloadStatus,
            isComplete = isComplete,
            isAdmin = isAdmin,
            isWaitingForWifi = isWaitingForWifi,
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
            onSeriesClick = onSeriesClick,
            onContributorClick = onContributorClick,
            onTagClick = onTagClick,
        )
    } else {
        ImmersiveBookDetail(
            state = state,
            downloadStatus = downloadStatus,
            isComplete = isComplete,
            isAdmin = isAdmin,
            isWaitingForWifi = isWaitingForWifi,
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
            onSeriesClick = onSeriesClick,
            onContributorClick = onContributorClick,
            onTagClick = onTagClick,
        )
    }
}

// =============================================================================
// IMMERSIVE SINGLE-PANE LAYOUT (Phone)
// =============================================================================

/**
 * Immersive book detail following audiobook user psychology.
 * Uses color extraction for dynamic, personalized theming.
 */
@Suppress("LongParameterList")
@Composable
private fun ImmersiveBookDetail(
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
    onTagClick: (tagId: String) -> Unit,
) {
    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var isChaptersExpanded by rememberSaveable { mutableStateOf(false) }

    // Use cached colors for instant rendering, fall back to runtime extraction
    val coverColors =
        rememberCoverColors(
            imagePath = state.book?.coverPath,
            cachedDominantColor = state.book?.dominantColor,
            cachedDarkMutedColor = state.book?.darkMutedColor,
            cachedVibrantColor = state.book?.vibrantColor,
        )

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // 1. HERO SECTION - Identity with color-extracted gradient
        item {
            HeroSection(
                coverPath = state.book?.coverPath,
                title = state.book?.title ?: "",
                subtitle = state.subtitle,
                progress = state.progress,
                timeRemaining = state.timeRemainingFormatted,
                coverColors = coverColors,
                isComplete = state.isComplete,
                isAdmin = state.isAdmin,
                onBackClick = onBackClick,
                onEditClick = onEditClick,
                onFindMetadataClick = onFindMetadataClick,
                onMarkCompleteClick = onMarkCompleteClick,
                onAddToCollectionClick = onAddToCollectionClick,
                onDeleteClick = onDeleteBookClick,
            )
        }

        // 2. THE TALENT - Who made this
        item {
            TalentSection(
                authors = state.book?.authors ?: emptyList(),
                narrators = state.book?.narrators ?: emptyList(),
                onContributorClick = onContributorClick,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        // 3. PRIMARY ACTIONS - What can I do (Thumb zone)
        item {
            PrimaryActionsSection(
                downloadStatus = downloadStatus,
                onPlayClick = onPlayClick,
                onDownloadClick = onDownloadClick,
                onCancelClick = onCancelClick,
                onDeleteClick = onDeleteClick,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                isWaitingForWifi = isWaitingForWifi,
            )
        }

        // 4. CONTEXT METADATA - Series, Stats, Genres
        item {
            ContextMetadataSection(
                seriesId = state.book?.seriesId,
                seriesName = state.series ?: state.book?.seriesName,
                rating = state.rating,
                duration = state.book?.duration ?: 0,
                year = state.year,
                genres = state.genresList,
                onSeriesClick = onSeriesClick,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        // 5. THE HOOK - What's it about (Description)
        state.description.takeIf { it.isNotBlank() }?.let { description ->
            item {
                DescriptionSection(
                    description = description,
                    isExpanded = isDescriptionExpanded,
                    onToggleExpanded = { isDescriptionExpanded = !isDescriptionExpanded },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }

        // 6. TAGS - User categorization
        if (state.tags.isNotEmpty()) {
            item {
                TagsSection(
                    tags = state.tags,
                    isLoading = state.isLoadingTags,
                    onTagClick = { tag -> onTagClick(tag.id) },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }

        // 7. CHAPTERS - Deep dive
        item {
            Spacer(modifier = Modifier.height(16.dp))
            ChaptersHeader(
                chapterCount = state.chapters.size,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        val displayedChapters = if (isChaptersExpanded) state.chapters else state.chapters.take(5)
        itemsIndexed(
            items = displayedChapters,
            key = { _, chapter -> chapter.id },
        ) { index, chapter ->
            ChapterListItem(chapter = chapter, chapterNumber = index + 1)
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
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text("Show all ${state.chapters.size} chapters")
                    }
                }
            }
        }
    }
}
