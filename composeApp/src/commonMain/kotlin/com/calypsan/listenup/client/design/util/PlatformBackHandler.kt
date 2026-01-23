package com.calypsan.listenup.client.design.util

import androidx.compose.runtime.Composable

/**
 * Platform-specific back handler.
 *
 * - Android: Intercepts the system back gesture/button
 * - Desktop: No-op (no system back button concept)
 *
 * @param enabled Whether the back handler is active
 * @param onBack Callback when back is triggered
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
