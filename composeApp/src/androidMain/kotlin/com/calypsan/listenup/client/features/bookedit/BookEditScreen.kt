package com.calypsan.listenup.client.features.bookedit

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.palette.graphics.Palette
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.calypsan.listenup.client.data.remote.ContributorSearchResult
import com.calypsan.listenup.client.design.components.ListenUpAsyncImage
import com.calypsan.listenup.client.design.components.AutocompleteResultItem
import com.calypsan.listenup.client.domain.imagepicker.ImagePickerResult
import com.calypsan.listenup.client.util.rememberImagePicker
import com.calypsan.listenup.client.design.components.LanguageDropdown
import com.calypsan.listenup.client.design.components.ListenUpAutocompleteField
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.ListenUpTextArea
import com.calypsan.listenup.client.design.components.ListenUpTextField
import com.calypsan.listenup.client.design.theme.GoogleSansDisplay
import com.calypsan.listenup.client.presentation.bookedit.BookEditNavAction
import com.calypsan.listenup.client.presentation.bookedit.BookEditUiEvent
import com.calypsan.listenup.client.presentation.bookedit.BookEditUiState
import com.calypsan.listenup.client.presentation.bookedit.BookEditViewModel
import com.calypsan.listenup.client.presentation.bookedit.ContributorRole
import com.calypsan.listenup.client.presentation.bookedit.EditableContributor
import com.calypsan.listenup.client.presentation.bookedit.EditableGenre
import com.calypsan.listenup.client.presentation.bookedit.EditableSeries
import com.calypsan.listenup.client.presentation.bookedit.EditableTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

/**
 * Creative Studio book editing screen following Material 3 Expressive Design.
 *
 * Design Philosophy: Transform data entry into creative expression with immersive
 * visuals that tie the editing experience to the book's identity.
 *
 * Layout:
 * - Immersive backdrop with blurred cover art
 * - Identity Header: Cover + Title/Subtitle side by side
 * - Floating cards for each editing section
 * - Extended FAB for Save action
 */
@Composable
fun BookEditScreen(
    bookId: String,
    onBackClick: () -> Unit,
    onSaveSuccess: () -> Unit = {},
    viewModel: BookEditViewModel = koinViewModel(),
) {
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    val state by viewModel.state.collectAsState()
    val navAction by viewModel.navActions.collectAsState()

    LaunchedEffect(navAction) {
        when (navAction) {
            is BookEditNavAction.NavigateBack -> {
                onBackClick()
                viewModel.clearNavAction()
            }
            is BookEditNavAction.ShowSaveSuccess -> {
                onBackClick()
                viewModel.clearNavAction()
            }
            null -> { /* no action */ }
        }
    }

    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = state.hasChanges) {
        showUnsavedChangesDialog = true
    }

    // Extract colors from cover for immersive backdrop (use displayCoverPath to show staging)
    val coverColors = rememberCoverColors(state.displayCoverPath, state.pendingCoverData)
    val surfaceColor = MaterialTheme.colorScheme.surface

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            // Extended FAB for Save - always visible, disabled when no changes
            if (!state.isLoading) {
                val isEnabled = state.hasChanges && !state.isSaving

                ExtendedFloatingActionButton(
                    onClick = { if (isEnabled) viewModel.onEvent(BookEditUiEvent.Save) },
                    icon = {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = if (isEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                            )
                        } else {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                tint = if (isEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                            )
                        }
                    },
                    text = {
                        Text(
                            text = if (state.isSaving) "Saving..." else "Save Changes",
                            color = if (isEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    },
                    expanded = true,
                    containerColor = if (isEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColor),
        ) {
            // Immersive blurred backdrop
            ImmersiveBackdrop(
                coverPath = state.displayCoverPath,
                refreshKey = state.pendingCoverData,
                coverColors = coverColors,
                surfaceColor = surfaceColor,
            )

            // Content
            when {
                state.isLoading -> {
                    ListenUpLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(paddingValues),
                    )
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(paddingValues),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(
                            onClick = { viewModel.onEvent(BookEditUiEvent.DismissError) },
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
                else -> {
                    BookEditContent(
                        state = state,
                        coverColors = coverColors,
                        onEvent = viewModel::onEvent,
                        onBackClick = {
                            if (state.hasChanges) {
                                showUnsavedChangesDialog = true
                            } else {
                                onBackClick()
                            }
                        },
                        modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()),
                    )
                }
            }
        }
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            shape = MaterialTheme.shapes.large,
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onBackClick()
                    },
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedChangesDialog = false }) {
                    Text("Keep Editing")
                }
            },
        )
    }
}

