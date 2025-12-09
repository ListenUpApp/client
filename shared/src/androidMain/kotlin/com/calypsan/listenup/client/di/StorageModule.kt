package com.calypsan.listenup.client.di

import android.content.Context
import com.calypsan.listenup.client.core.AndroidSecureStorage
import com.calypsan.listenup.client.core.SecureStorage
import com.calypsan.listenup.client.data.local.images.AndroidImageStorage
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.repository.AndroidNetworkMonitor
import com.calypsan.listenup.client.data.repository.NetworkMonitor
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific storage module.
 * Provides SecureStorage implementation using Android Keystore.
 */
actual val platformStorageModule: Module = module {
    single<SecureStorage> {
        val context: Context = get()
        AndroidSecureStorage(context)
    }

    single<ImageStorage> {
        val context: Context = get()
        AndroidImageStorage(context)
    }

    single<NetworkMonitor> {
        val context: Context = get()
        AndroidNetworkMonitor(context)
    }
}
