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
import androidx.compose.material.icons.outlined.AutoStories
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
import com.calypsan.listenup.client.data.local.db.SeriesWithBooks
import com.calypsan.listenup.client.design.components.AlphabetIndex
import com.calypsan.listenup.client.design.components.AlphabetScrollbar
import kotlinx.coroutines.launch

/**
 * Content for the Series tab in the Library screen.
 *
 * Displays a vertical list of series cards with animated cover stacks.
 * Each card shows overlapping book covers that cycle through all books
 * in the series, with the series name and book count below.
 *
 * @param series List of series with their books
 * @param onSeriesClick Callback when a series is clicked (passes series ID)
 * @param modifier Optional modifier
 */
@Composable
fun SeriesContent(
    series: List<SeriesWithBooks>,
    onSeriesClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (series.isEmpty()) {
            SeriesEmptyState()
        } else {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()

            val alphabetIndex = remember(series) {
                AlphabetIndex.build(series) { it.series.name }
            }

            val isScrolling by remember {
                derivedStateOf { listState.isScrollInProgress }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = series,
                    key = { it.series.id }
                ) { seriesWithBooks ->
                    SeriesCard(
                        seriesWithBooks = seriesWithBooks,
                        onClick = { onSeriesClick(seriesWithBooks.series.id) }
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
                    .padding(end = 4.dp)
            )
        }
    }
}

/**
 * Empty state when no series in library.
 */
@Composable
private fun SeriesEmptyState() {
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
                imageVector = Icons.Outlined.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "No series yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Series will appear here when books are part of a series",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
