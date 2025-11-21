# iOS Native SwiftUI Design
**Date:** 2025-11-20
**Status:** Approved
**Scope:** Feature parity with Android app using native SwiftUI

## Vision

Build an ultra-modern iOS app using **liquid glass aesthetic**, iOS 17+ best practices, and native SwiftUI patterns. The app maintains feature parity with the Android side while feeling authentically iOS-native.

## Core Principles

- **Native over shared UI**: SwiftUI views consuming shared Kotlin business logic
- **Liquid glass aesthetic**: Translucent materials, blur effects, depth, and vibrancy
- **iOS 17+ patterns**: @Observable, NavigationStack, async/await, modern previews
- **Composable components**: Reusable design system matching brand identity
- **Single source of truth**: Business logic in Kotlin, UI logic in Swift

## Architecture

### Layer Structure

```
┌─────────────────────────────────────┐
│     SwiftUI Views (Presentation)    │
│  - ServerSetupView                  │
│  - LoginView                        │
│  - LibraryView                      │
└─────────────────────────────────────┘
              ↕
┌─────────────────────────────────────┐
│   Swift Bridge Layer                │
│  - Dependencies container           │
│  - ViewModel wrappers (@Observable) │
│  - Kotlin extensions                │
└─────────────────────────────────────┘
              ↕
┌─────────────────────────────────────┐
│   Shared Kotlin Framework           │
│  - ViewModels (StateFlow)           │
│  - UseCases                         │
│  - Repositories                     │
└─────────────────────────────────────┘
```

### Navigation System

**Auth-driven navigation** mirroring Android pattern:

```swift
enum Route: Hashable {
    case serverSetup
    case login
    case library
}
```

**AppNavigator** (@Observable):
- Observes Kotlin AuthState flow (Loading → Unauthenticated → Authenticated)
- Manages NavigationPath based on auth state
- Provides type-safe navigation methods
- Handles deep links and state restoration

**Root structure:**
```swift
@main
struct ListenUpApp: App {
    @State private var navigator = AppNavigator()

    var body: some Scene {
        WindowGroup {
            NavigationStack(path: $navigator.path) {
                navigator.rootView
                    .navigationDestination(for: Route.self) { route in
                        navigator.view(for: route)
                    }
            }
            .environment(navigator)
        }
    }
}
```

### Dependency Injection

**Swift container wrapping Koin** for SwiftUI-native injection:

```swift
@Observable
final class Dependencies {
    static let shared = Dependencies()
    private let koin: Koin_coreKoin

    // Lazy, type-safe accessors
    lazy var getInstanceUseCase: GetInstanceUseCase
    lazy var serverConnectViewModel: ServerConnectViewModel
    lazy var settingsRepository: SettingsRepository
}

// SwiftUI Environment integration
extension EnvironmentValues {
    var dependencies: Dependencies
}
```

**Benefits:**
- SwiftUI-idiomatic @Environment injection
- Easy mocking for previews and tests
- Single point of Koin access
- No direct Koin coupling in views

### ViewModel Bridging

**Observable wrappers** for Kotlin ViewModels with **bulletproof memory safety**.

#### Critical Implementation Details

**Problem 1: Retain Cycle Trap**
A naive Task capture of `self` creates a retain cycle, preventing `deinit` from ever being called.

**Problem 2: StateFlow Bridging**
Without SKIE, Kotlin's `StateFlow` doesn't conform to Swift's `AsyncSequence`, so we can't use `for await` directly.

#### Solution: Kotlin Helper + Memory-Safe Swift Wrapper

**Step 1: Create Kotlin StateFlow Helper**

File: `shared/src/iosMain/kotlin/com/calypsan/listenup/client/core/StateFlowExtensions.kt`

```kotlin
package com.calypsan.listenup.client.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Collect StateFlow values from Swift using a callback.
 * Returns a Job that can be cancelled to stop collection.
 *
 * Dispatches on Main thread to ensure Swift UI updates are safe.
 *
 * Usage from Swift:
 * ```swift
 * let job = viewModel.state.collect { state in
 *     self.handleState(state)
 * }
 * // Later: job.cancel(cause: nil)
 * ```
 */
fun <T> StateFlow<T>.collect(
    onEach: (T) -> Unit
): Job {
    return CoroutineScope(Dispatchers.Main).launch {
        collect { value ->
            onEach(value)
        }
    }
}
```

