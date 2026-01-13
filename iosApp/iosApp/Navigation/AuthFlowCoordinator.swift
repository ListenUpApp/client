import SwiftUI
import Shared

/// Coordinates the authentication flow using state-driven navigation.
///
/// Flow: Login ↔ Register (bidirectional)
///
/// When login/register succeeds, the Kotlin layer updates AuthState to .authenticated,
/// which automatically transitions the app to the main view.
/// **No onLoginSuccess callback needed.**
struct AuthFlowCoordinator: View {

    let openRegistration: Bool

    @State private var showingRegister = false

    var body: some View {
        NavigationStack {
            LoginView(openRegistration: openRegistration)
                .navigationDestination(isPresented: $showingRegister) {
                    RegisterView()
                }
                .environment(\.navigateToRegister) {
                    showingRegister = true
                }
                .environment(\.navigateBack) {
                    showingRegister = false
                }
        }
    }
}

// MARK: - Navigation Environment Keys

/// Environment key for navigating to register screen.
/// Cleaner than passing closures through init.
private struct NavigateToRegisterKey: EnvironmentKey {
    static let defaultValue: () -> Void = {}
}

private struct NavigateBackKey: EnvironmentKey {
    static let defaultValue: () -> Void = {}
}

extension EnvironmentValues {
    var navigateToRegister: () -> Void {
        get { self[NavigateToRegisterKey.self] }
        set { self[NavigateToRegisterKey.self] = newValue }
    }

    var navigateBack: () -> Void {
        get { self[NavigateBackKey.self] }
        set { self[NavigateBackKey.self] = newValue }
    }
}
