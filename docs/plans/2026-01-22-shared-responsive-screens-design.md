# Shared Responsive Screens Design

> Cross-platform UI sharing between Android and Desktop using Compose Multiplatform

## Overview

Move all screen implementations from `androidMain` to `commonMain`, enabling code sharing between Android and Desktop. Both platforms use the same responsive, adaptive UI code with minimal platform-specific abstractions.

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Code sharing approach | Screens in `commonMain` | Maximum code reuse, single source of truth |
| Navigation | Multiplatform Navigation 3 | Already invested in Nav3; JetBrains provides multiplatform version in 1.10.0 |
| Adaptive layouts | Multiplatform Material 3 Adaptive | Window size classes work cross-platform |
| Migration strategy | Incremental by feature | Prove architecture with auth flow first |

## Architecture

### Source Set Structure

```
composeApp/src/
├── commonMain/kotlin/com/calypsan/listenup/client/
│   ├── design/
│   │   ├── theme/
│   │   │   ├── Color.kt          # Brand colors, light/dark palettes
│   │   │   ├── Type.kt           # Typography
│   │   │   └── Theme.kt          # ListenUpTheme composable
│   │   └── components/           # All 23+ shared components
│   ├── features/
│   │   ├── shell/                # AppShell, navigation bars/rail/drawer
│   │   ├── auth/                 # Login, Register, PendingApproval
│   │   ├── connect/              # ServerConnect, ServerSetup
│   │   ├── home/                 # Home screen
│   │   ├── library/              # Library screens
│   │   └── ...                   # All other features
│   ├── navigation/
│   │   ├── Routes.kt             # Type-safe route definitions
│   │   └── ListenUpNavHost.kt    # Navigation graph
│   └── platform/
│       ├── PlatformTheme.kt      # expect: platformColorScheme()
│       └── PlatformHaptics.kt    # expect: rememberHapticFeedback()
│
├── androidMain/kotlin/.../
│   ├── platform/
│   │   ├── PlatformTheme.android.kt   # actual: dynamic color support
│   │   └── PlatformHaptics.android.kt # actual: vibration feedback
│   ├── MainActivity.kt                 # Entry point
│   └── playback/                       # Android audio service
│
├── desktopMain/kotlin/.../
│   ├── platform/
│   │   ├── PlatformTheme.desktop.kt   # actual: static theme
│   │   └── PlatformHaptics.desktop.kt # actual: no-op
│   └── di/
│       └── PlatformModule.kt          # Desktop DI bindings
```

### Dependencies

Add to `libs.versions.toml`:

```toml
[versions]
navigation3-multiplatform = "1.0.0-alpha06"
material3-adaptive-multiplatform = "1.3.0-alpha02"

[libraries]
navigation3-runtime = { module = "org.jetbrains.androidx.navigation3:navigation3-runtime", version.ref = "navigation3-multiplatform" }
navigation3-ui = { module = "org.jetbrains.androidx.navigation3:navigation3-ui", version.ref = "navigation3-multiplatform" }
material3-adaptive = { module = "org.jetbrains.compose.material3.adaptive:adaptive", version.ref = "material3-adaptive-multiplatform" }
material3-adaptive-layout = { module = "org.jetbrains.compose.material3.adaptive:adaptive-layout", version.ref = "material3-adaptive-multiplatform" }
material3-adaptive-navigation = { module = "org.jetbrains.compose.material3.adaptive:adaptive-navigation", version.ref = "material3-adaptive-multiplatform" }
```

In `composeApp/build.gradle.kts`:

```kotlin
commonMain.dependencies {
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.material3.adaptive)
    implementation(libs.material3.adaptive.layout)
    implementation(libs.material3.adaptive.navigation)
}

androidMain.dependencies {
    // Android-specific: deep linking support
    implementation(libs.androidx.navigation3.ui.android)
}
```

## Platform Abstractions

### Dynamic Color (Theme)

```kotlin
// commonMain/platform/PlatformTheme.kt
@Composable
expect fun platformColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean
): ColorScheme
```

```kotlin
// androidMain/platform/PlatformTheme.android.kt
@Composable
actual fun platformColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean
): ColorScheme {
    val context = LocalContext.current
    return when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
}
```

```kotlin
// desktopMain/platform/PlatformTheme.desktop.kt
@Composable
actual fun platformColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean
): ColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
```

### Haptic Feedback

```kotlin
// commonMain/platform/PlatformHaptics.kt
interface HapticFeedbackHandler {
    fun performClick()
    fun performLongPress()
}

@Composable
expect fun rememberHapticFeedback(): HapticFeedbackHandler
```

