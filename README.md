<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/logo-dark.svg" />
    <source media="(prefers-color-scheme: light)" srcset=".github/logo-light.svg" />
    <img src=".github/logo-light.svg" alt="ListenUp" width="120" />
  </picture>
</p>

<h1 align="center">ListenUp</h1>

<p align="center">
  <strong>A modern audiobook player for Android &amp; Desktop</strong>
</p>

<p align="center">
  <a href="https://kotlinlang.org/docs/multiplatform.html"><img src="https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Multiplatform" /></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Android-36-3DDC84?logo=android&logoColor=white" alt="Android" /></a>
  <a href="https://www.jetbrains.com/compose-multiplatform/"><img src="https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform" /></a>
  <img src="https://img.shields.io/badge/License-AGPL--3.0-blue" alt="License" />
</p>
<p align="center">
  ListenUp is a <a href="https://kotlinlang.org/docs/multiplatform.html">Kotlin Multiplatform</a> audiobook player that connects to a <a href="#">ListenUp server</a>. It's built with Kotline Multiplatform and designed to be offline-first — download your books, sync your progress in real time, and pick up where you left off on any device.
</p>


---

## Screenshots

<!-- TODO: Add screenshots -->

<p align="center"><em>Screenshots coming soon</em></p>

---

## Features

- 🎧 **Audiobook playback** — chapter navigation, sleep timer, playback speed control
- 📥 **Offline downloads** — download books for listening without a connection
- 🔄 **Real-time sync** — progress syncs across devices via Server-Sent Events (SSE)
- 📚 **Collection-based library** — browse by collection, contributor, series, or tag
- 📖 **Shelves** — organize your library with custom shelves
- 👥 **Social features** — activity feed, listening leaderboard
- 🔍 **Discover** — browse and search your server's catalog
- 🛠️ **Admin tools** — manage collections, categories, inbox, and backups from the app
- 🎨 **Material 3 Expressive** — dynamic color, adaptive layouts, modern design
- 🖥️ **Cross-platform** — single codebase for Android and Desktop (JVM)

## Platforms

| Platform | Status | Audio Engine |
|----------|--------|-------------|
| Android  | ✅ Primary | Media3 / ExoPlayer |
| Desktop (JVM) | 🚧 In progress | FFmpeg via JavaCV |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/), Material 3, Navigation 3 |
| DI | [Koin](https://insert-koin.io/) |
| Networking | [Ktor](https://ktor.io/) |
| Database | [Room](https://developer.android.com/training/data-storage/room) (Multiplatform) |
| Image Loading | [Coil](https://coil-kt.github.io/coil/) |
| Playback (Android) | [Media3 / ExoPlayer](https://developer.android.com/media/media3) |
| Playback (Desktop) | [JavaCV / FFmpeg](https://github.com/bytedeco/javacv) |
| Serialization | [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) |
| Code Quality | Detekt, Spotless (ktlint) |
| Language | Kotlin 2.3, Java 17 |

## Architecture

```
listenup/client
├── shared/            # Kotlin Multiplatform library (Android, Desktop, iOS)
│   ├── domain/        #   Models, repositories, use cases, playback interfaces
│   ├── data/          #   API clients (Ktor), Room database, sync engine (SSE)
│   └── core/          #   Error handling, shared utilities
├── composeApp/        # Compose Multiplatform UI (Android + Desktop)
│   ├── features/      #   Feature-based packages (home, library, nowplaying, ...)
│   ├── design/        #   Design system — theme, components, utilities
│   ├── navigation/    #   Navigation 3 graph and routes
│   └── platform/      #   Platform expect/actual declarations
├── androidApp/        # Android application entry point
└── desktopApp/        # Desktop (JVM) application entry point
```

The app follows **MVVM** with a clean separation between layers:

- **`shared`** contains all business logic, data access, and platform abstractions. No UI dependencies.
- **`composeApp`** contains all Compose UI code, organized by feature. Each feature typically has a Screen, ViewModel, and composable components.
- **`androidApp`** and **`desktopApp`** are thin entry points that wire up platform-specific services and launch the shared Compose UI.

## Getting Started

### Prerequisites

- **JDK 17+** (the build enforces Java 17 compatibility)
- **Android SDK** with API 33 (for Android builds)
- **Android Studio** Otter or later (recommended)

### Building

```bash
# Clone
git clone https://github.com/calypsan/listenup-client.git
cd listenup-client

# Android (debug APK)
./gradlew androidApp:assembleDebug

# Desktop
./gradlew desktopApp:run
```

### Connecting to a Server

ListenUp is a client-server app. You need a running [ListenUp server](# "Link TBD") to use it.

1. Launch the app
2. On the connect screen, enter your server URL (e.g. `http://192.168.1.100:3000`) Or, use MDNS if on the same network.
3. Sign in or accept an invite link

The app supports automatic server discovery on local networks via mDNS (desktop).

## License



AGPL-3.0.
