@file:Suppress("LongMethod")

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
import androidx.window.core.layout.WindowSizeClass
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.LocalSnackbarHostState
import com.calypsan.listenup.client.design.components.rememberCoverColors
import com.calypsan.listenup.client.domain.model.BookDownloadState
import com.calypsan.listenup.client.domain.model.BookDownloadStatus
import com.calypsan.listenup.client.domain.repository.UserRepository
import com.calypsan.listenup.client.domain.model.Shelf
import com.calypsan.listenup.client.download.DownloadResult
import com.calypsan.listenup.client.features.library.ShelfPickerSheet
import com.calypsan.listenup.client.features.bookdetail.components.BookReadersSection
import com.calypsan.listenup.client.features.bookdetail.components.ChapterListItem
import com.calypsan.listenup.client.features.bookdetail.components.ChaptersHeader
import com.calypsan.listenup.client.features.bookdetail.components.ContextMetadataSection
import com.calypsan.listenup.client.features.bookdetail.components.DescriptionSection
import com.calypsan.listenup.client.features.bookdetail.components.HeroSection
import com.calypsan.listenup.client.features.bookdetail.components.MarkCompleteDialog
import com.calypsan.listenup.client.features.bookdetail.components.PrimaryActionsSection
import com.calypsan.listenup.client.features.bookdetail.components.TalentSectionWithRoles
import com.calypsan.listenup.client.features.bookdetail.components.TwoPaneBookDetail
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
    onUserProfileClick: (userId: String) -> Unit,
    viewModel: BookDetailViewModel = koinViewModel(),
) {
    val platformActions: BookDetailPlatformActions = koinInject()
    val userRepository: UserRepository = koinInject()
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    val state by viewModel.state.collectAsState()
    val isAdmin by userRepository.observeIsAdmin().collectAsState(initial = false)
    val downloadStatus by platformActions
        .observeBookStatus(BookId(bookId))
        .collectAsState(initial = BookDownloadStatus.notDownloaded(bookId))

    // WiFi-only download state detection
    val wifiOnlyDownloads by platformActions.observeWifiOnlyDownloads().collectAsState(initial = false)
    val isOnUnmeteredNetwork by platformActions.observeIsOnUnmeteredNetwork().collectAsState(initial = true)

    // Show "Waiting for WiFi" when:
    // - Download is queued AND
    // - WiFi-only is enabled AND
    // - Not currently on WiFi/unmetered network
    val isWaitingForWifi =
        downloadStatus.state == BookDownloadState.QUEUED &&
            wifiOnlyDownloads &&
            !isOnUnmeteredNetwork

    // Server reachability check - runs when screen loads
    var isServerReachable by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(bookId, downloadStatus.isFullyDownloaded) {
        // Only check if book isn't downloaded - downloaded books always play
        if (!downloadStatus.isFullyDownloaded) {
            isServerReachable = platformActions.checkServerReachable()
        }
    }

    // Playback availability: can play if book is downloaded OR server is confirmed reachable
    // While check is in progress (null), assume playable to avoid flicker for fast connections
    val canPlay = downloadStatus.isFullyDownloaded || isServerReachable != false

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMarkCompleteDialog by remember { mutableStateOf(false) }

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
                    bookId = bookId,
                    state = state,
                    downloadStatus = downloadStatus,
                    isComplete = state.isComplete,
                    hasProgress = state.progress != null,
                    isAdmin = isAdmin,
                    isWaitingForWifi = isWaitingForWifi,
                    showPlaybackActions = platformActions.isPlaybackAvailable,
                    onBackClick = onBackClick,
                    onEditClick = { onEditClick(bookId) },
                    onFindMetadataClick = onFindMetadataClick,
                    onMarkCompleteClick = {
                        if (state.isComplete) {
                            viewModel.restartBook() // "Mark as Not Started" = restart
                        } else {
                            showMarkCompleteDialog = true
                        }
                    },
                    onDiscardProgressClick = { viewModel.discardProgress() },
                    onAddToShelfClick = { viewModel.showShelfPicker() },
                    onAddToCollectionClick = { /* TODO: Implement */ },
                    onDeleteBookClick = { /* TODO: Implement */ },
                    onPlayClick = { platformActions.playBook(BookId(bookId)) },
                    canPlay = canPlay,
                    onPlayDisabledClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Server is unreachable. Connect to your server to play or download this book.",
                            )
                        }
                    },
                    onUserProfileClick = onUserProfileClick,
                    onDownloadClick = {
                        scope.launch {
                            when (val result = platformActions.downloadBook(BookId(bookId))) {
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
                            platformActions.cancelDownload(BookId(bookId))
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
                    platformActions.deleteDownload(BookId(bookId))
                }
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    if (showMarkCompleteDialog) {
        MarkCompleteDialog(
            startedAtMs = state.startedAtMs,
            onConfirm = { startedAt, finishedAt ->
                viewModel.markComplete(startedAt = startedAt, finishedAt = finishedAt)
                showMarkCompleteDialog = false
            },
            onDismiss = { showMarkCompleteDialog = false },
        )
    }

    if (state.showShelfPicker) {
        val myShelves by viewModel.myShelves.collectAsState()

        ShelfPickerSheet(
            shelves = myShelves,
            selectedBookCount = 1,
            onShelfSelected = { shelfId -> viewModel.addBookToShelf(shelfId) },
            onCreateAndAddToShelf = { name -> viewModel.createShelfAndAddBook(name) },
            onDismiss = { viewModel.hideShelfPicker() },
            isLoading = state.isAddingToShelf,
        )
    }

    state.shelfError?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearShelfError()
        }
    }
}

/**
 * Main content container that handles responsive layout.
 * Uses TwoPaneLayout for tablets, ImmersiveBookDetail for phones.
 */
