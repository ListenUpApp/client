package com.calypsan.listenup.client.design.theme

import androidx.compose.material3.Typography

/**
 * ListenUp typography system.
 *
 * Platform-specific implementations:
 * - Android: Uses Google Sans Flex variable font for expressive typography
 * - Desktop: Uses system sans-serif font for native platform feel
 */
expect val ListenUpTypography: Typography
