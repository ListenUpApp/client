package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.presentation.nowplaying.NowPlayingViewModel
import com.calypsan.listenup.client.presentation.player.DesktopPlayerViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Koin module for playback-layer presentation (now-playing surface + Desktop full player).
 *
 * Both Android and Desktop Koin graphs include this module. NowPlayingViewModel is consumed
 * from Android Compose only today; DesktopPlayerViewModel is Desktop-only.
 *
 * `NowPlayingViewModel` uses `factoryOf` rather than `viewModelOf` because the latter ships
 * in `koin-compose-viewmodel`, which is not on the shared classpath — `factoryOf` from
 * `koin-core` produces an equivalent factory binding that `koinInject<T>()` and Android's
 * `koinViewModel<T>()` both resolve.
 *
 * `DesktopPlayerViewModel` stays bound as a `single` (Q5 fallback) because the Desktop graph
 * has multiple non-Compose constructor-injection sites (e.g. `GlobalMediaKeyManager` in
 * `desktopAppModule`) that must share the same VM instance with the UI. Migrating those to a
 * Compose-scoped lookup is non-trivial and out of scope for E2.2.2 — E2.2.3 will revisit this
 * when the Desktop player VM is consolidated into the shared shape.
 */
val playbackPresentationModule =
    module {
        factoryOf(::NowPlayingViewModel)
        single {
            DesktopPlayerViewModel(
                playbackManager = get(),
                playbackController = get(),
                audioPlayer = get(),
                progressTracker = get(),
                bookRepository = get(),
                playbackPreferences = get(),
                sleepTimerManager = get(),
            )
        }
    }
