package com.calypsan.listenup.client.design.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vanniktech.blurhash.BlurHash

actual fun decodeBlurHash(
    blurHash: String,
    width: Int,
    height: Int,
): ImageBitmap? = BlurHash.decode(blurHash, width = width, height = height)?.asImageBitmap()
