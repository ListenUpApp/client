package com.calypsan.listenup.client.design.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * In-memory set of book IDs that have already been attempted for lazy cover fetch.
 * Prevents re-fetching every recomposition. Cleared on app restart.
 */
private val attemptedBookIds = mutableSetOf<String>()

/**
 * Smart book cover image that lazily fetches missing covers from the server.
 *
 * Wraps [ListenUpAsyncImage] with cover resolution logic:
 * 1. If the cover exists locally, displays it immediately
 * 2. If missing, triggers an async download from the server
 * 3. On successful download, the image refreshes automatically
 * 4. Each book ID is only attempted once per session to avoid loops
 *
 * Use this instead of [ListenUpAsyncImage] wherever a book cover is displayed
 * and a bookId is available. For non-book images (contributors, avatars, etc.),
 * continue using [ListenUpAsyncImage] directly.
 *
 * @param bookId The book's unique identifier, used for lazy fetching
 * @param coverPath Local file path to the cover image, or null if unknown
 * @param contentDescription Accessibility description
 * @param modifier Optional modifier
 * @param blurHash BlurHash string for placeholder, or null
 * @param contentScale How to scale the image (default: Crop)
 * @param onState Optional callback for loading state changes
 */
@Composable
fun BookCoverImage(
    bookId: String,
    coverPath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    blurHash: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    val imageRepository: ImageRepository = koinInject()

    // Track refresh key to bust Coil cache after download
    var refreshKey by remember(bookId) { mutableIntStateOf(0) }

    // Resolve cover path — use provided path or fall back to repository path
    val resolvedPath = coverPath ?: imageRepository.getBookCoverPath(BookId(bookId))

    // Lazy fetch if cover is missing and not yet attempted
    LaunchedEffect(bookId) {
        if (bookId !in attemptedBookIds && !imageRepository.bookCoverExists(BookId(bookId))) {
            attemptedBookIds.add(bookId)
            withContext(Dispatchers.IO) {
                val result = imageRepository.downloadBookCover(BookId(bookId))
                if (result is com.calypsan.listenup.client.core.Result.Success && result.data) {
                    // Download succeeded — bump refresh key to trigger recomposition
                    refreshKey++
                }
            }
        }
    }

    ListenUpAsyncImage(
        path = resolvedPath,
        contentDescription = contentDescription,
        modifier = modifier,
        blurHash = blurHash,
        contentScale = contentScale,
        refreshKey = refreshKey,
        onState = onState,
    )
}
