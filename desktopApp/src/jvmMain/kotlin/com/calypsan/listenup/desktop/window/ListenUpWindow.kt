package com.calypsan.listenup.desktop.window

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import com.calypsan.listenup.client.design.theme.ListenUpTheme
import com.calypsan.listenup.desktop.DesktopApp
import com.calypsan.listenup.desktop.tray.ListenUpTray

/**
 * Main window for the ListenUp desktop application.
 *
 * Features:
 * - Material 3 theming with ListenUp brand colors
 * - System tray integration for background playback
 * - Minimize to tray support (when playing)
 */
@Composable
fun ListenUpWindow(
    state: WindowState,
    onCloseRequest: () -> Unit,
) {
    var isVisible by remember { mutableStateOf(true) }

    // System tray for background playback
    ListenUpTray(
        onOpenWindow = { isVisible = true },
        onExitApplication = onCloseRequest,
    )

    Window(
        onCloseRequest = {
            // TODO: Minimize to tray instead of closing if playing
            // For now, just close
            onCloseRequest()
        },
        visible = isVisible,
        title = "ListenUp",
        state = state,
    ) {
        ListenUpTheme(
            darkTheme = true, // Default to dark mode for desktop
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                DesktopApp()
            }
        }
    }
}
