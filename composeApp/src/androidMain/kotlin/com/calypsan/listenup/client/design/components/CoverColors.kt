package com.calypsan.listenup.client.design.components

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Color scheme extracted from cover art using the Palette API.
 * Provides a comprehensive set of colors for dynamic theming based on cover images.
 */
data class CoverColors(
    /** The most dominant color in the image */
    val dominant: Color,
    /** A vibrant color extracted from the image */
    val vibrant: Color,
    /** A dark vibrant color for backgrounds */
    val darkVibrant: Color,
    /** A light vibrant color for accents */
    val lightVibrant: Color,
    /** A muted color for subtle backgrounds */
    val muted: Color,
    /** A dark muted color for darker backgrounds */
    val darkMuted: Color,
    /** Contrasting text color for use on dominant background */
    val onDominant: Color,
)

/**
 * Extracts a color scheme from a bitmap using the Palette API.
 * Runs synchronously - should be called from a background thread.
 *
 * @param bitmap The bitmap to extract colors from
 * @param fallbackColor Color to use when extraction fails
 * @return Extracted color scheme
 */
fun extractCoverColors(
    bitmap: Bitmap,
    fallbackColor: Color,
): CoverColors {
    val palette = Palette.from(bitmap).generate()

    val dominant = palette.dominantSwatch?.rgb?.let { Color(it) } ?: fallbackColor
    val vibrant = palette.vibrantSwatch?.rgb?.let { Color(it) } ?: dominant
    val darkVibrant = palette.darkVibrantSwatch?.rgb?.let { Color(it) } ?: dominant
    val lightVibrant = palette.lightVibrantSwatch?.rgb?.let { Color(it) } ?: dominant
    val muted = palette.mutedSwatch?.rgb?.let { Color(it) } ?: dominant
    val darkMuted = palette.darkMutedSwatch?.rgb?.let { Color(it) } ?: dominant

    // Calculate contrasting text color
    val onDominant =
        palette.dominantSwatch?.let {
            Color(it.titleTextColor)
        } ?: Color.White

    return CoverColors(
        dominant = dominant,
        vibrant = vibrant,
        darkVibrant = darkVibrant,
        lightVibrant = lightVibrant,
        muted = muted,
        darkMuted = darkMuted,
        onDominant = onDominant,
    )
}

/**
 * Extracts the most dominant/vibrant color from a bitmap.
 * Simpler alternative when full color scheme is not needed.
 *
 * @param bitmap The bitmap to extract color from
 * @return The dominant color, or null if extraction fails
 */
fun extractDominantColor(bitmap: Bitmap): Color? =
    try {
        val palette = Palette.from(bitmap).generate()
        val swatch =
            palette.vibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch
        swatch?.rgb?.let { Color(it) }
    } catch (e: Exception) {
        null
    }

/**
 * Composable that loads an image and extracts its color palette.
 * Palette extraction runs on background thread to avoid blocking UI.
 * Uses file modification time for automatic cache invalidation.
 *
 * @param imagePath Local file path to the cover image
 * @param refreshKey Optional key to force re-extraction (e.g., staging file changes)
 * @param fallbackColor Color to use when extraction fails
 * @return Extracted color scheme, or default scheme based on fallbackColor
 */
@Composable
fun rememberCoverColors(
    imagePath: String?,
    refreshKey: Any? = null,
    fallbackColor: Color = MaterialTheme.colorScheme.primaryContainer,
): CoverColors {
    val context = LocalContext.current
    val defaultColors =
        CoverColors(
            dominant = fallbackColor,
            vibrant = fallbackColor,
            darkVibrant = fallbackColor,
            lightVibrant = fallbackColor,
            muted = fallbackColor,
            darkMuted = fallbackColor,
            onDominant = Color.White,
        )

    // Build cache key using file modification time for automatic invalidation
    val cacheKey by produceState<String?>(
        initialValue = imagePath,
        key1 = imagePath,
        key2 = refreshKey,
    ) {
        value =
            withContext(Dispatchers.IO) {
                imagePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) "$path:${file.lastModified()}" else path
                }
            }
    }

    // Return default if no image path
    if (imagePath == null || cacheKey == null) {
        return defaultColors
    }

    var colorScheme by remember(cacheKey) { mutableStateOf(defaultColors) }

    // Load image with hardware acceleration disabled for Palette extraction
    val painter =
        rememberAsyncImagePainter(
            model =
                ImageRequest
                    .Builder(context)
                    .data(imagePath)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .allowHardware(false) // Required for Palette extraction
                    .build(),
        )

    LaunchedEffect(painter.state, cacheKey) {
        val state = painter.state
        if (state is AsyncImagePainter.State.Success) {
            // Run Palette extraction on background thread
            val extracted =
                withContext(Dispatchers.Default) {
                    try {
                        val bitmap = state.result.image.toBitmap()
                        extractCoverColors(bitmap, fallbackColor)
                    } catch (e: Exception) {
                        null
                    }
                }
            if (extracted != null) {
                colorScheme = extracted
            }
        }
    }

    return colorScheme
}

/**
 * Creates a default CoverColors instance with a single color.
 * Useful for previews and fallback states.
 */
fun defaultCoverColors(color: Color = Color(0xFF424242)): CoverColors =
    CoverColors(
        dominant = color,
        vibrant = color,
        darkVibrant = color,
        lightVibrant = color,
        muted = color,
        darkMuted = color,
        onDominant = Color.White,
    )
