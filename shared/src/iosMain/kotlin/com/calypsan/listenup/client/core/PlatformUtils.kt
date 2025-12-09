package com.calypsan.listenup.client.core

import platform.UIKit.UIDevice

/**
 * iOS implementation of PlatformUtils.
 *
 * Simulator detection checks if device name contains "Simulator".
 * This is the standard approach for iOS and works reliably across
 * all simulator types (iPhone, iPad, etc.).
 */
actual object PlatformUtils {
    actual fun isEmulator(): Boolean = UIDevice.currentDevice.name.contains("Simulator", ignoreCase = true)
}
