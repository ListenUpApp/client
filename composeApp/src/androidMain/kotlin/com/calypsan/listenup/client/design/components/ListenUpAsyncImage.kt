package com.calypsan.listenup.client.design.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import java.io.File

/**
 * Design system image component with automatic cache invalidation.
 *
 * This component wraps Coil's AsyncImage with intelligent cache-busting
 * for local files. When a file is modified (e.g., a new cover is uploaded),
 * the cache key automatically updates based on the file's last-modified
 * timestamp, ensuring fresh images are displayed without manual version tracking.
 *
 * Usage:
 * ```
 * ListenUpAsyncImage(
 *     path = book.coverPath,
 *     contentDescription = "Book cover",
 *     modifier = Modifier.fillMaxSize(),
 * )
 * ```
 *
 * For edit screens where the file may be overwritten at the same path,
 * pass a `refreshKey` that changes when the content changes:
 * ```
 * ListenUpAsyncImage(
 *     path = state.coverPath,
 *     contentDescription = "Book cover",
 *     refreshKey = state.pendingCoverData, // Triggers refresh when new image selected
 * )
 * ```
 *
 * @param path Local file path to the image, or null for no image
 * @param contentDescription Accessibility description
 * @param modifier Optional modifier
 * @param contentScale How to scale the image (default: Crop)
 * @param refreshKey Optional key to force cache refresh (use when file is overwritten at same path)
 * @param onState Optional callback for loading state changes
 */
@Composable
fun ListenUpAsyncImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    refreshKey: Any? = null,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    val context = LocalContext.current

    val request = remember(path, refreshKey) {
        path?.let {
            // Use file modification time as cache key for automatic invalidation
            val file = File(it)
            val cacheKey = if (file.exists()) "$it:${file.lastModified()}" else it

            ImageRequest.Builder(context)
                .data(it)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .build()
        }
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onState = onState,
    )
}

/**
 * Variant that accepts a file:// prefixed path.
 *
 * Some parts of the codebase use "file://$path" format.
 * This normalizes the path before processing.
 */
@Composable
fun ListenUpAsyncImage(
    filePath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    stripFilePrefix: Boolean = false,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    val normalizedPath = if (stripFilePrefix && filePath?.startsWith("file://") == true) {
        filePath.removePrefix("file://")
    } else {
        filePath
    }

    ListenUpAsyncImage(
        path = normalizedPath,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onState = onState,
    )
}