```kotlin
// androidMain - wraps LocalHapticFeedback
// desktopMain - no-op implementation
```

## Navigation

### Routes

```kotlin
// commonMain/navigation/Routes.kt
@Serializable
sealed interface Route {
    // Auth flow
    @Serializable data object ServerConnect : Route
    @Serializable data object ServerSetup : Route
    @Serializable data object Login : Route
    @Serializable data object Register : Route
    @Serializable data class InviteRegistration(
        val serverUrl: String,
        val inviteCode: String
    ) : Route

    // Main app
    @Serializable data class Shell(
        val initialTab: ShellTab = ShellTab.Home
    ) : Route

    // Detail screens
    @Serializable data class BookDetail(val bookId: String) : Route
    @Serializable data class SeriesDetail(val seriesId: String) : Route
    // ... etc
}
```

### NavHost

```kotlin
// commonMain/navigation/ListenUpNavHost.kt
@Composable
fun ListenUpNavHost(
    navController: NavController,
    startDestination: Route
) {
    NavDisplay(
        controller = navController,
        startDestination = startDestination
    ) {
        scene<Route.ServerConnect> {
            ServerConnectScreen(
                onConnected = { navController.navigate(Route.Login) }
            )
        }
        scene<Route.Login> {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Route.Shell()) },
                onNavigateToRegister = { navController.navigate(Route.Register) }
            )
        }
        scene<Route.Shell> { entry ->
            AppShell(
                navController = navController,
                initialTab = entry.initialTab
            )
        }
        // ... remaining routes
    }
}
```

## Screen Pattern

All screens follow this pattern:

```kotlin
@Composable
fun FeatureScreen(
    // Navigation callbacks
    onNavigateToX: () -> Unit,
    onNavigateBack: () -> Unit,
    // ViewModel via Koin
    viewModel: FeatureViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Adaptive layout using window size class
    val windowInfo = currentWindowAdaptiveInfo()
    val isExpanded = windowInfo.windowSizeClass
        .isWidthAtLeastBreakpoint(1000)

    if (isExpanded) {
        TwoPaneLayout(...)
    } else {
        SinglePaneLayout(...)
    }
}
```

## Adaptive Layout Strategy

Three breakpoints, consistent across platforms:

| Width | Classification | Navigation | Layout |
|-------|---------------|------------|--------|
| < 600dp | Compact | Bottom bar | Single pane |
| 600-999dp | Medium | Rail | Single pane (wider) |
| >= 1000dp | Expanded | Drawer | Two pane where appropriate |

## Implementation Order

### Phase 1: Infrastructure
1. Add multiplatform dependencies to version catalog
2. Update `composeApp/build.gradle.kts`
3. Create `expect/actual` for platformColorScheme
4. Create `expect/actual` for rememberHapticFeedback
5. Move theme files to `commonMain`

### Phase 2: Components
6. Move all design components to `commonMain`
7. Fix any platform-specific imports

### Phase 3: Minimal Auth Flow
8. Move ServerConnectScreen to `commonMain`
9. Move LoginScreen to `commonMain`
10. Move AppShell + navigation components to `commonMain`
11. Create Routes.kt in `commonMain`
12. Create ListenUpNavHost in `commonMain`
13. Wire up desktopApp entry point
14. Test auth flow on both platforms

### Phase 4: Complete Auth Flow
15. Move RegisterScreen
16. Move PendingApprovalScreen
17. Move InviteRegistrationScreen
18. Move ServerSetupScreen
19. Move SetupScreen

### Phase 5: Remaining Features
20. Library screens
21. Book/Series/Contributor detail screens
22. Edit screens
23. Admin screens
24. Settings/Profile screens
25. NowPlaying/Player screens

## Platform-Specific Considerations

### Android
- Deep linking via `navigation3-ui-android`
- Dynamic color (Material You)
- Haptic feedback
- System bars / edge-to-edge
- Media session integration

### Desktop
- Window management (min size, remember position)
- Keyboard shortcuts
- System tray (future)
- File dialogs for downloads
- Native menu bar (future)

## Testing Strategy

- **Unit tests**: ViewModels in `commonTest`
- **UI tests**: Screen composables with fake data in `commonTest`
- **Platform tests**: Integration tests in `androidTest` and `desktopTest`

## Success Criteria

1. Auth flow works identically on Android and Desktop
2. Adaptive layouts respond correctly to window resizing on Desktop
3. No code duplication between platforms for UI logic
4. Desktop app launches and authenticates successfully
