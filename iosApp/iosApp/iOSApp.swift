import SwiftUI
import Shared

@main
struct iOSApp: App {
    @State private var navigator = AppNavigator()

    init() {
        // Initialize Koin dependency injection with shared modules
        Koin_iosKt.initializeKoin(additionalModules: [])
    }

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