**Step 2: Memory-Safe Swift Wrapper**

```swift
@Observable
final class ServerConnectViewModelWrapper {
    private let kotlinVM: ServerConnectViewModel
    private var collectionJob: Kotlinx_coroutines_coreJob?

    // Swift properties mirroring Kotlin UiState
    var serverUrl: String = ""
    var isLoading: Bool = false
    var error: ServerConnectError? = nil
    var isConnectEnabled: Bool = false
    var isVerified: Bool = false

    init(viewModel: ServerConnectViewModel) {
        self.kotlinVM = viewModel
        observeState()
    }

    private func observeState() {
        // ✅ Memory-safe: [weak self] prevents retain cycle
        // ✅ Main thread: Guaranteed by Dispatchers.Main in Kotlin
        collectionJob = kotlinVM.state.collect { [weak self] state in
            guard let self = self else { return }

            self.serverUrl = state.serverUrl
            self.isLoading = state.isLoading
            self.error = state.error
            self.isConnectEnabled = state.isConnectEnabled
            self.isVerified = state.isVerified
        }
    }

    // Event forwarding
    func onUrlChanged(_ url: String) {
        kotlinVM.onEvent(event: ServerConnectUiEvent.UrlChanged(url: url))
    }

    func onConnectClicked() {
        kotlinVM.onEvent(event: ServerConnectUiEvent.ConnectClicked())
    }

    deinit {
        // Cancel Kotlin Job when wrapper is deallocated
        collectionJob?.cancel(cause: nil)
    }
}
```

**Why this is bulletproof:**
- ✅ **No retain cycles**: `[weak self]` capture prevents memory leaks
- ✅ **No SKIE dependency**: Pure Kotlin/Swift interop
- ✅ **Proper cancellation**: Kotlin Job cancelled on deinit
- ✅ **Main thread safety**: Dispatchers.Main ensures UI updates are safe
- ✅ **Clean separation**: Bridge logic in Kotlin where it belongs
- ✅ **Reusable pattern**: Works for any StateFlow in the app

## Liquid Glass Design System

### Color Palette

```swift
extension Color {
    // Brand colors
    static let listenUpOrange = Color(hex: "FF6B4A")

    // Adaptive backgrounds
    static let glassBackground = Color(.systemBackground)
    static let glassSecondary = Color(.secondarySystemBackground)
}
```

### Core Components

#### 1. GlassCard
Frosted container using `.ultraThinMaterial`:

```swift
struct GlassCard<Content: View>: View {
    let content: Content
    var padding: CGFloat = 24

    var body: some View {
        content
            .padding(padding)
            .background {
                RoundedRectangle(cornerRadius: 28)
                    .fill(.ultraThinMaterial)
                    .shadow(color: .black.opacity(0.05), radius: 10, y: 5)
            }
    }
}
```

#### 2. ListenUpButton
Primary CTA with gradient fill:

```swift
struct ListenUpButton: View {
    let title: String
    let isLoading: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Group {
                if isLoading {
                    ProgressView().tint(.white)
                } else {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(.white)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background {
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.listenUpOrange.gradient)
            }
        }
        .disabled(isLoading)
    }
}
```

#### 3. GlassTextField
Input field with `.regularMaterial` background:

```swift
struct GlassTextField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    var error: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.secondary)

            TextField(placeholder, text: $text)
                .padding(16)
                .background {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(.regularMaterial)
                }

            if let error {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
            }
        }
    }
}
```

### Design Principles

- **Translucency over opacity**: Use materials (.ultraThin, .regular, .thick)
- **Depth through blur**: Layer cards with varying blur intensities
- **Vibrancy for text**: Automatic color adaptation
- **Dynamic color**: Respect system light/dark mode
- **Native components**: Leverage SwiftUI defaults, don't reinvent

## Screen Architecture

### Standard Screen Pattern

