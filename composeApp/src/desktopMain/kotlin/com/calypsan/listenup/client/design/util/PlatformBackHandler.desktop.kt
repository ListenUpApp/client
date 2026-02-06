package com.calypsan.listenup.client.design.util

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // No system back button on Desktop.
    // Users exit selection mode via the close button in the SelectionToolbar.
}
