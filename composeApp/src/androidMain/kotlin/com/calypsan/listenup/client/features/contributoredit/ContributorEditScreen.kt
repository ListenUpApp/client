package com.calypsan.listenup.client.features.contributoredit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import com.calypsan.listenup.client.design.components.AutocompleteResultItem
import com.calypsan.listenup.client.design.components.ListenUpAutocompleteField
import com.calypsan.listenup.client.design.components.ListenUpDatePicker
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.ListenUpTextArea
import com.calypsan.listenup.client.design.components.ListenUpTextField
import com.calypsan.listenup.client.design.components.avatarColorForUser
import com.calypsan.listenup.client.design.components.getInitials
import com.calypsan.listenup.client.design.theme.GoogleSansDisplay
import com.calypsan.listenup.client.presentation.contributoredit.ContributorEditNavAction
import com.calypsan.listenup.client.presentation.contributoredit.ContributorEditUiEvent
import com.calypsan.listenup.client.presentation.contributoredit.ContributorEditUiState
import com.calypsan.listenup.client.presentation.contributoredit.ContributorEditViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Artist Studio - immersive contributor editing experience.
 *
 * Design Philosophy: "The person is the brand."
 * Uses dynamic color gradients derived from contributor ID to create
 * a personalized editing studio that feels like managing an artist's profile.
 *
 * Layout Hierarchy:
 * 1. Dynamic Gradient Backdrop - Rich colors from avatar palette
 * 2. Identity Header - Large avatar + Name field side by side
 * 3. Content Cards - Biography, Links, Dates, Aliases
 * 4. Extended FAB - Save action always visible
 *
 * Responsive Design:
 * - Mobile: Single column card layout
 * - Tablet: Two-column layout with Biography spanning full width
 */
@Composable
fun ContributorEditScreen(
    contributorId: String,
    onBackClick: () -> Unit,
    onSaveSuccess: () -> Unit = {},
    viewModel: ContributorEditViewModel = koinViewModel(),
) {
    LaunchedEffect(contributorId) {
        viewModel.loadContributor(contributorId)
    }

    val state by viewModel.state.collectAsState()
    val navAction by viewModel.navActions.collectAsState()

    LaunchedEffect(navAction) {
        when (navAction) {
            is ContributorEditNavAction.NavigateBack -> {
                onBackClick()
                viewModel.clearNavAction()
            }
            is ContributorEditNavAction.SaveSuccess -> {
                onSaveSuccess()
                viewModel.clearNavAction()
            }
            null -> { /* no action */ }
        }
    }

    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = state.hasChanges) {
        showUnsavedChangesDialog = true
    }

    // Generate color palette from contributor ID
    val colorScheme = rememberContributorColorScheme(contributorId)
    val surfaceColor = MaterialTheme.colorScheme.surface

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (!state.isLoading) {
                SaveFab(
                    hasChanges = state.hasChanges,
                    isSaving = state.isSaving,
                    onSave = { viewModel.onEvent(ContributorEditUiEvent.Save) },
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColor),
        ) {
            // Immersive gradient backdrop
            ImmersiveBackdrop(
                colorScheme = colorScheme,
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
                    ErrorContent(
                        error = state.error,
                        onDismiss = { viewModel.onEvent(ContributorEditUiEvent.DismissError) },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(paddingValues),
                    )
                }
                else -> {
                    ArtistStudioContent(
                        state = state,
                        colorScheme = colorScheme,
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
        UnsavedChangesDialog(
            onDiscard = {
                showUnsavedChangesDialog = false
                onBackClick()
            },
            onKeepEditing = { showUnsavedChangesDialog = false },
        )
    }
}

// =============================================================================
// COLOR SCHEME - Dynamic palette from contributor ID
// =============================================================================

/**
 * Rich color scheme derived from contributor's avatar hue.
 * More saturated than detail screen for editing atmosphere.
 */
data class ContributorColorScheme(
    val primary: Color,
    val primaryDark: Color,
    val primaryMuted: Color,
    val onPrimary: Color,
)

@Composable
private fun rememberContributorColorScheme(contributorId: String): ContributorColorScheme {
    return remember(contributorId) {
        val baseColor = avatarColorForUser(contributorId)

        // Derive darker and muted variants from the base color
        ContributorColorScheme(
            primary = baseColor,
            primaryDark = baseColor.copy(
                red = baseColor.red * 0.6f,
                green = baseColor.green * 0.6f,
                blue = baseColor.blue * 0.6f,
            ),
            primaryMuted = baseColor.copy(
                red = baseColor.red * 0.8f,
                green = baseColor.green * 0.8f,
                blue = baseColor.blue * 0.8f,
                alpha = 0.7f,
            ),
            onPrimary = Color.White,
        )
    }
}

// =============================================================================
// IMMERSIVE BACKDROP
// =============================================================================

@Composable
private fun ImmersiveBackdrop(
    colorScheme: ContributorColorScheme,
    surfaceColor: Color,
) {
    val gradientColors = listOf(
        colorScheme.primaryDark,
        colorScheme.primaryMuted.copy(alpha = 0.7f),
        colorScheme.primaryMuted.copy(alpha = 0.3f),
        surfaceColor.copy(alpha = 0.95f),
        surfaceColor,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .background(Brush.verticalGradient(gradientColors)),
    )
}

// =============================================================================
// EXTENDED FAB
// =============================================================================

@Composable
private fun SaveFab(
    hasChanges: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
) {
    val isEnabled = hasChanges && !isSaving
    val contentColor = if (isEnabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    ExtendedFloatingActionButton(
        onClick = { if (isEnabled) onSave() },
        icon = {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    tint = contentColor,
                )
            }
        },
        text = {
            Text(
                text = if (isSaving) "Saving..." else "Save Changes",
                color = contentColor,
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

// =============================================================================
// DIALOGS
// =============================================================================

@Composable
private fun UnsavedChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Unsaved Changes") },
        text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
        shape = MaterialTheme.shapes.large,
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard")
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text("Keep Editing")
            }
        },
    )
}