// =============================================================================
// COLOR EXTRACTION (same pattern as BookDetailScreen)
// =============================================================================

data class CoverColorScheme(
    val dominant: Color,
    val darkMuted: Color,
    val onDominant: Color,
)

private fun extractColorScheme(bitmap: Bitmap, fallbackColor: Color): CoverColorScheme {
    val palette = Palette.from(bitmap).generate()

    val dominant = palette.dominantSwatch?.rgb?.let { Color(it) } ?: fallbackColor
    val darkMuted = palette.darkMutedSwatch?.rgb?.let { Color(it) } ?: dominant

    val onDominant = palette.dominantSwatch?.let {
        Color(it.titleTextColor)
    } ?: Color.White

    return CoverColorScheme(
        dominant = dominant,
        darkMuted = darkMuted,
        onDominant = onDominant,
    )
}

@Composable
private fun rememberCoverColors(
    coverPath: String?,
    refreshKey: Any? = null,
    fallbackColor: Color = MaterialTheme.colorScheme.primaryContainer,
): CoverColorScheme {
    val context = LocalContext.current
    val defaultScheme = CoverColorScheme(
        dominant = fallbackColor,
        darkMuted = fallbackColor,
        onDominant = Color.White,
    )

    // Use file's last modified time as cache key for automatic invalidation
    val cacheKey = remember(coverPath, refreshKey) {
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
            .allowHardware(false)
            .build()
    )

    LaunchedEffect(painter.state) {
        val state = painter.state
        if (state is AsyncImagePainter.State.Success) {
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
// IMMERSIVE BACKDROP
// =============================================================================

@Composable
private fun ImmersiveBackdrop(
    coverPath: String?,
    refreshKey: Any?,
    coverColors: CoverColorScheme,
    surfaceColor: Color,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred cover image
        if (coverPath != null) {
            ListenUpAsyncImage(
                path = coverPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                refreshKey = refreshKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .blur(50.dp),
            )
        }

        // Gradient overlay from cover color to surface
        val gradientColors = listOf(
            coverColors.darkMuted.copy(alpha = 0.85f),
            coverColors.darkMuted.copy(alpha = 0.6f),
            surfaceColor.copy(alpha = 0.95f),
            surfaceColor,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .background(Brush.verticalGradient(gradientColors)),
        )
    }
}

// =============================================================================
// CREATIVE STUDIO LAYOUT
// =============================================================================

@Composable
private fun BookEditContent(
    state: BookEditUiState,
    coverColors: CoverColorScheme,
    onEvent: (BookEditUiEvent) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Detect window size for responsive layout
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isMediumOrLarger = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
    )

    // Image picker for cover uploads
    val imagePicker = rememberImagePicker { result ->
        when (result) {
            is ImagePickerResult.Success -> {
                onEvent(BookEditUiEvent.UploadCover(result.data, result.filename))
            }
            is ImagePickerResult.Cancelled -> { /* User cancelled */ }
            is ImagePickerResult.Error -> {
                // Error is handled via the ViewModel's error state
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Identity Header with navigation
        IdentityHeader(
            coverPath = state.displayCoverPath,
            refreshKey = state.pendingCoverData,
            title = state.title,
            subtitle = state.subtitle,
            isUploadingCover = state.isUploadingCover,
            onTitleChange = { onEvent(BookEditUiEvent.TitleChanged(it)) },
            onSubtitleChange = { onEvent(BookEditUiEvent.SubtitleChanged(it)) },
            onCoverClick = { imagePicker.launch() },
            onBackClick = onBackClick,
        )

        // Cards section - responsive layout
        if (isMediumOrLarger) {
            TwoColumnCardsLayout(state = state, onEvent = onEvent)
        } else {
            SingleColumnCardsLayout(state = state, onEvent = onEvent)
        }

        // Bottom spacing for FAB
        Spacer(modifier = Modifier.height(88.dp))
    }
}

/**
 * Single-column layout for mobile (Compact) screens.
 */
@Composable
private fun SingleColumnCardsLayout(
    state: BookEditUiState,
    onEvent: (BookEditUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Card 1: Description (The Hook)
        StudioCard(title = "Description") {
            ListenUpTextArea(
                value = state.description,
                onValueChange = { onEvent(BookEditUiEvent.DescriptionChanged(it)) },
                label = "Description",
                placeholder = "Enter a description...",
            )
        }

        // Card 2: Publishing
        StudioCard(title = "Publishing") {
            PublishingCardContent(state = state, onEvent = onEvent)
        }

        // Card 3: Identifiers & Format
        StudioCard(title = "Identifiers") {
            IdentifiersCardContent(state = state, onEvent = onEvent)
        }

        // Card 4: Series
        StudioCard(title = "Series") {
            SeriesCardContent(state = state, onEvent = onEvent)
        }

        // Card 5: Classification
        StudioCard(title = "Classification") {
            ClassificationCardContent(state = state, onEvent = onEvent)
        }

        // Card 6: Talent
        StudioCard(title = "Talent") {
            TalentCardContent(state = state, onEvent = onEvent)
        }
    }
}

/**
 * Two-column layout for tablet (Medium/Expanded) screens.
 *
 * Full Width: Description (The Hook - primary content)
 * Left Column: Publishing, Identifiers, Series
 * Right Column: Classification, Talent
 */
@Composable
private fun TwoColumnCardsLayout(
    state: BookEditUiState,
    onEvent: (BookEditUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Full-width: Description (The Hook) - Primary content
        StudioCard(title = "Description") {
            ListenUpTextArea(
                value = state.description,
                onValueChange = { onEvent(BookEditUiEvent.DescriptionChanged(it)) },
                label = "Description",
                placeholder = "Enter a description...",
            )
        }

        // Two-column grid for remaining cards
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Left Column - Book metadata
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StudioCard(title = "Publishing") {
                    PublishingCardContent(state = state, onEvent = onEvent)
                }

                StudioCard(title = "Identifiers") {
                    IdentifiersCardContent(state = state, onEvent = onEvent)
                }

                StudioCard(title = "Series") {
                    SeriesCardContent(state = state, onEvent = onEvent)
                }
            }

            // Right Column - Classification & Talent
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StudioCard(title = "Classification") {
                    ClassificationCardContent(state = state, onEvent = onEvent)
                }

                StudioCard(title = "Talent") {
                    TalentCardContent(state = state, onEvent = onEvent)
                }
            }
        }
    }
}

