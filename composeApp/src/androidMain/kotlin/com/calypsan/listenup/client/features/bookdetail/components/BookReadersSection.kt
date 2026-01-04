package com.calypsan.listenup.client.features.bookdetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.theme.GoogleSansDisplay
import com.calypsan.listenup.client.presentation.bookdetail.BookReadersViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Section displaying readers of a book on the Book Detail screen.
 *
 * Shows:
 * - User's own reading history (if available)
 * - Other readers who are currently reading or have finished
 * - "See all" link if more than 3 other readers
 *
 * Does not render if there are no readers (including the user).
 *
 * @param bookId The book ID to load readers for
 * @param onUserClick Callback when a reader row is clicked (navigates to user profile)
 * @param modifier Optional modifier
 * @param viewModel The ViewModel for loading readers data
 */
@Composable
fun BookReadersSection(
    bookId: String,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookReadersViewModel = koinViewModel(),
) {
    LaunchedEffect(bookId) {
        viewModel.loadReaders(bookId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    // Loading state: show skeleton
    if (state.isLoading) {
        Column(modifier = modifier.fillMaxWidth()) {
            // Header skeleton
            Text(
                text = "Readers",
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontFamily = GoogleSansDisplay,
                        fontWeight = FontWeight.Bold,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            ListenUpLoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        return
    }

    // Don't render section if empty (no readers at all)
    if (!state.hasYourHistory && state.otherReaders.isEmpty()) {
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header with "See all" link if > 3 readers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Readers",
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontFamily = GoogleSansDisplay,
                        fontWeight = FontWeight.Bold,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (state.otherReaders.size > 3) {
                TextButton(onClick = { /* TODO: Navigate to full readers list */ }) {
                    Text("See all")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Your reading history card (if available)
        if (state.hasYourHistory) {
            YourReadingHistory(
                sessions = state.yourSessions,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Other readers (take 3)
        val displayedReaders = state.otherReaders.take(3)
        displayedReaders.forEach { reader ->
            ReaderRow(
                reader = reader,
                onUserClick = onUserClick,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
