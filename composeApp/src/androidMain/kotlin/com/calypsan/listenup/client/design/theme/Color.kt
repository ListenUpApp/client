package com.calypsan.listenup.client.design.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces

/**
 * ListenUp brand orange - primary color for light theme fallback.
 * Used when dynamic color is unavailable (Android <12) or disabled by user.
 *
 * Defined in Display P3 color space for HDR support on compatible displays.
 * Displays ~25% more colors than sRGB, with richer saturation and vibrancy.
 */
val ListenUpOrange =
    Color(
        red = 1.0f,
        green = 0.42f,
        blue = 0.29f,
        colorSpace = ColorSpaces.DisplayP3,
    )

/**
 * Light color scheme with ListenUp orange as seed color.
 * Surface container colors are tinted with the primary color to create
 * the "alive" Material You feel even without dynamic color.
 *
 * Surface hierarchy (low to high tint):
 * - surface: Base background
 * - surfaceContainer: Default container
 * - surfaceContainerHigh: Elevated cards (key change for visual depth)
 */
internal val LightColorScheme =
    lightColorScheme(
        primary = ListenUpOrange,
        // Tinted surface containers derived from orange seed
        surface = Color(0xFFFFF8F6),
        surfaceContainer = Color(0xFFFFEDE9),
        surfaceContainerLow = Color(0xFFFFF1EE),
        surfaceContainerHigh = Color(0xFFFFE4DE),
        surfaceContainerHighest = Color(0xFFFFDAD2),
    )
