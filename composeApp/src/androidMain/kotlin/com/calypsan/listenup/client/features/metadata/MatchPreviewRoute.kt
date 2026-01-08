package com.calypsan.listenup.client.features.metadata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.presentation.metadata.AudibleRegion
import com.calypsan.listenup.client.presentation.metadata.MetadataViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Route composable for the match preview screen.
 *
 * Handles loading the current book and metadata preview, then displays
 * the MatchPreviewScreen with all necessary data.
 */
@Composable
fun MatchPreviewRoute(
    bookId: String,
    asin: String,
    onBack: () -> Unit,
    onApplySuccess: () -> Unit,
    metadataViewModel: MetadataViewModel = koinViewModel(),
) {
    val bookRepository: BookRepository = koinInject()
    val metadataState by metadataViewModel.state.collectAsState()

    // Load book and metadata if not already loaded
    LaunchedEffect(bookId, asin) {
        // If the metadata wasn't already loaded from the search, load it now
        if (metadataState.previewBook == null || metadataState.selectedMatch?.asin != asin) {
            metadataViewModel.initForBook(bookId, "", "")
            // Create a temporary search result to trigger preview loading
            val tempResult =
                com.calypsan.listenup.client.domain.repository.MetadataSearchResult(
                    asin = asin,
                    title = "",
                )
            metadataViewModel.selectMatch(tempResult)
        }
    }

    // Handle apply success
    LaunchedEffect(metadataState.applySuccess) {
        if (metadataState.applySuccess) {
            onApplySuccess()
        }
    }

    // Get current book from repository
    val currentBook by androidx.compose.runtime.produceState<com.calypsan.listenup.client.domain.model.Book?>(
        initialValue = null,
        key1 = bookId,
    ) {
        value = bookRepository.getBook(bookId)
    }

    // Show loading while data is being fetched
    when {
        currentBook == null || metadataState.isLoadingPreview -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ListenUpLoadingIndicator()
            }
        }

        metadataState.previewError != null -> {
            // API error - book not found on Audible, offer region selection
            NotFoundErrorScreen(
                selectedRegion = metadataState.selectedRegion,
                onRegionSelected = { region ->
                    metadataViewModel.changeRegion(region)
                },
                onBack = onBack,
            )
        }

        metadataState.previewBook == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Failed to load metadata preview",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        else -> {
            MatchPreviewScreen(
                currentBook = currentBook!!,
                newMetadata = metadataState.previewBook!!,
                selections = metadataState.selections,
                isApplying = metadataState.isApplying,
                applyError = metadataState.applyError,
                previewNotFound = metadataState.previewNotFound,
                selectedRegion = metadataState.selectedRegion,
                // Cover selection
                coverOptions = metadataState.coverOptions,
                isLoadingCovers = metadataState.isLoadingCovers,
                selectedCoverUrl = metadataState.selectedCoverUrl,
                onSelectCover = metadataViewModel::selectCover,
                // Callbacks
                onRegionSelected = metadataViewModel::changeRegion,
                onToggleField = metadataViewModel::toggleField,
                onToggleAuthor = metadataViewModel::toggleAuthor,
                onToggleNarrator = metadataViewModel::toggleNarrator,
                onToggleSeries = metadataViewModel::toggleSeries,
                onToggleGenre = metadataViewModel::toggleGenre,
                onApply = metadataViewModel::applyMatch,
                onBack = onBack,
            )
        }
    }
}

/**
 * Error screen when the book cannot be found on Audible.
 * Offers region selection to try different Audible markets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotFoundErrorScreen(
    selectedRegion: AudibleRegion,
    onRegionSelected: (AudibleRegion) -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Book not found on Audible",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "This book couldn't be found in the ${selectedRegion.displayName} " +
                        "Audible catalog. Try a different region:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Region selector chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AudibleRegion.entries.forEach { region ->
                    FilterChip(
                        selected = region == selectedRegion,
                        onClick = { onRegionSelected(region) },
                        label = { Text(region.displayName) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBack) {
                Text("Go Back")
            }
        }
    }
}
