package com.calypsan.listenup.client.design.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import kotlin.math.cos
import kotlin.math.pow

/**
 * Pure-JVM BlurHash decoder using AWT BufferedImage.
 *
 * Implements the BlurHash algorithm (https://blurha.sh) without
 * Android dependencies by rendering to a BufferedImage.
 */
actual fun decodeBlurHash(blurHash: String, width: Int, height: Int): ImageBitmap? {
    if (blurHash.length < 6) return null

    val sizeFlag = decode83(blurHash, 0, 1)
    val numY = (sizeFlag / 9) + 1
    val numX = (sizeFlag % 9) + 1

    val expectedLength = 4 + 2 * numX * numY - 2
    if (blurHash.length != expectedLength) return null

    val quantisedMaximumValue = decode83(blurHash, 1, 2)
    val maximumValue = (quantisedMaximumValue + 1) / 166f

    val colors = Array(numX * numY) { i ->
        if (i == 0) {
            val value = decode83(blurHash, 2, 6)
            decodeDC(value)
        } else {
            val value = decode83(blurHash, 4 + i * 2, 4 + i * 2 + 2)
            decodeAC(value, maximumValue)
        }
    }

    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var r = 0f
            var g = 0f
            var b = 0f

            for (j in 0 until numY) {
                for (i in 0 until numX) {
                    val basis = cos(Math.PI * i * x / width).toFloat() *
                        cos(Math.PI * j * y / height).toFloat()
                    val color = colors[i + j * numX]
                    r += color[0] * basis
                    g += color[1] * basis
                    b += color[2] * basis
                }
            }

            val intR = linearToSRGB(r)
            val intG = linearToSRGB(g)
            val intB = linearToSRGB(b)
            image.setRGB(x, y, (intR shl 16) or (intG shl 8) or intB)
        }
    }

    return image.toComposeImageBitmap()
}

private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

private fun decode83(str: String, from: Int, to: Int): Int {
    var value = 0
    for (i in from until to) {
        val index = CHARS.indexOf(str[i])
        if (index == -1) return 0
        value = value * 83 + index
    }
    return value
}

private fun decodeDC(value: Int): FloatArray {
    val r = value shr 16
    val g = (value shr 8) and 255
    val b = value and 255
    return floatArrayOf(sRGBToLinear(r), sRGBToLinear(g), sRGBToLinear(b))
}

private fun decodeAC(value: Int, maximumValue: Float): FloatArray {
    val quantR = value / (19 * 19)
    val quantG = (value / 19) % 19
    val quantB = value % 19
    return floatArrayOf(
        signPow((quantR - 9f) / 9f, 2f) * maximumValue,
        signPow((quantG - 9f) / 9f, 2f) * maximumValue,
        signPow((quantB - 9f) / 9f, 2f) * maximumValue,
    )
}

private fun signPow(value: Float, exp: Float): Float {
    return kotlin.math.sign(value) * kotlin.math.abs(value).pow(exp)
}

private fun sRGBToLinear(value: Int): Float {
    val v = value / 255f
    return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
}

private fun linearToSRGB(value: Float): Int {
    val v = value.coerceIn(0f, 1f)
    val srgb = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
    return (srgb * 255 + 0.5f).toInt().coerceIn(0, 255)
}
