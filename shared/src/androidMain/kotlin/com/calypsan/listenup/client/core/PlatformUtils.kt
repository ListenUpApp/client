package com.calypsan.listenup.client.core

import android.os.Build

/**
 * Android implementation of PlatformUtils.
 *
 * Emulator detection uses multiple heuristics to catch all Android emulator types:
 * - Official Android Emulator (FINGERPRINT contains "generic")
 * - Genymotion (MANUFACTURER contains "Genymotion")
 * - Hardware indicators (goldfish, ranchu)
 * - Product name patterns
 *
 * This approach is more reliable than checking a single property.
 */
actual object PlatformUtils {
    actual fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.PRODUCT == "google_sdk" ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
}
