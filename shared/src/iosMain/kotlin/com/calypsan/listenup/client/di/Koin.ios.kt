package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.domain.usecase.GetInstanceUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * iOS-specific Koin initialization.
 *
 * Starts Koin with shared modules plus any iOS-specific modules.
 * Should be called from the iOS app's initialization code (typically in App struct).
 *
 * @param additionalModules iOS-specific modules to include
 */
actual fun initializeKoin(additionalModules: List<Module>) {
    startKoin {
        modules(sharedModules + additionalModules)
    }
}

/**
 * iOS simulator connects to host via 127.0.0.1.
 * Using explicit IPv4 address instead of localhost to avoid IPv6 resolution issues.
 */
actual fun getBaseUrl(): String = "http://127.0.0.1:8080"

/**
 * Helper object for accessing Koin dependencies from Swift.
 * Provides strongly-typed accessors that are easier to use from Swift.
 */
object KoinHelper : KoinComponent {
    fun getInstanceUseCase(): GetInstanceUseCase {
        val useCase: GetInstanceUseCase by inject()
        return useCase
    }
}