@Composable
private fun ErrorContent(
    error: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = error ?: "Unknown error",
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onDismiss) {
            Text("Dismiss")
        }
    }
}

// =============================================================================
// ARTIST STUDIO CONTENT
// =============================================================================

@Composable
private fun ArtistStudioContent(
    state: ContributorEditUiState,
    colorScheme: ContributorColorScheme,
    onEvent: (ContributorEditUiEvent) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isMediumOrLarger = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Identity Header with avatar and name
        IdentityHeader(
            imagePath = state.imagePath,
            name = state.name,
            colorScheme = colorScheme,
            onNameChange = { onEvent(ContributorEditUiEvent.NameChanged(it)) },
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

// =============================================================================
// IDENTITY HEADER - Avatar + Name side by side
// =============================================================================

@Composable
private fun IdentityHeader(
    imagePath: String?,
    name: String,
    colorScheme: ContributorColorScheme,
    onNameChange: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(16.dp),
    ) {
        // Floating back button
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

        Spacer(modifier = Modifier.height(24.dp))

        // Avatar + Name row (matching BookEditScreen's identity header)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Large editable avatar (120dp)
            ElevatedCard(
                shape = CircleShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = colorScheme.primary,
                ),
                modifier = Modifier.size(120.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (imagePath != null) {
                        AsyncImage(
                            model = imagePath,
                            contentDescription = "Contributor photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        )
                    } else {
                        Text(
                            text = getInitials(name),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = GoogleSansDisplay,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = colorScheme.onPrimary,
                        )
                    }
                }
            }

            // Name field - Large editorial style (matching BookEditScreen's title field)
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                textStyle = TextStyle(
                    fontFamily = GoogleSansDisplay,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                placeholder = {
                    Text(
                        "Name",
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
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// =============================================================================
// SINGLE COLUMN LAYOUT (Mobile)
// =============================================================================

@Composable
private fun SingleColumnCardsLayout(
    state: ContributorEditUiState,
    onEvent: (ContributorEditUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StudioCard(title = "Biography") {
            BiographyCardContent(state = state, onEvent = onEvent)
        }

        StudioCard(title = "Links") {
            LinksCardContent(state = state, onEvent = onEvent)
        }

        StudioCard(title = "Dates") {
            DatesCardContent(state = state, onEvent = onEvent)
        }

        StudioCard(title = "Also Known As") {
            AliasesCardContent(state = state, onEvent = onEvent)
        }
    }
}

// =============================================================================
// TWO COLUMN LAYOUT (Tablet)
// =============================================================================

@Composable
private fun TwoColumnCardsLayout(
    state: ContributorEditUiState,
    onEvent: (ContributorEditUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Full-width: Biography (primary content)
        StudioCard(title = "Biography") {
            BiographyCardContent(state = state, onEvent = onEvent)
        }

        // Two-column grid
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Left column: Links + Dates
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StudioCard(title = "Links") {
                    LinksCardContent(state = state, onEvent = onEvent)
                }

                StudioCard(title = "Dates") {
                    DatesCardContent(state = state, onEvent = onEvent)
                }
            }

            // Right column: Aliases (can grow long)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StudioCard(title = "Also Known As") {
                    AliasesCardContent(state = state, onEvent = onEvent)
                }
            }
        }
    }
}

// =============================================================================
// STUDIO CARD
// =============================================================================

@Composable
private fun StudioCard(
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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

@Composable
private fun BiographyCardContent(
    state: ContributorEditUiState,
    onEvent: (ContributorEditUiEvent) -> Unit,
) {
    ListenUpTextArea(
        value = state.description,
        onValueChange = { onEvent(ContributorEditUiEvent.DescriptionChanged(it)) },
        label = "Bio",
        placeholder = "Enter a biography...",
    )
}

@Composable
private fun LinksCardContent(
    state: ContributorEditUiState,
    onEvent: (ContributorEditUiEvent) -> Unit,
) {
    ListenUpTextField(
        value = state.website,
        onValueChange = { onEvent(ContributorEditUiEvent.WebsiteChanged(it)) },
        label = "Website",
        placeholder = "https://...",
    )
}

@Composable
private fun DatesCardContent(
    state: ContributorEditUiState,
    onEvent: (ContributorEditUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ListenUpDatePicker(
            value = state.birthDate,
            onValueChange = { onEvent(ContributorEditUiEvent.BirthDateChanged(it)) },
            label = "Birth Date",
            placeholder = "Select birth date",
        )

        ListenUpDatePicker(
            value = state.deathDate,
            onValueChange = { onEvent(ContributorEditUiEvent.DeathDateChanged(it)) },
            label = "Death Date",
            placeholder = "Select death date",
        )
    }
}

/**
 * Aliases card with merge functionality.
 *
 * Add pen names to merge into this contributor.
 * E.g., Adding "Richard Bachman" to Stephen King will:
 * - Re-link all "Richard Bachman" books to Stephen King
 * - Delete the Richard Bachman contributor
 * - Store "Richard Bachman" as an alias name
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AliasesCardContent(
    state: ContributorEditUiState,
    onEvent: (ContributorEditUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.aliases.isEmpty()) {
            Text(
                text = "No pen names yet. Add aliases to merge other contributors into this one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.aliases.forEach { alias ->
                    AliasChip(
                        aliasName = alias,
                        onRemove = { onEvent(ContributorEditUiEvent.RemoveAlias(alias)) },
                    )
                }
            }
        }

        // Search field for adding aliases
        ListenUpAutocompleteField(
            value = state.aliasSearchQuery,
            onValueChange = { onEvent(ContributorEditUiEvent.AliasSearchQueryChanged(it)) },
            results = state.aliasSearchResults,
            onResultSelected = { result -> onEvent(ContributorEditUiEvent.AliasSelected(result)) },
            onSubmit = { query ->
                val topResult = state.aliasSearchResults.firstOrNull()
                if (topResult != null && topResult.name.equals(query, ignoreCase = true)) {
                    onEvent(ContributorEditUiEvent.AliasSelected(topResult))
                } else if (query.isNotBlank()) {
                    onEvent(ContributorEditUiEvent.AliasEntered(query))
                }
            },
            resultContent = { result ->
                AutocompleteResultItem(
                    name = result.name,
                    subtitle = if (result.bookCount > 0) {
                        "${result.bookCount} ${if (result.bookCount == 1) "book" else "books"} will be merged"
                    } else {
                        "No books - will just add as alias"
                    },
                    onClick = { onEvent(ContributorEditUiEvent.AliasSelected(result)) },
                )
            },
            placeholder = "Search contributor to merge, or type pen name...",
            isLoading = state.aliasSearchLoading,
        )
    }
}

@Composable
private fun AliasChip(
    aliasName: String,
    onRemove: () -> Unit,
) {
    InputChip(
        selected = false,
        onClick = { },
        label = { Text(aliasName) },
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
                contentDescription = "Remove $aliasName",
                modifier = Modifier
                    .size(InputChipDefaults.AvatarSize)
                    .clickable { onRemove() },
            )
        },
    )
}
