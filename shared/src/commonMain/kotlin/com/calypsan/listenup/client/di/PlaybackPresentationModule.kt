package com.calypsan.listenup.client.di

import com.calypsan.listenup.client.presentation.nowplaying.NowPlayingViewModel
import com.calypsan.listenup.client.presentation.player.DesktopPlayerViewModel
import org.koin.dsl.module

/**
 * Koin module for playback-layer presentation (now-playing surface + Desktop full player).
 *
 * Both Android and Desktop Koin graphs include this module. NowPlayingViewModel is consumed
 * from Android Compose only today; DesktopPlayerViewModel is Desktop-only.
 *
 * Both VMs are bound as `single` because they are app-session singletons hoisted to the
 * authenticated nav root and shared across the entire session. Their state — overlayFlow,
 * isExpandedFlow, and viewModelScope-launched chapter-change and sleep-event collectors
 * — must survive Compose recompositions and Android configuration changes. A `factory` binding would silently drop that state on every `koinInject<T>()`
 * call from a recomposing nav-root composable; a Compose `viewModel {}` binding would
 * require `koin-compose-viewmodel` (not on the shared classpath) and would not cover
 * the Desktop non-Compose constructor-injection sites (e.g. `GlobalMediaKeyManager` in
 * `desktopAppModule`).
 *
 * `viewModelOf` is not used because it ships in `koin-compose-viewmodel`, which is not on
 * the shared classpath. The `single` scope is the same precedent as `LibraryViewModel`
 * in `presentationModule` (a hoisted app-session VM).
 *
 * E2.2.3 will likely consolidate `DesktopPlayerViewModel` into the shared shape, at which
 * point the Desktop binding can be retired in favour of the consolidated VM.
 */
val playbackPresentationModule =
    module {
        single {
            NowPlayingViewModel(
                playbackManager = get(),
                bookRepository = get(),
                sleepTimerManager = get(),
                playbackController = get(),
                playbackPreferences = get(),
            )
        }
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
