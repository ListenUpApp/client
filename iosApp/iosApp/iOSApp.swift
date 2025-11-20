import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Initialize Koin dependency injection with shared modules
        Koin_iosKt.initializeKoin(additionalModules: [])
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}