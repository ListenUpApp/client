@file:Suppress("LongMethod")

package com.calypsan.listenup.client.features.shelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.calypsan.listenup.client.design.components.ListenUpTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.presentation.shelf.CreateEditShelfViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.stringResource
import listenup.composeapp.generated.resources.Res
import listenup.composeapp.generated.resources.admin_back
import listenup.composeapp.generated.resources.admin_delete
import listenup.composeapp.generated.resources.design_cancel
import listenup.composeapp.generated.resources.shelf_delete_shelf
import listenup.composeapp.generated.resources.shelf_delete_shelf_2
import listenup.composeapp.generated.resources.shelf_description_optional
import listenup.composeapp.generated.resources.shelf_eg_to_read_favorites_mystery
import listenup.composeapp.generated.resources.shelf_this_will_permanently_delete_this
import listenup.composeapp.generated.resources.shelf_whats_this_shelf_for

/**
 * Screen for creating or editing a shelf.
 *
 * @param shelfId If provided, edit this shelf. If null, create new shelf.
 * @param onBack Callback for back navigation
 * @param viewModel The ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditShelfScreen(
    shelfId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateEditShelfViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Initialize based on mode
    LaunchedEffect(shelfId) {
        if (shelfId != null) {
            viewModel.initEdit(shelfId)
        } else {
            viewModel.initCreate()
        }
    }

    // Show error in snackbar
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isEditing) "Edit Shelf" else "Create Shelf",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.admin_back),
                        )
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(Res.string.shelf_delete_shelf),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { paddingValues ->
        if (state.isLoading) {
            ListenUpLoadingIndicator(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(16.dp),
            ) {
                // Name field
                ListenUpTextField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = "Name",
                    placeholder = stringResource(Res.string.shelf_eg_to_read_favorites_mystery),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Description field
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text(stringResource(Res.string.shelf_description_optional)) },
                    placeholder = { Text(stringResource(Res.string.shelf_whats_this_shelf_for)) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Save button
                Button(
                    onClick = { viewModel.save(onSuccess = onBack) },
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            when {
                                state.isSaving -> "Saving..."
                                state.isEditing -> "Save Changes"
                                else -> "Create Shelf"
                            },
                    )
                }

                // Delete confirmation dialog
                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        shape = MaterialTheme.shapes.large,
                        title = { Text(stringResource(Res.string.shelf_delete_shelf_2)) },
                        text = {
                            Text(stringResource(Res.string.shelf_this_will_permanently_delete_this))
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showDeleteDialog = false
                                    viewModel.delete(onSuccess = onBack)
                                },
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                            ) {
                                Text(stringResource(Res.string.admin_delete))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text(stringResource(Res.string.design_cancel))
                            }
                        },
                    )
                }
            }
        }
    }
}
