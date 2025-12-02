package com.calypsan.listenup.client.features.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.data.local.db.ContributorWithBookCount
import com.calypsan.listenup.client.design.components.AlphabetIndex
import com.calypsan.listenup.client.design.components.AlphabetScrollbar
import com.calypsan.listenup.client.features.nowplaying.MiniPlayerReservedHeight
import kotlinx.coroutines.launch

/**
 * Content for the Narrators tab in the Library screen.
 *
 * Displays a list of narrators with their book counts.
 *
 * @param narrators List of narrators with book counts
 * @param onNarratorClick Callback when a narrator is clicked
 * @param modifier Optional modifier
 */
@Composable
fun NarratorsContent(
    narrators: List<ContributorWithBookCount>,
    onNarratorClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (narrators.isEmpty()) {
            NarratorsEmptyState()
        } else {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()

            val alphabetIndex = remember(narrators) {
                AlphabetIndex.build(narrators) { it.contributor.name }
            }

            val isScrolling by remember {
                derivedStateOf { listState.isScrollInProgress }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp + MiniPlayerReservedHeight
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = narrators,
                    key = { it.contributor.id }
                ) { narratorWithCount ->
                    // Reuse ContributorCard from AuthorsContent
                    ContributorCard(
                        contributorWithCount = narratorWithCount,
                        onClick = { onNarratorClick(narratorWithCount.contributor.id) }
                    )
                }
            }

            AlphabetScrollbar(
                alphabetIndex = alphabetIndex,
                onLetterSelected = { index ->
                    scope.launch {
                        listState.animateScrollToItem(index)
                    }
                },
                isScrolling = isScrolling,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, bottom = MiniPlayerReservedHeight)
            )
        }
    }
}

/**
 * Empty state when no narrators in library.
 */
@Composable
private fun NarratorsEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.RecordVoiceOver,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "No narrators yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Narrators will appear here when you have audiobooks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
