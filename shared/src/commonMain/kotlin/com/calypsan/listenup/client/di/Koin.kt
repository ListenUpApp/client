package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.data.remote.api.ListenUpApi
import com.calypsan.listenup.client.data.repository.InstanceRepositoryImpl
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import com.calypsan.listenup.client.domain.usecase.GetInstanceUseCase
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Network layer dependencies.
 * Provides HTTP client and API configuration.
 */
val networkModule = module {
    single {
        ListenUpApi(baseUrl = getBaseUrl())
    }
}

/**
 * Platform-specific base URL for the API.
 * - Android emulator: 10.0.2.2 (maps to host's localhost)
 * - iOS simulator: localhost/127.0.0.1
 * - Physical devices: Use your computer's LAN IP
 */
expect fun getBaseUrl(): String

/**
 * Repository layer dependencies.
 * Binds repository interfaces to their implementations.
 */
val repositoryModule = module {
    singleOf(::InstanceRepositoryImpl) bind InstanceRepository::class
}

/**
 * Use case layer dependencies.
 * Creates use case instances for business logic.
 */
val useCaseModule = module {
    factoryOf(::GetInstanceUseCase)
}

/**
 * All shared modules that should be loaded in both Android and iOS.
 */
val sharedModules = listOf(
    networkModule,
    repositoryModule,
    useCaseModule
)

/**
 * Platform-specific initialization function.
 * Each platform (Android/iOS) implements this to set up Koin appropriately.
 *
 * @param additionalModules Platform-specific modules to include
 */
expect fun initializeKoin(additionalModules: List<Module> = emptyList())