```swift
struct [Feature]View: View {
    @Environment(\.dependencies) private var deps
    @State private var viewModel: [Feature]ViewModelWrapper

    init(dependencies: Dependencies = .shared) {
        _viewModel = State(initialValue:
            [Feature]ViewModelWrapper(
                viewModel: dependencies.get[Feature]ViewModel()
            )
        )
    }

    var body: some View {
        // Layout based on state
    }
}
```

### ServerSetupView Example

Combines gradient background (from mockup) with liquid glass form card:

```swift
struct ServerSetupView: View {
    @State private var viewModel: ServerConnectViewModelWrapper

    var body: some View {
        ZStack {
            // Gradient background (matches mockup)
            LinearGradient(
                colors: [
                    Color(hex: "8B3A3A"),
                    Color(hex: "E8704A")
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // Logo section (1/3)
                LogoSection()
                    .frame(maxHeight: .infinity)

                // Form card (2/3) with liquid glass
                GlassCard {
                    FormContent(viewModel: viewModel)
                }
                .frame(maxHeight: .infinity, alignment: .top)
            }
        }
    }
}
```

**Key features:**
- State-driven UI (loading, error, success)
- Keyboard-aware layouts
- Accessibility built-in
- Respects safe areas

## Testing & Previews

### Preview-Driven Development

Use iOS 17+ `#Preview` macro with mock dependencies:

```swift
#Preview("Server Setup - Empty") {
    ServerSetupView(
        dependencies: .mock(
            serverConnectVM: .init(
                state: ServerConnectUiState()
            )
        )
    )
}

#Preview("Server Setup - Loading") {
    ServerSetupView(
        dependencies: .mock(
            serverConnectVM: .init(
                state: ServerConnectUiState(
                    serverUrl: "https://audiobooks.example.com",
                    isLoading: true
                )
            )
        )
    )
}

#Preview("Server Setup - Error") {
    ServerSetupView(
        dependencies: .mock(
            serverConnectVM: .init(
                state: ServerConnectUiState(
                    serverUrl: "https://invalid.url",
                    error: ServerConnectError.networkError(...)
                )
            )
        )
    )
}
```

### Mock Dependencies

```swift
extension Dependencies {
    static func mock(
        serverConnectVM: ServerConnectViewModel? = nil,
        settingsRepo: SettingsRepository? = nil
    ) -> Dependencies {
        let mock = Dependencies()
        // Override with mocks
        if let vm = serverConnectVM {
            mock.serverConnectViewModel = vm
        }
        return mock
    }
}
```

### Testing Strategy

- **Unit tests**: ViewModel wrappers observe Kotlin state correctly
- **Snapshot tests**: Visual regression for components
- **Integration tests**: Navigation flows
- **Shared logic**: Business logic tested in Kotlin (shared module)

**Benefits:**
- Fast iteration with live previews
- Test all UI states easily
- No simulator needed for basic development
- Catches UI bugs early

## Implementation Scope

### Phase 1: Foundation
- Dependencies container
- AppNavigator
- Route enum
- Core components (GlassCard, ListenUpButton, GlassTextField)

### Phase 2: Screens
- ServerSetupView (with ServerConnectViewModelWrapper)
- LoginView (placeholder until Android implements)
- LibraryView (placeholder until Android implements)

### Phase 3: Polish
- Animations and transitions
- Accessibility labels
- Dark mode refinement
- Error state handling

## Design References

- Mockups in `~/Downloads/Photos-1-001/` (inspiration only)
- Liquid glass aesthetic: iOS 17+ native feel
- Color scheme: Orange (#FF6B4A) primary, adaptive backgrounds
- Typography: System fonts (SF Pro)
- Corner radius: 12-28pt depending on component size

## Success Criteria

- ✅ App builds and runs on iOS 17+
- ✅ Feature parity with Android (ServerSetup screen)
- ✅ Liquid glass aesthetic throughout
- ✅ All UI states have previews
- ✅ Navigation flows match Android pattern
- ✅ Dark mode support
- ✅ Accessibility (VoiceOver, Dynamic Type)
- ✅ No force unwraps or unsafe casts in views
- ✅ SwiftUI best practices (@Observable, modern syntax)

---

**Next Steps:**
1. Set up git worktree for isolated development
2. Create detailed implementation plan
3. Build component library first
4. Implement screens incrementally
5. Test with previews throughout