@Suppress("LongParameterList")
@Composable
fun BookDetailContent(
    bookId: String,
    state: BookDetailUiState,
    downloadStatus: BookDownloadStatus,
    isComplete: Boolean,
    hasProgress: Boolean,
    isAdmin: Boolean,
    isWaitingForWifi: Boolean,
    showPlaybackActions: Boolean,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onFindMetadataClick: () -> Unit,
    onMarkCompleteClick: () -> Unit,
    onDiscardProgressClick: () -> Unit,
    onAddToShelfClick: () -> Unit,
    onAddToCollectionClick: () -> Unit,
    onDeleteBookClick: () -> Unit,
    onPlayClick: () -> Unit,
    canPlay: Boolean,
    onPlayDisabledClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
    onTagClick: (tagId: String) -> Unit,
    onUserProfileClick: (userId: String) -> Unit,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    // Use two-pane layout for medium+ width devices (tablets, foldables, desktop)
    val useTwoPane =
        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
        )

    if (useTwoPane) {
        TwoPaneBookDetail(
            bookId = bookId,
            state = state,
            downloadStatus = downloadStatus,
            isComplete = isComplete,
            hasProgress = hasProgress,
            isAdmin = isAdmin,
            isWaitingForWifi = isWaitingForWifi,
            showPlaybackActions = showPlaybackActions,
            onBackClick = onBackClick,
            onEditClick = onEditClick,
            onFindMetadataClick = onFindMetadataClick,
            onMarkCompleteClick = onMarkCompleteClick,
            onDiscardProgressClick = onDiscardProgressClick,
            onAddToShelfClick = onAddToShelfClick,
            onAddToCollectionClick = onAddToCollectionClick,
            onDeleteBookClick = onDeleteBookClick,
            onPlayClick = onPlayClick,
            onDownloadClick = onDownloadClick,
            onCancelClick = onCancelClick,
            onDeleteClick = onDeleteClick,
            playEnabled = canPlay,
            onPlayDisabledClick = onPlayDisabledClick,
            onSeriesClick = onSeriesClick,
            onContributorClick = onContributorClick,
            onTagClick = onTagClick,
            onUserProfileClick = onUserProfileClick,
        )
    } else {
        ImmersiveBookDetail(
            bookId = bookId,
            state = state,
            downloadStatus = downloadStatus,
            isComplete = isComplete,
            hasProgress = hasProgress,
            isAdmin = isAdmin,
            isWaitingForWifi = isWaitingForWifi,
            showPlaybackActions = showPlaybackActions,
            onBackClick = onBackClick,
            onEditClick = onEditClick,
            onFindMetadataClick = onFindMetadataClick,
            onMarkCompleteClick = onMarkCompleteClick,
            onDiscardProgressClick = onDiscardProgressClick,
            onAddToShelfClick = onAddToShelfClick,
            onAddToCollectionClick = onAddToCollectionClick,
            onDeleteBookClick = onDeleteBookClick,
            onPlayClick = onPlayClick,
            canPlay = canPlay,
            onPlayDisabledClick = onPlayDisabledClick,
            onDownloadClick = onDownloadClick,
            onCancelClick = onCancelClick,
            onDeleteClick = onDeleteClick,
            onSeriesClick = onSeriesClick,
            onContributorClick = onContributorClick,
            onTagClick = onTagClick,
            onUserProfileClick = onUserProfileClick,
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
    bookId: String,
    state: BookDetailUiState,
    downloadStatus: BookDownloadStatus,
    isComplete: Boolean,
    hasProgress: Boolean,
    isAdmin: Boolean,
    isWaitingForWifi: Boolean,
    showPlaybackActions: Boolean,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onFindMetadataClick: () -> Unit,
    onMarkCompleteClick: () -> Unit,
    onDiscardProgressClick: () -> Unit,
    onAddToShelfClick: () -> Unit,
    onAddToCollectionClick: () -> Unit,
    onDeleteBookClick: () -> Unit,
    onPlayClick: () -> Unit,
    canPlay: Boolean,
    onPlayDisabledClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
    onTagClick: (tagId: String) -> Unit,
    onUserProfileClick: (userId: String) -> Unit,
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
                hasProgress = hasProgress,
                isAdmin = state.isAdmin,
                onBackClick = onBackClick,
                onEditClick = onEditClick,
                onFindMetadataClick = onFindMetadataClick,
                onMarkCompleteClick = onMarkCompleteClick,
                onDiscardProgressClick = onDiscardProgressClick,
                onAddToShelfClick = onAddToShelfClick,
                onAddToCollectionClick = onAddToCollectionClick,
                onDeleteClick = onDeleteBookClick,
            )
        }

        // 2. THE TALENT - Who made this
        item {
            TalentSectionWithRoles(
                authors = state.book?.authors ?: emptyList(),
                narrators = state.book?.narrators ?: emptyList(),
                allContributors = state.book?.allContributors ?: emptyList(),
                onContributorClick = onContributorClick,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        // 3. PRIMARY ACTIONS - What can I do (Thumb zone)
        if (showPlaybackActions) {
            item {
                PrimaryActionsSection(
                    downloadStatus = downloadStatus,
                    onPlayClick = onPlayClick,
                    onDownloadClick = onDownloadClick,
                    onCancelClick = onCancelClick,
                    onDeleteClick = onDeleteClick,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    isWaitingForWifi = isWaitingForWifi,
                    playEnabled = canPlay,
                    onPlayDisabledClick = onPlayDisabledClick,
                )
            }
        }

        // 4. CONTEXT METADATA - Series, Stats, Genres
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

        // 7. READERS - Social reading activity
        item {
            BookReadersSection(
                bookId = bookId,
                onUserClick = onUserProfileClick,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        // 8. CHAPTERS - Deep dive
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
