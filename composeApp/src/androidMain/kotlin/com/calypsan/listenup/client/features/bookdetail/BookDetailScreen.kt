package com.calypsan.listenup.client.features.bookdetail

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.design.components.ListenUpAsyncImage
import com.calypsan.listenup.client.data.model.BookDownloadStatus
import com.calypsan.listenup.client.design.components.GenreChipRow
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.LocalSnackbarHostState
import com.calypsan.listenup.client.design.components.MarkdownText
import com.calypsan.listenup.client.design.components.ProgressOverlay
import com.calypsan.listenup.client.design.theme.GoogleSansDisplay
import com.calypsan.listenup.client.domain.model.Contributor
import com.calypsan.listenup.client.domain.model.Tag
import com.calypsan.listenup.client.download.DownloadManager
import com.calypsan.listenup.client.download.DownloadResult
import com.calypsan.listenup.client.playback.PlayerViewModel
import com.calypsan.listenup.client.presentation.bookdetail.BookDetailUiState
import com.calypsan.listenup.client.presentation.bookdetail.BookDetailViewModel
import com.calypsan.listenup.client.presentation.bookdetail.ChapterUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
@Composable
fun BookDetailScreen(
    bookId: String,
    onBackClick: () -> Unit,
    onEditClick: (bookId: String) -> Unit,
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

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                ListenUpLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }

            state.error != null -> {
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

            else -> {
                BookDetailContent(
                    state = state,
                    downloadStatus = downloadStatus,
                    onBackClick = onBackClick,
                    onEditClick = { onEditClick(bookId) },
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
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isMediumOrLarger = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    if (isMediumOrLarger) {
        TwoPaneBookDetail(
            state = state,
            downloadStatus = downloadStatus,
            onBackClick = onBackClick,
            onEditClick = onEditClick,
            onPlayClick = onPlayClick,
            onDownloadClick = onDownloadClick,
            onCancelClick = onCancelClick,
            onDeleteClick = onDeleteClick,
            onSeriesClick = onSeriesClick,
            onContributorClick = onContributorClick,
        )
    } else {
        ImmersiveBookDetail(
            state = state,
            downloadStatus = downloadStatus,
            onBackClick = onBackClick,
            onEditClick = onEditClick,
            onPlayClick = onPlayClick,
            onDownloadClick = onDownloadClick,
            onCancelClick = onCancelClick,
            onDeleteClick = onDeleteClick,
            onSeriesClick = onSeriesClick,
            onContributorClick = onContributorClick,
        )
    }
}

// =============================================================================
// COLOR EXTRACTION
// =============================================================================

/**
 * Color scheme extracted from cover art using Palette API.
 */
data class CoverColorScheme(
    val dominant: Color,
    val vibrant: Color,
    val darkVibrant: Color,
    val lightVibrant: Color,
    val muted: Color,
    val darkMuted: Color,
    val onDominant: Color,
)

/**
 * Extracts a color scheme from a bitmap using the Palette API.
 */
private fun extractColorScheme(bitmap: Bitmap, fallbackColor: Color): CoverColorScheme {
    val palette = Palette.from(bitmap).generate()

    val dominant = palette.dominantSwatch?.rgb?.let { Color(it) } ?: fallbackColor
    val vibrant = palette.vibrantSwatch?.rgb?.let { Color(it) } ?: dominant
    val darkVibrant = palette.darkVibrantSwatch?.rgb?.let { Color(it) } ?: dominant
    val lightVibrant = palette.lightVibrantSwatch?.rgb?.let { Color(it) } ?: dominant
    val muted = palette.mutedSwatch?.rgb?.let { Color(it) } ?: dominant
    val darkMuted = palette.darkMutedSwatch?.rgb?.let { Color(it) } ?: dominant

    // Calculate contrasting text color
    val onDominant = palette.dominantSwatch?.let {
        Color(it.titleTextColor)
    } ?: Color.White

    return CoverColorScheme(
        dominant = dominant,
        vibrant = vibrant,
        darkVibrant = darkVibrant,
        lightVibrant = lightVibrant,
        muted = muted,
        darkMuted = darkMuted,
        onDominant = onDominant,
    )
}

/**
 * Composable that loads an image and extracts its color palette.
 * Palette extraction runs on background thread to avoid blocking UI.
 * Uses file modification time for automatic cache invalidation.
 *
 * @param coverPath Local file path to the cover image
 * @param fallbackColor Color to use when extraction fails
 */
@Composable
private fun rememberCoverColors(
    coverPath: String?,
    fallbackColor: Color = MaterialTheme.colorScheme.primaryContainer,
): CoverColorScheme {
    val context = LocalContext.current
    val defaultScheme = CoverColorScheme(
        dominant = fallbackColor,
        vibrant = fallbackColor,
        darkVibrant = fallbackColor,
        lightVibrant = fallbackColor,
        muted = fallbackColor,
        darkMuted = fallbackColor,
        onDominant = Color.White,
    )

    // Use file's last modified time as cache key for automatic invalidation
    val cacheKey = remember(coverPath) {
        coverPath?.let {
            val file = java.io.File(it)
            if (file.exists()) "$it:${file.lastModified()}" else it
        }
    }

    var colorScheme by remember(cacheKey) { mutableStateOf(defaultScheme) }

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(coverPath)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .allowHardware(false) // Required for Palette extraction
            .build()
    )

    LaunchedEffect(painter.state) {
        val state = painter.state
        if (state is AsyncImagePainter.State.Success) {
            // Run Palette extraction on background thread
            val extracted = withContext(Dispatchers.Default) {
                try {
                    val bitmap = state.result.image.toBitmap()
                    extractColorScheme(bitmap, fallbackColor)
                } catch (e: Exception) {
                    null
                }
            }
            if (extracted != null) {
                colorScheme = extracted
            }
        }
    }

    return colorScheme
}

// =============================================================================
// IMMERSIVE SINGLE-PANE LAYOUT (Phone)
// =============================================================================

/**
 * Immersive book detail following audiobook user psychology.
 * Uses color extraction for dynamic, personalized theming.
 */
@Composable
private fun ImmersiveBookDetail(
    state: BookDetailUiState,
    downloadStatus: BookDownloadStatus,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
) {
    var isDescriptionExpanded by rememberSaveable { mutableStateOf(false) }
    var isChaptersExpanded by rememberSaveable { mutableStateOf(false) }

    // Extract colors from cover art (use updatedAt for cache-busting)
    val coverColors = rememberCoverColors(
        coverPath = state.book?.coverPath,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // =====================================================================
        // 1. HERO SECTION - Identity with color-extracted gradient
        // =====================================================================
        item {
            HeroSection(
                coverPath = state.book?.coverPath,
                title = state.book?.title ?: "",
                subtitle = state.subtitle,
                progress = state.progress,
                timeRemaining = state.timeRemainingFormatted,
                coverColors = coverColors,
                onBackClick = onBackClick,
                onEditClick = onEditClick,
            )
        }

        // =====================================================================
        // 2. THE TALENT - Who made this
        // =====================================================================
        item {
            TalentSection(
                authors = state.book?.authors ?: emptyList(),
                narrators = state.book?.narrators ?: emptyList(),
                onContributorClick = onContributorClick,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        // =====================================================================
        // 3. PRIMARY ACTIONS - What can I do (Thumb zone)
        // =====================================================================
        item {
            PrimaryActionsSection(
                downloadStatus = downloadStatus,
                onPlayClick = onPlayClick,
                onDownloadClick = onDownloadClick,
                onCancelClick = onCancelClick,
                onDeleteClick = onDeleteClick,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }

        // =====================================================================
        // 4. CONTEXT METADATA - Series, Stats, Genres
        // =====================================================================
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

        // =====================================================================
        // 5. THE HOOK - What's it about (Description)
        // =====================================================================
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

        // =====================================================================
        // 6. TAGS - User categorization
        // =====================================================================
        if (state.tags.isNotEmpty()) {
            item {
                TagsSection(
                    tags = state.tags,
                    isLoading = state.isLoadingTags,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }

        // =====================================================================
        // 7. CHAPTERS - Deep dive
        // =====================================================================
        item {
            Spacer(modifier = Modifier.height(16.dp))
            ChaptersHeader(
                chapterCount = state.chapters.size,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

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
                    modifier = Modifier
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

// =============================================================================
// 1. HERO SECTION - Identity with Color-Extracted Gradient
// =============================================================================

/**
 * Hero section with color-extracted gradient background.
 * Uses Palette API colors instead of blur for performance.
 */
@Composable
private fun HeroSection(
    coverPath: String?,
    title: String,
    subtitle: String?,
    progress: Float?,
    timeRemaining: String?,
    coverColors: CoverColorScheme,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Create gradient from extracted color to surface
    val gradientColors = listOf(
        coverColors.darkMuted.copy(alpha = 0.9f),
        coverColors.darkMuted.copy(alpha = 0.7f),
        surfaceColor.copy(alpha = 0.9f),
        surfaceColor,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 480.dp)
            .background(
                Brush.verticalGradient(gradientColors)
            ),
    ) {
        // Content overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Navigation bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = surfaceColor.copy(alpha = 0.5f),
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = surfaceColor.copy(alpha = 0.5f),
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit book",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Floating cover card (240dp)
            FloatingCoverCard(
                coverPath = coverPath,
                title = title,
                progress = progress,
                timeRemaining = timeRemaining,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title - Magazine headline style
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = GoogleSansDisplay,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            // Subtitle
            subtitle?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Floating cover card (240dp width) - the visual anchor.
 */
@Composable
private fun FloatingCoverCard(
    coverPath: String?,
    title: String,
    progress: Float?,
    timeRemaining: String?,
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 16.dp),
        modifier = Modifier
            .width(240.dp)
            .aspectRatio(1f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ListenUpAsyncImage(
                path = coverPath,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            progress?.let { prog ->
                ProgressOverlay(
                    progress = prog,
                    timeRemaining = timeRemaining,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

// =============================================================================
// 2. THE TALENT - Who made this
// =============================================================================

/**
 * Authors and Narrators displayed as centered, clickable text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TalentSection(
    authors: List<Contributor>,
    narrators: List<Contributor>,
    onContributorClick: (contributorId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (authors.isEmpty() && narrators.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Authors line
        if (authors.isNotEmpty()) {
            TalentLine(
                prefix = "By ",
                contributors = authors,
                onContributorClick = onContributorClick,
            )
        }

        // Narrators line
        if (narrators.isNotEmpty()) {
            TalentLine(
                prefix = "Narrated by ",
                contributors = narrators,
                onContributorClick = onContributorClick,
            )
        }
    }
}

/**
 * A single talent line like "By **Stephen King** & **Peter Straub**"
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TalentLine(
    prefix: String,
    contributors: List<Contributor>,
    onContributorClick: (contributorId: String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        contributors.forEachIndexed { index, contributor ->
            Text(
                text = contributor.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onContributorClick(contributor.id) },
            )

            if (index < contributors.size - 1) {
                val separator = if (index == contributors.size - 2) " & " else ", "
                Text(
                    text = separator,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Extended talent section that includes additional contributor roles beyond authors and narrators.
 * Displays roles like Editor, Translator, etc.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TalentSectionWithRoles(
    authors: List<Contributor>,
    narrators: List<Contributor>,
    allContributors: List<Contributor>,
    onContributorClick: (contributorId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (authors.isEmpty() && narrators.isEmpty() && allContributors.isEmpty()) return

    // Known primary roles to exclude from "additional roles"
    val primaryRoles = setOf("author", "narrator", "writer")

    // Group contributors by their non-primary roles
    val additionalRoleGroups = allContributors
        .flatMap { contributor ->
            contributor.roles
                .filter { role -> role.lowercase() !in primaryRoles }
                .map { role -> role to contributor }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, contributors) -> contributors.distinctBy { it.id } }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Authors line
        if (authors.isNotEmpty()) {
            TalentLine(
                prefix = "By ",
                contributors = authors,
                onContributorClick = onContributorClick,
            )
        }

        // Narrators line
        if (narrators.isNotEmpty()) {
            TalentLine(
                prefix = "Narrated by ",
                contributors = narrators,
                onContributorClick = onContributorClick,
            )
        }

        // Additional roles (e.g., Editor, Translator, etc.)
        additionalRoleGroups.forEach { (role, contributors) ->
            if (contributors.isNotEmpty()) {
                TalentLine(
                    prefix = formatRolePrefix(role),
                    contributors = contributors,
                    onContributorClick = onContributorClick,
                )
            }
        }
    }
}

/**
 * Formats a role name into a display prefix.
 * e.g., "editor" -> "Edited by ", "translator" -> "Translated by "
 */
private fun formatRolePrefix(role: String): String {
    val normalizedRole = role.lowercase().trim()
    return when {
        normalizedRole.endsWith("or") -> {
            // editor -> Edited by, translator -> Translated by
            val base = normalizedRole.dropLast(2)
            "${base.replaceFirstChar { it.uppercase() }}ed by "
        }
        normalizedRole.endsWith("er") -> {
            // producer -> Produced by
            val base = normalizedRole.dropLast(2)
            "${base.replaceFirstChar { it.uppercase() }}ed by "
        }
        else -> {
            // Fallback: just capitalize and add "by"
            "${role.replaceFirstChar { it.uppercase() }} by "
        }
    }
}

// =============================================================================
// 3. PRIMARY ACTIONS - What can I do
// =============================================================================

/**
 * Primary action buttons - Play dominates, Download alongside.
 */
@Composable
private fun PrimaryActionsSection(
    downloadStatus: BookDownloadStatus,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Primary Play Button
        Button(
            onClick = onPlayClick,
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
            ),
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Play",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Download Button - icon-only square
        DownloadButton(
            status = downloadStatus,
            onDownloadClick = onDownloadClick,
            onCancelClick = onCancelClick,
            onDeleteClick = onDeleteClick,
            modifier = Modifier.size(64.dp),
        )
    }
}

// =============================================================================
// 4. CONTEXT METADATA - Series, Stats, Genres
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextMetadataSection(
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Series Badge
        seriesId?.let { id ->
            seriesName?.let { name ->
                SeriesBadge(
                    seriesName = name,
                    onClick = { onSeriesClick(id) },
                )
            }
        }

        // Stats Row
        StatsRow(
            rating = rating,
            duration = duration,
            year = year,
        )

        // Genres
        if (genres.isNotEmpty()) {
            GenreChipRow(
                genres = genres,
                onGenreClick = null,
            )
        }
    }
}

@Composable
private fun SeriesBadge(
    seriesName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = seriesName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsRow(
    rating: Double?,
    duration: Long,
    year: Int?,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rating?.takeIf { it > 0 }?.let { r ->
            StatChip(
                icon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) },
                text = String.format("%.1f", r),
            )
        }

        StatChip(
            icon = { Icon(Icons.Default.AccessTime, null, Modifier.size(16.dp)) },
            text = formatDuration(duration),
        )

        year?.takeIf { it > 0 }?.let { y ->
            StatChip(
                icon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)) },
                text = y.toString(),
            )
        }
    }
}

@Composable
private fun StatChip(
    icon: @Composable () -> Unit,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon()
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// =============================================================================
// 5. THE HOOK - Description
// =============================================================================

@Composable
private fun DescriptionSection(
    description: String,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = GoogleSansDisplay,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Box(
            modifier = if (isExpanded) {
                Modifier
            } else {
                Modifier
                    .heightIn(max = 120.dp)
                    .clip(RoundedCornerShape(0.dp))
            },
        ) {
            MarkdownText(
                markdown = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (description.length > 200) {
            TextButton(
                onClick = onToggleExpanded,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (isExpanded) "Read less" else "Read more")
            }
        }
    }
}

// =============================================================================
// 6. CHAPTERS
// =============================================================================

@Composable
private fun ChaptersHeader(
    chapterCount: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Chapters ($chapterCount)",
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = GoogleSansDisplay,
            fontWeight = FontWeight.Bold,
        ),
        modifier = modifier,
    )
}

@Composable
fun ChapterListItem(chapter: ChapterUiModel) {
    Row(
        modifier = Modifier
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

// =============================================================================
// TWO-PANE LAYOUT (Tablet/Foldable)
// =============================================================================

@Composable
private fun TwoPaneBookDetail(
    state: BookDetailUiState,
    downloadStatus: BookDownloadStatus,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
) {
    val coverColors = rememberCoverColors(
        coverPath = state.book?.coverPath,
    )
    val surfaceColor = MaterialTheme.colorScheme.surface

    Row(modifier = Modifier.fillMaxSize()) {
        // Left pane - Hero section with gradient background
        TwoPaneLeftPane(
            state = state,
            downloadStatus = downloadStatus,
            coverColors = coverColors,
            onBackClick = onBackClick,
            onEditClick = onEditClick,
            onPlayClick = onPlayClick,
            onDownloadClick = onDownloadClick,
            onCancelClick = onCancelClick,
            onDeleteClick = onDeleteClick,
            onContributorClick = onContributorClick,
            modifier = Modifier
                .width(400.dp)
                .fillMaxHeight(),
        )

        // Right pane - Content section with surface background
        TwoPaneRightPane(
            state = state,
            onSeriesClick = onSeriesClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(surfaceColor),
        )
    }
}

@Composable
private fun TwoPaneLeftPane(
    state: BookDetailUiState,
    downloadStatus: BookDownloadStatus,
    coverColors: CoverColorScheme,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onContributorClick: (contributorId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Create premium gradient from extracted color (same as HeroSection)
    val gradientColors = listOf(
        coverColors.darkMuted.copy(alpha = 0.95f),
        coverColors.darkMuted.copy(alpha = 0.85f),
        coverColors.darkMuted.copy(alpha = 0.7f),
        surfaceColor.copy(alpha = 0.3f),
    )

    Box(
        modifier = modifier
            .background(Brush.verticalGradient(gradientColors)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Navigation row with semi-transparent background
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
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
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = surfaceColor.copy(alpha = 0.5f),
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        "Edit",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cover with elevated card
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ListenUpAsyncImage(
                        path = state.book?.coverPath,
                        contentDescription = state.book?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    state.progress?.let { progress ->
                        ProgressOverlay(
                            progress = progress,
                            timeRemaining = state.timeRemainingFormatted,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title - Large editorial style (matching HeroSection)
            Text(
                text = state.book?.title ?: "",
                style = MaterialTheme.typography.headlineMedium.copy(
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
        contentPadding = PaddingValues(
            start = 32.dp,
            end = 32.dp,
            top = 32.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // =====================================================================
        // 1. THE HOOK - Description (Primary text content, first impression)
        // =====================================================================
        state.description.takeIf { it.isNotBlank() }?.let { description ->
            item {
                DescriptionSection(
                    description = description,
                    isExpanded = isDescriptionExpanded,
                    onToggleExpanded = { isDescriptionExpanded = !isDescriptionExpanded },
                )
            }
        }

        // =====================================================================
        // 2. CONTEXT METADATA - Supporting info below the hook
        // Flow: Series Badge -> Stats Row -> Genre Chips (all left-aligned)
        // =====================================================================
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

        // =====================================================================
        // 3. TAGS - User categorization
        // =====================================================================
        if (state.tags.isNotEmpty()) {
            item {
                TagsSection(
                    tags = state.tags,
                    isLoading = state.isLoadingTags,
                )
            }
        }

        // =====================================================================
        // 4. CHAPTERS - Deep dive content
        // =====================================================================
        item {
            ChaptersHeader(chapterCount = state.chapters.size)
        }

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
                    modifier = Modifier
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
 * All elements (Series Badge, Stats Row, Genre Chips) align to Start for visual consistency.
 */
@OptIn(ExperimentalLayoutApi::class)
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
private fun ChapterListItemCompact(chapter: ChapterUiModel) {
    Row(
        modifier = Modifier
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

// =============================================================================
// HELPERS
// =============================================================================

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    return "${hours}h ${minutes}m"
}