// =============================================================================
// IDENTITY HEADER
// =============================================================================

@Composable
private fun IdentityHeader(
    coverPath: String?,
    refreshKey: Any?,
    title: String,
    subtitle: String,
    isUploadingCover: Boolean,
    onTitleChange: (String) -> Unit,
    onSubtitleChange: (String) -> Unit,
    onCoverClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(16.dp),
    ) {
        // Navigation
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = surfaceColor.copy(alpha = 0.6f),
                    shape = CircleShape,
                ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cover + Title/Subtitle row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Cover art (120dp) - tappable for upload
            ElevatedCard(
                onClick = onCoverClick,
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(1f),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ListenUpAsyncImage(
                        path = coverPath,
                        contentDescription = "Book cover",
                        contentScale = ContentScale.Crop,
                        refreshKey = refreshKey,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Loading overlay during upload
                    if (isUploadingCover) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        }
                    } else {
                        // Edit indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change cover",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            // Title and Subtitle fields
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Title - Large editorial style
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    textStyle = TextStyle(
                        fontFamily = GoogleSansDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    placeholder = {
                        Text(
                            "Title",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = GoogleSansDisplay,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = surfaceColor.copy(alpha = 0.4f),
                        unfocusedContainerColor = surfaceColor.copy(alpha = 0.2f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Subtitle
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = onSubtitleChange,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    placeholder = {
                        Text(
                            "Subtitle (optional)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = surfaceColor.copy(alpha = 0.4f),
                        unfocusedContainerColor = surfaceColor.copy(alpha = 0.2f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// =============================================================================
// STUDIO CARD
// =============================================================================

/**
 * Elevated card container for edit sections.
 * Uses surfaceContainerHigh for visual pop against the gradient background.
 */
@Composable
private fun StudioCard(
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = GoogleSansDisplay,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

// =============================================================================
// CARD CONTENTS
// =============================================================================

/**
 * Card: Publisher, Year, Language
 */
@Composable
private fun PublishingCardContent(
    state: BookEditUiState,
    onEvent: (BookEditUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ListenUpTextField(
            value = state.publisher,
            onValueChange = { onEvent(BookEditUiEvent.PublisherChanged(it)) },
            label = "Publisher",
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListenUpTextField(
                value = state.publishYear,
                onValueChange = { onEvent(BookEditUiEvent.PublishYearChanged(it)) },
                label = "Year",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )

            LanguageDropdown(
                selectedCode = state.language,
                onLanguageSelected = { onEvent(BookEditUiEvent.LanguageChanged(it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Card: ISBN, ASIN, Abridged toggle
 */
@Composable
private fun IdentifiersCardContent(
    state: BookEditUiState,
    onEvent: (BookEditUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListenUpTextField(
                value = state.isbn,
                onValueChange = { onEvent(BookEditUiEvent.IsbnChanged(it)) },
                label = "ISBN",
                modifier = Modifier.weight(1f),
            )

            ListenUpTextField(
                value = state.asin,
                onValueChange = { onEvent(BookEditUiEvent.AsinChanged(it)) },
                label = "ASIN",
                modifier = Modifier.weight(1f),
            )
        }

        // Abridged toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEvent(BookEditUiEvent.AbridgedChanged(!state.abridged)) }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Abridged",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Shortened version of the original",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.abridged,
                onCheckedChange = { onEvent(BookEditUiEvent.AbridgedChanged(it)) },
            )
        }
    }
}

/**
 * Card: Series with search and sequence editing
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesCardContent(
    state: BookEditUiState,
    onEvent: (BookEditUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Existing series with sequence editing
        state.series.forEach { series ->
            SeriesChipWithSequence(
                series = series,
                onSequenceChange = { sequence ->
                    onEvent(BookEditUiEvent.SeriesSequenceChanged(series, sequence))
                },
                onRemove = { onEvent(BookEditUiEvent.RemoveSeries(series)) },
            )
        }

        // Offline indicator
        if (state.seriesOfflineResult && state.seriesSearchResults.isNotEmpty()) {
            Text(
                text = "Showing offline results",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Search field
        ListenUpAutocompleteField(
            value = state.seriesSearchQuery,
            onValueChange = { onEvent(BookEditUiEvent.SeriesSearchQueryChanged(it)) },
            results = state.seriesSearchResults,
            onResultSelected = { result -> onEvent(BookEditUiEvent.SeriesSelected(result)) },
            onSubmit = { query ->
                val trimmed = query.trim()
                if (trimmed.isNotEmpty()) {
                    val topResult = state.seriesSearchResults.firstOrNull()
                    if (topResult != null) {
                        onEvent(BookEditUiEvent.SeriesSelected(topResult))
                    } else if (trimmed.length >= 2) {
                        onEvent(BookEditUiEvent.SeriesEntered(trimmed))
                    }
                }
            },
            resultContent = { result ->
                AutocompleteResultItem(
                    name = result.name,
                    subtitle = if (result.bookCount > 0) {
                        "${result.bookCount} ${if (result.bookCount == 1) "book" else "books"}"
                    } else null,
                    onClick = { onEvent(BookEditUiEvent.SeriesSelected(result)) },
                )
            },
            placeholder = "Add series...",
            isLoading = state.seriesSearchLoading,
        )

        // Add new chip
        val trimmedQuery = state.seriesSearchQuery.trim()
        val hasExactMatch = state.seriesSearchResults.any {
            it.name.equals(trimmedQuery, ignoreCase = true)
        }
        if (trimmedQuery.length >= 2 && !state.seriesSearchLoading && !hasExactMatch) {
            AssistChip(
                onClick = { onEvent(BookEditUiEvent.SeriesEntered(trimmedQuery)) },
                label = { Text("Add \"$trimmedQuery\"") },
                leadingIcon = {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                },
            )
        }
    }
}

@Composable
private fun SeriesChipWithSequence(
    series: EditableSeries,
    onSequenceChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InputChip(
            selected = false,
            onClick = { },
            label = { Text(series.name) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove ${series.name}",
                    modifier = Modifier
                        .size(InputChipDefaults.AvatarSize)
                        .clickable { onRemove() },
                )
            },
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
        )

        OutlinedTextField(
            value = series.sequence ?: "",
            onValueChange = onSequenceChange,
            label = { Text("#") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Card: Genres and Tags
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassificationCardContent(
    state: BookEditUiState,
    onEvent: (BookEditUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Genres subsection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Genres",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )

            if (state.genres.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.genres.forEach { genre ->
                        GenreChip(
                            genre = genre,
                            onRemove = { onEvent(BookEditUiEvent.RemoveGenre(genre)) },
                        )
                    }
                }
            }

            ListenUpAutocompleteField(
                value = state.genreSearchQuery,
                onValueChange = { onEvent(BookEditUiEvent.GenreSearchQueryChanged(it)) },
                results = state.genreSearchResults,
                onResultSelected = { genre -> onEvent(BookEditUiEvent.GenreSelected(genre)) },
                onSubmit = { query ->
                    val topResult = state.genreSearchResults.firstOrNull()
                    if (topResult != null) {
                        onEvent(BookEditUiEvent.GenreSelected(topResult))
                    }
                },
                resultContent = { genre ->
                    AutocompleteResultItem(
                        name = genre.name,
                        subtitle = genre.parentPath,
                        onClick = { onEvent(BookEditUiEvent.GenreSelected(genre)) },
                    )
                },
                placeholder = "Add genre...",
                isLoading = false,
            )
        }

        // Tags subsection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Tags",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )

            if (state.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.tags.forEach { tag ->
                        TagChip(
                            tag = tag,
                            onRemove = { onEvent(BookEditUiEvent.RemoveTag(tag)) },
                        )
                    }
                }
            }

            ListenUpAutocompleteField(
                value = state.tagSearchQuery,
                onValueChange = { onEvent(BookEditUiEvent.TagSearchQueryChanged(it)) },
                results = state.tagSearchResults,
                onResultSelected = { tag -> onEvent(BookEditUiEvent.TagSelected(tag)) },
                onSubmit = { query ->
                    val trimmed = query.trim()
                    if (trimmed.isNotEmpty()) {
                        val topResult = state.tagSearchResults.firstOrNull()
                        if (topResult != null) {
                            onEvent(BookEditUiEvent.TagSelected(topResult))
                        } else if (trimmed.length >= 2) {
                            onEvent(BookEditUiEvent.TagEntered(trimmed))
                        }
                    }
                },
                resultContent = { tag ->
                    AutocompleteResultItem(
                        name = tag.name,
                        subtitle = null,
                        onClick = { onEvent(BookEditUiEvent.TagSelected(tag)) },
                    )
                },
                placeholder = "Add tag...",
                isLoading = state.tagSearchLoading || state.tagCreating,
            )

            // Add new tag chip
            val trimmedTagQuery = state.tagSearchQuery.trim()
            val hasTagMatch = state.tagSearchResults.any {
                it.name.equals(trimmedTagQuery, ignoreCase = true)
            }
            val alreadyHasTag = state.tags.any {
                it.name.equals(trimmedTagQuery, ignoreCase = true)
            }
            if (trimmedTagQuery.length >= 2 && !state.tagSearchLoading && !state.tagCreating &&
                !hasTagMatch && !alreadyHasTag
            ) {
                AssistChip(
                    onClick = { onEvent(BookEditUiEvent.TagEntered(trimmedTagQuery)) },
                    label = { Text("Add \"$trimmedTagQuery\"") },
                    leadingIcon = {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    },
                )
            }
        }
    }
}

@Composable
private fun GenreChip(
    genre: EditableGenre,
    onRemove: () -> Unit,
) {
    InputChip(
        selected = false,
        onClick = { },
        label = { Text(genre.name) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove ${genre.name}",
                modifier = Modifier
                    .size(InputChipDefaults.AvatarSize)
                    .clickable { onRemove() },
            )
        },
    )
}

@Composable
private fun TagChip(
    tag: EditableTag,
    onRemove: () -> Unit,
) {
    InputChip(
        selected = false,
        onClick = { },
        label = { Text(tag.name) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove ${tag.name}",
                modifier = Modifier
                    .size(InputChipDefaults.AvatarSize)
                    .clickable { onRemove() },
            )
        },
    )
}

/**
 * Card: Contributors by role
 */
@Composable
private fun TalentCardContent(
    state: BookEditUiState,
    onEvent: (BookEditUiEvent) -> Unit,
) {
    var roleToRemove by remember { mutableStateOf<ContributorRole?>(null) }
    var contributorsToRemoveCount by remember { mutableStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Show a section for each visible role
        state.visibleRoles.forEach { role ->
            val contributorsForRole = state.contributorsForRole(role)
            RoleContributorSection(
                role = role,
                contributors = contributorsForRole,
                searchQuery = state.roleSearchQueries[role] ?: "",
                searchResults = state.roleSearchResults[role] ?: emptyList(),
                isSearching = state.roleSearchLoading[role] ?: false,
                isOffline = state.roleOfflineResults[role] ?: false,
                onQueryChange = { query ->
                    onEvent(BookEditUiEvent.RoleSearchQueryChanged(role, query))
                },
                onResultSelected = { result ->
                    onEvent(BookEditUiEvent.RoleContributorSelected(role, result))
                },
                onNameEntered = { name ->
                    onEvent(BookEditUiEvent.RoleContributorEntered(role, name))
                },
                onRemoveContributor = { contributor ->
                    onEvent(BookEditUiEvent.RemoveContributor(contributor, role))
                },
                onRemoveSection = {
                    if (contributorsForRole.size >= 2) {
                        roleToRemove = role
                        contributorsToRemoveCount = contributorsForRole.size
                    } else {
                        onEvent(BookEditUiEvent.RemoveRoleSection(role))
                    }
                },
            )
        }

        // Add Role button
        if (state.availableRolesToAdd.isNotEmpty()) {
            AddRoleButton(
                availableRoles = state.availableRolesToAdd,
                onRoleSelected = { role ->
                    onEvent(BookEditUiEvent.AddRoleSection(role))
                },
            )
        }
    }

    // Confirmation dialog
    roleToRemove?.let { role ->
        AlertDialog(
            onDismissRequest = { roleToRemove = null },
            title = { Text("Remove ${role.displayName}s?") },
            text = {
                Text("This will remove $contributorsToRemoveCount ${role.displayName.lowercase()}${if (contributorsToRemoveCount > 1) "s" else ""} from this book.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(BookEditUiEvent.RemoveRoleSection(role))
                        roleToRemove = null
                    },
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { roleToRemove = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleContributorSection(
    role: ContributorRole,
    contributors: List<EditableContributor>,
    searchQuery: String,
    searchResults: List<ContributorSearchResult>,
    isSearching: Boolean,
    isOffline: Boolean,
    onQueryChange: (String) -> Unit,
    onResultSelected: (ContributorSearchResult) -> Unit,
    onNameEntered: (String) -> Unit,
    onRemoveContributor: (EditableContributor) -> Unit,
    onRemoveSection: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Role header with remove button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${role.displayName}s",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            IconButton(
                onClick = onRemoveSection,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove ${role.displayName} section",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Contributor chips
        if (contributors.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                contributors.forEach { contributor ->
                    ContributorChip(
                        contributor = contributor,
                        onRemove = { onRemoveContributor(contributor) },
                    )
                }
            }
        }

        // Offline indicator
        if (isOffline && searchResults.isNotEmpty()) {
            Text(
                text = "Showing offline results",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Search field
        ListenUpAutocompleteField(
            value = searchQuery,
            onValueChange = onQueryChange,
            results = searchResults,
            onResultSelected = onResultSelected,
            onSubmit = { query ->
                val trimmed = query.trim()
                if (trimmed.isNotEmpty()) {
                    val topResult = searchResults.firstOrNull()
                    if (topResult != null) {
                        onResultSelected(topResult)
                    } else if (trimmed.length >= 2) {
                        onNameEntered(trimmed)
                    }
                }
            },
            resultContent = { result ->
                AutocompleteResultItem(
                    name = result.name,
                    subtitle = if (result.bookCount > 0) {
                        "${result.bookCount} ${if (result.bookCount == 1) "book" else "books"}"
                    } else null,
                    onClick = { onResultSelected(result) },
                )
            },
            placeholder = "Add ${role.displayName.lowercase()}...",
            isLoading = isSearching,
        )

        // Add new chip
        val trimmedQuery = searchQuery.trim()
        val hasExactMatch = searchResults.any { it.name.equals(trimmedQuery, ignoreCase = true) }
        if (trimmedQuery.length >= 2 && !isSearching && !hasExactMatch) {
            AssistChip(
                onClick = { onNameEntered(trimmedQuery) },
                label = { Text("Add \"$trimmedQuery\"") },
                leadingIcon = {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                },
            )
        }
    }
}

@Composable
private fun ContributorChip(
    contributor: EditableContributor,
    onRemove: () -> Unit,
) {
    InputChip(
        selected = false,
        onClick = { },
        label = { Text(contributor.name) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(InputChipDefaults.AvatarSize),
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove ${contributor.name}",
                modifier = Modifier
                    .size(InputChipDefaults.AvatarSize)
                    .clickable { onRemove() },
            )
        },
    )
}

@Composable
private fun AddRoleButton(
    availableRoles: List<ContributorRole>,
    onRoleSelected: (ContributorRole) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text("Add Role") },
            leadingIcon = {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            availableRoles.forEach { role ->
                DropdownMenuItem(
                    text = { Text(role.displayName) },
                    onClick = {
                        expanded = false
                        onRoleSelected(role)
                    },
                )
            }
        }
    }
}
