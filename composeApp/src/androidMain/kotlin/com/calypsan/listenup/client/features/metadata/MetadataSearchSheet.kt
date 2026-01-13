package com.calypsan.listenup.client.features.metadata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicatorSmall
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.calypsan.listenup.client.domain.repository.MetadataSearchResult
import com.calypsan.listenup.client.presentation.metadata.MetadataUiState

/**
 * Bottom sheet for searching Audible metadata.
 *
 * Shows a search field and results list. Results display cover, title,
 * author, narrator (key differentiator!), duration, and rating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataSearchSheet(
    state: MetadataUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onResultClick: (MetadataSearchResult) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
        ) {
            // Title
            Text(
                text = "Find on Audible",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Search field
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                label = { Text("Search") },
                placeholder = { Text("Title, author, or narrator...") },
                trailingIcon = {
                    IconButton(
                        onClick = onSearch,
                        enabled = !state.isSearching,
                    ) {
                        if (state.isSearching) {
                            ListenUpLoadingIndicatorSmall()
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error message
            state.searchError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Results
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 400.dp),
            ) {
                items(
                    items = state.searchResults,
                    key = { it.asin },
                ) { result ->
                    MetadataSearchResultItem(
                        result = result,
                        onClick = { onResultClick(result) },
                    )
                }

                // Empty state
                if (state.searchResults.isEmpty() && !state.isSearching && state.searchError == null) {
                    item {
                        Text(
                            text = "Search Audible to find metadata for this book",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single search result item showing Audible book metadata.
 */
@Composable
private fun MetadataSearchResultItem(
    result: MetadataSearchResult,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cover thumbnail
            @Suppress("MagicNumber") // Standard book cover aspect ratio
            AsyncImage(
                model = result.coverUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .width(48.dp)
                        .aspectRatio(1f / 1.5f)
                        .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Author
                if (result.authors.isNotEmpty()) {
                    Text(
                        text = "by ${result.authors.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Narrator - KEY DIFFERENTIATOR from local metadata!
                if (result.narrators.isNotEmpty()) {
                    Text(
                        text = "Narrated by ${result.narrators.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Duration and rating
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    val runtime = result.runtimeMinutes
                    if (runtime != null && runtime > 0) {
                        Text(
                            text = formatDuration(runtime),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val rating = result.rating
                    if (rating != null && rating > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "%.1f".format(rating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format duration in minutes to human-readable string.
 */
private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}
