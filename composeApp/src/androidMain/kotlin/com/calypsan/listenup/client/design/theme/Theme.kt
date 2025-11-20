package com.calypsan.listenup.client.design.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive shape system with larger corner radii.
 * Creates a softer, more expressive visual language compared to standard M3.
 *
 * - extraSmall/small: Input fields, chips
 * - medium (20.dp): Buttons, text fields - primary touch targets
 * - large (28.dp): Cards, elevated surfaces
 * - extraLarge: Fully rounded elements (FABs, avatars)
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = CircleShape
)

/**
 * ListenUp Material 3 Expressive theme.
 *
 * Features:
 * - Dynamic color on Android 12+ (adapts to user's wallpaper)
 * - Fallback to ListenUpOrange seed color on older devices
 * - Display P3 wide color gamut support for HDR displays
 * - Google Sans Flex variable font typography
 * - Expressive shapes with larger corner radii (20-28dp)
 *
 * @param darkTheme Whether to use dark theme. Currently only light theme implemented.
 * @param dynamicColor Whether to use dynamic color from system (Android 12+).
 *                     Defaults to true. Set to false to always use ListenUpOrange seed.
 * @param content The composable content to theme.
 */
@Composable
fun ListenUpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color takes precedence on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicLightColorScheme(LocalContext.current)
        }
        // Fallback to our seed color with Display P3 support
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ListenUpTypography,
        shapes = ExpressiveShapes,
        content = content
    )
}
