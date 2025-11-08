package com.calypsan.listenup.client.di

import org.koin.core.module.Module

/**
 * Android-specific Koin initialization.
 *
 * On Android, Koin is initialized in the Application class
 * where we have access to the Android Context.
 *
 * This function is a no-op on Android.
 */
actual fun initializeKoin(additionalModules: List<Module>) {
    // Android initialization happens in the Application class
    // See: composeApp/src/androidMain/kotlin/.../ListenUpApp.kt
}
