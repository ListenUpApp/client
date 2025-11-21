import SwiftUI
import Shared

/// Simple navigation coordinator for ListenUp iOS app.
///
/// Provides type-safe navigation between screens.
/// For now, starts with unauthenticated flow (ServerSetup).
/// TODO: Add auth-driven navigation when auth system is fully implemented.
@Observable
final class AppNavigator {
    /// Current navigation path (managed by NavigationStack)
    var path = NavigationPath()

    private let dependencies: Dependencies

    init(dependencies: Dependencies = .shared) {
        self.dependencies = dependencies
    }

    // MARK: - Navigation Actions

    /// Navigate to a specific route
    func navigate(to route: Route) {
        path.append(route)
    }

    /// Go back one screen
    func goBack() {
        if !path.isEmpty {
            path.removeLast()
        }
    }

    /// Pop to root screen
    func popToRoot() {
        path = NavigationPath()
    }

    // MARK: - Root View

    /// Root view - starts with ServerSetup
    @ViewBuilder
    var rootView: some View {
        ServerSetupView()
    }

    /// View factory for navigation destinations
    @ViewBuilder
    func view(for route: Route) -> some View {
        switch route {
        case .serverSetup:
            ServerSetupView()

        case .login:
            LoginView()

        case .library:
            LibraryView()
        }
    }
}
