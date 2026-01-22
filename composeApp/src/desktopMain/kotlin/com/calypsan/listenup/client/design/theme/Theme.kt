package com.calypsan.listenup.client.design.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
private val ExpressiveShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = CircleShape,
    )

/**
 * Composition local for accessing the current theme's dark mode state.
 * Use this instead of checking system theme to respect the app's actual theme.
 *
 * Usage: val isDark = LocalDarkTheme.current
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * ListenUp Material 3 theme for desktop.
 *
 * Features:
 * - Static color scheme (no dynamic color on desktop)
 * - Respects dark/light mode preference
 * - ListenUpOrange seed color
 * - Expressive shapes with larger corner radii (20-28dp)
 *
 * @param darkTheme Whether to use dark theme. Defaults to true for desktop.
 * @param content The composable content to theme.
 */
@Composable
fun ListenUpTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ListenUpTypography,
            shapes = ExpressiveShapes,
            content = content,
        )
    }
}
