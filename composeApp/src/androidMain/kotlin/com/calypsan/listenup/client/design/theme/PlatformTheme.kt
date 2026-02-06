package com.calypsan.listenup.client.design.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Android-specific color scheme selection with Material You dynamic color support.
 */
@Composable
actual fun platformColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
): ColorScheme {
    val context = LocalContext.current

    return when {
        // Dynamic color on Android 12+ - respects wallpaper and system theme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        // Fallback for older devices or when dynamic color is disabled
        darkTheme -> {
            DarkColorScheme
        }

        else -> {
            LightColorScheme
        }
    }
}
