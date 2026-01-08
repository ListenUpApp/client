package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.core.IosSecureStorage
import com.calypsan.listenup.client.core.SecureStorage
import com.calypsan.listenup.client.data.local.images.CommonImageStorage
import com.calypsan.listenup.client.data.local.images.CoverColorExtractor
import com.calypsan.listenup.client.data.local.images.IosCoverColorExtractor
import com.calypsan.listenup.client.data.local.images.IosStoragePaths
import com.calypsan.listenup.client.data.local.images.StoragePaths
import com.calypsan.listenup.client.data.repository.IosNetworkMonitor
import com.calypsan.listenup.client.domain.repository.ImageStorage
import com.calypsan.listenup.client.domain.repository.NetworkMonitor
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS-specific storage module.
 * Provides SecureStorage implementation using iOS Keychain Services.
 */
actual val platformStorageModule: Module =
    module {
        single<SecureStorage> {
            IosSecureStorage()
        }

        single<StoragePaths> {
            IosStoragePaths()
        }

        single<ImageStorage> {
            CommonImageStorage(get())
        }

        single<NetworkMonitor> {
            IosNetworkMonitor()
        }

        single<CoverColorExtractor> {
            IosCoverColorExtractor()
        }
    }
