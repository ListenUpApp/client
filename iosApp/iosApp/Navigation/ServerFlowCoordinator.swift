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

    /// Navigation state - simple enum, not NavigationPath
    @State private var destination: Destination = .select

    enum Destination: Hashable {
        case select
        case manualEntry
    }

    var body: some View {
        NavigationStack {
            ServerSelectView(
                onManualEntryRequested: {
                    destination = .manualEntry
                }
            )
            .navigationDestination(for: Destination.self) { dest in
                switch dest {
                case .select:
                    EmptyView() // Never pushed, it's the root

                case .manualEntry:
                    ServerManualEntryView(
                        onBack: {
                            destination = .select
                        }
                    )
                }
            }
        }
        .onChange(of: destination) { _, newValue in
            // This could use a NavigationPath if we need deeper navigation
            // For now, simple enum works perfectly
        }
    }
}
