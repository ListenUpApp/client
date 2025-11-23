package com.calypsan.listenup.client.core

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.util.DebugLogger
import okio.Path.Companion.toOkioPath

/**
 * Factory for creating configured Coil ImageLoader instances.
 *
 * Configures Coil for optimal loading of book cover images:
 * - Loads from local file storage (covers pre-downloaded during sync)
 * - Memory cache for fast repeated access (25% of available memory)
 * - Disk cache for decoded bitmaps (50 MB, separate from source images)
 * - Crossfade animation for smooth UX
 */
object ImageLoaderFactory {

    /**
     * Create and configure an ImageLoader for the application.
     *
     * Since covers are stored locally by ImageStorage, Coil loads directly
     * from file paths (e.g., file:///data/user/0/.../covers/book123.jpg).
     *
     * @param context Android application context
     * @param debug Enable debug logging (default: false)
     * @return Configured ImageLoader instance
     */
    fun create(context: Context, debug: Boolean = false): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                // No special components needed - Coil handles file:// URIs natively
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, percent = 0.25) // 25% of app memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(50 * 1024 * 1024) // 50 MB for decoded bitmaps
                    .build()
            }
            .crossfade(enable = true)
            .apply {
                if (debug) {
                    logger(DebugLogger())
                }
            }
            .build()
    }
}
