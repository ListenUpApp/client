package com.calypsan.listenup.client.design.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.vanniktech.blurhash.BlurHash

/**
 * Renders a BlurHash placeholder image.
 *
 * BlurHash is decoded to a small bitmap (32x32) and scaled up by Compose.
 * Decoding is cached per blurHash string and takes <1ms.
 *
 * @param blurHash The BlurHash string to decode
 * @param modifier Modifier for the image
 * @param contentDescription Accessibility description
 */
@Composable
fun BlurHashImage(
    blurHash: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val bitmap: Bitmap? = remember(blurHash) {
        BlurHash.decode(blurHash, width = 32, height = 32)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
