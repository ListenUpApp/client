import SwiftUI
import Shared

/// Coordinates the server setup flow using state-driven navigation.
///
/// Flow: ServerSelect → (optional) ManualEntry
///
/// When a server is activated, the Kotlin layer updates AuthState,
/// which automatically transitions the app to the next flow.
/// No callbacks needed.
struct ServerFlowCoordinator: View {

    @State private var showManualEntry = false

    var body: some View {
        NavigationStack {
            ServerSelectView(
                showManualEntry: $showManualEntry
            )
            .navigationDestination(isPresented: $showManualEntry) {
                ServerManualEntryView(
                    onBack: {
                        showManualEntry = false
                    }
                )
            }
        }
    }
}
