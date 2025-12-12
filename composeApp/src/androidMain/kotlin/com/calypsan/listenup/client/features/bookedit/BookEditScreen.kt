package com.calypsan.listenup.client.features.bookedit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.data.remote.ContributorSearchResult
import com.calypsan.listenup.client.design.components.AutocompleteResultItem
import com.calypsan.listenup.client.design.components.ListenUpAutocompleteField
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.ListenUpTextArea
import com.calypsan.listenup.client.design.components.ListenUpTextField
import com.calypsan.listenup.client.design.components.LocalSnackbarHostState
import com.calypsan.listenup.client.presentation.bookedit.BookEditNavAction
import com.calypsan.listenup.client.presentation.bookedit.BookEditUiEvent
import com.calypsan.listenup.client.presentation.bookedit.BookEditUiState
import com.calypsan.listenup.client.presentation.bookedit.BookEditViewModel
import com.calypsan.listenup.client.presentation.bookedit.ContributorRole
import com.calypsan.listenup.client.presentation.bookedit.EditableContributor
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen for editing book metadata and contributors.
 *
 * Features:
 * - Edit title, subtitle, description, series sequence, publish year
 * - Manage contributors (authors/narrators) with autocomplete search
 * - Debounced contributor search with offline fallback
 * - Change tracking with unsaved changes warning
 *
 * @param bookId The ID of the book to edit
 * @param onBackClick Callback when back button is clicked
 * @param onSaveSuccess Callback after successful save
 * @param viewModel The ViewModel for book editing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookEditScreen(
    bookId: String,
    onBackClick: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: BookEditViewModel = koinViewModel(),
) {
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    val state by viewModel.state.collectAsState()
    val navAction by viewModel.navActions.collectAsState()

    // Handle navigation actions
    LaunchedEffect(navAction) {
        when (val action = navAction) {
            is BookEditNavAction.NavigateBack -> {
                onBackClick()
                viewModel.clearNavAction()
            }
            is BookEditNavAction.ShowSaveSuccess -> {
                snackbarHostState.showSnackbar(action.message)
                onSaveSuccess()
                viewModel.clearNavAction()
            }
            null -> { /* no action */ }
        }
    }

    // Unsaved changes dialog state
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    // Handle system back gesture
    BackHandler(enabled = state.hasChanges) {
        showUnsavedChangesDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Book",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.hasChanges) {
                                showUnsavedChangesDialog = true
                            } else {
                                onBackClick()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.onEvent(BookEditUiEvent.Save) },
                            enabled = state.hasChanges,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save",
                                tint = if (state.hasChanges) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
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
                        onEvent = viewModel::onEvent,
                    )
                }
            }
        }
    }

    // Unsaved changes dialog
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
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

/**
 * Main content for book edit screen.
 */
@Composable
private fun BookEditContent(
    state: BookEditUiState,
    onEvent: (BookEditUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Metadata Section
        MetadataSection(
            state = state,
            onEvent = onEvent,
        )

        HorizontalDivider()

        // Contributors Section
        ContributorsSection(
            state = state,
            onEvent = onEvent,
        )
    }
}

/**
 * Metadata editing fields.
 */
@Composable
private fun MetadataSection(
    state: BookEditUiState,
    onEvent: (BookEditUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Metadata",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        // Title
        ListenUpTextField(
            value = state.title,
            onValueChange = { onEvent(BookEditUiEvent.TitleChanged(it)) },
            label = "Title",
        )

        // Subtitle
        ListenUpTextField(
            value = state.subtitle,
            onValueChange = { onEvent(BookEditUiEvent.SubtitleChanged(it)) },
            label = "Subtitle",
        )

        // Description
        ListenUpTextArea(
            value = state.description,
            onValueChange = { onEvent(BookEditUiEvent.DescriptionChanged(it)) },
            label = "Description",
            placeholder = "Enter a description...",
        )

        // Series info (read-only name, editable sequence)
        state.seriesName?.let { seriesName ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Series name is read-only, use OutlinedTextField with weight
                OutlinedTextField(
                    value = seriesName,
                    onValueChange = { },
                    label = { Text("Series") },
                    singleLine = true,
                    readOnly = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(2f),
                )
                // Series sequence uses OutlinedTextField for weight support
                OutlinedTextField(
                    value = state.seriesSequence,
                    onValueChange = { onEvent(BookEditUiEvent.SeriesSequenceChanged(it)) },
                    label = { Text("Book #") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Publish Year
        ListenUpTextField(
            value = state.publishYear,
            onValueChange = { onEvent(BookEditUiEvent.PublishYearChanged(it)) },
            label = "Publish Year",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(150.dp),
        )
    }
}

/**
 * Contributors section with per-role search fields.
 */
@Composable
private fun ContributorsSection(
    state: BookEditUiState,
    onEvent: (BookEditUiEvent) -> Unit,
) {
    // Dialog state for confirming role section removal
    var roleToRemove by remember { mutableStateOf<ContributorRole?>(null) }
    var contributorsToRemoveCount by remember { mutableStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Contributors",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

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
                        // Show confirmation dialog for 2+ contributors
                        roleToRemove = role
                        contributorsToRemoveCount = contributorsForRole.size
                    } else {
                        // Remove immediately for 0-1 contributors
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

    // Confirmation dialog for removing role section with multiple contributors
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

/**
 * Section for contributors of a specific role.
 * Shows chips for existing contributors and a search field to add more.
 */
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

        // Search field with autocomplete
        ListenUpAutocompleteField(
            value = searchQuery,
            onValueChange = onQueryChange,
            results = searchResults,
            onResultSelected = onResultSelected,
            onSubmit = { query ->
                val trimmed = query.trim()
                if (trimmed.isNotEmpty()) {
                    // If there's a top result, select it; otherwise add as new
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

        // Show "Add new" chip when query is valid and no exact match exists
        val trimmedQuery = searchQuery.trim()
        val hasExactMatch = searchResults.any { it.name.equals(trimmedQuery, ignoreCase = true) }
        if (trimmedQuery.length >= 2 && !isSearching && !hasExactMatch) {
            AssistChip(
                onClick = { onNameEntered(trimmedQuery) },
                label = { Text("Add \"$trimmedQuery\"") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

/**
 * Chip displaying a contributor with remove button.
 */
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

/**
 * Button to add a new role section.
 */
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
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
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
