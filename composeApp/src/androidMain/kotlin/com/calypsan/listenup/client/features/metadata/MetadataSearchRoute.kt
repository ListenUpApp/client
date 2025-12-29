package com.calypsan.listenup.client.features.metadata

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.presentation.metadata.MetadataViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Route for the book metadata search screen.
 *
 * Handles ViewModel initialization and navigation to match preview.
 */
@Composable
fun MetadataSearchRoute(
    bookId: String,
    onResultSelected: (asin: String) -> Unit,
    onBack: () -> Unit,
    viewModel: MetadataViewModel = koinViewModel(),
) {
    val bookRepository: BookRepositoryContract = koinInject()
    val state by viewModel.state.collectAsState()

    // Load book and initialize search
    LaunchedEffect(bookId) {
        val book = bookRepository.getBook(bookId)
        if (book != null) {
            viewModel.initForBook(
                bookId = bookId,
                title = book.title,
                author = book.authors.firstOrNull()?.name ?: "",
            )

            // If book has ASIN, search by ASIN, otherwise search by title+author
            if (book.asin != null) {
                viewModel.updateQuery(book.asin!!)
            }
            // Always auto-search when screen opens
            viewModel.search()
        }
    }

    MetadataSearchScreen(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onSearch = viewModel::search,
        onRegionSelected = viewModel::changeRegion,
        onResultClick = { result ->
            viewModel.selectMatch(result)
            onResultSelected(result.asin)
        },
        onBack = onBack,
    )
}
