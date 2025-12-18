package com.calypsan.listenup.client.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter

/**
 * Book cover with BlurHash placeholder and lazy-loaded real cover.
 *
 * Rendering layers:
 * 1. Default gray background (always)
 * 2. BlurHash placeholder (if available, instant)
 * 3. Real cover image (fades in when loaded)
 *
 * @param coverPath Local path to cover image, or null if not cached
 * @param blurHash BlurHash string for placeholder, or null
 * @param contentDescription Accessibility description
 * @param modifier Modifier for the container
 */
@Composable
fun BookCover(
    coverPath: String?,
    blurHash: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    var imageLoaded by remember { mutableStateOf(false) }

    Box(modifier = modifier.background(Color(0xFFE0E0E0))) {
        // Layer 1: BlurHash placeholder (instant, shows until real cover loads)
        if (blurHash != null && !imageLoaded) {
            BlurHashImage(
                blurHash = blurHash,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Layer 2: Real cover (fades in over BlurHash)
        if (coverPath != null) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
            ) {
                ListenUpAsyncImage(
                    path = coverPath,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Success) {
                            imageLoaded = true
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
