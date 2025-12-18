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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter

/**
 * Contributor image with BlurHash placeholder and lazy-loaded real image.
 *
 * Rendering layers:
 * 1. Default gray background (always)
 * 2. BlurHash placeholder (if available, instant)
 * 3. Real image (fades in when loaded)
 *
 * @param imagePath Local path to contributor image, or null if not cached
 * @param blurHash BlurHash string for placeholder, or null
 * @param shape Shape to clip the image (e.g., CircleShape for avatars)
 * @param contentDescription Accessibility description
 * @param modifier Modifier for the container
 */
@Composable
fun ContributorImage(
    imagePath: String?,
    blurHash: String?,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    var imageLoaded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFFE0E0E0)),
    ) {
        // Layer 1: BlurHash placeholder (instant, shows until real image loads)
        if (blurHash != null && !imageLoaded) {
            BlurHashImage(
                blurHash = blurHash,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Layer 2: Real image (fades in over BlurHash)
        if (imagePath != null) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
            ) {
                ListenUpAsyncImage(
                    path = imagePath,
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
