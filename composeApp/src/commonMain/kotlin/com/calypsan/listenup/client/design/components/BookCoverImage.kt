package com.calypsan.listenup.client.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.design.util.decodeBlurHash
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.ImageRepository
import com.calypsan.listenup.client.domain.repository.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.compose.koinInject
import androidx.compose.runtime.produceState

private val logger = KotlinLogging.logger {}

/**
 * Smart book cover image with server URL fallback.
 *
 * Loading strategy:
 * 1. If cover exists locally -> display from disk (fast, works offline)
 * 2. If missing locally -> load directly from server URL via Coil with auth
 * 3. Once the download queue saves the cover to disk, subsequent renders use disk
 *
 * Covers are NEVER blank when online.
 */
@Suppress("CognitiveComplexMethod")
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
    val serverConfig: ServerConfig = koinInject()
    val authSession: AuthSession = koinInject()
    val context = LocalPlatformContext.current

    var imageLoaded by remember(bookId) { mutableStateOf(false) }

    // Resolve: local file or server URL
    val imageRequest by produceState<ImageRequest?>(
        initialValue = null,
        key1 = bookId,
        key2 = coverPath,
    ) {
        value =
            withContext(Dispatchers.IO) {
                val localPath = coverPath ?: imageRepository.getBookCoverPath(BookId(bookId))

                val exists = imageRepository.bookCoverExists(BookId(bookId))
                logger.info { "BookCoverImage: bookId=$bookId, exists=$exists" }
                if (exists) {
                    // Local file — load from disk
                    ImageRequest
                        .Builder(context)
                        .data(localPath)
                        .memoryCacheKey("$bookId:cover")
                        .diskCacheKey("$bookId:cover")
                        .build()
                } else {
                    // No local file — server URL fallback
                    val baseUrl = serverConfig.getActiveUrl()?.value
                    val token = authSession.getAccessToken()?.value
                    logger.info { "BookCoverImage: FALLBACK bookId=$bookId, url=$baseUrl/api/v1/covers/$bookId" }
                    if (baseUrl != null) {
                        ImageRequest
                            .Builder(context)
                            .data("$baseUrl/api/v1/covers/$bookId")
                            .apply {
                                if (token != null) {
                                    httpHeaders(
                                        NetworkHeaders
                                            .Builder()
                                            .set("Authorization", "Bearer $token")
                                            .build(),
                                    )
                                }
                            }.build()
                    } else {
                        ImageRequest
                            .Builder(context)
                            .data(localPath)
                            .build()
                    }
                }
            }
    }

    if (blurHash != null) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            if (!imageLoaded) {
                val bitmap: ImageBitmap? =
                    remember(blurHash) {
                        decodeBlurHash(blurHash, width = 32, height = 32)
                    }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Success) {
                            imageLoaded = true
                        }
                        onState?.invoke(state)
                    },
                )
            }
        }
    } else if (imageRequest != null) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            onState = onState,
        )
    }
}
