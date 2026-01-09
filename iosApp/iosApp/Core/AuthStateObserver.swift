import SwiftUI
import Shared

/// Observes Kotlin's AuthState flow and exposes it as SwiftUI-native state.
///
/// This is the **single source of truth** for authentication state in the iOS app.
/// Place this at the root of your app and let state drive navigation.
///
/// Usage:
/// ```swift
/// @State private var auth = AuthStateObserver()
///
/// var body: some View {
///     switch auth.state {
///     case .authenticated:
///         MainTabView()
///     default:
///         AuthFlowView()
///     }
/// }
/// ```
@Observable
@MainActor
final class AuthStateObserver {

    // MARK: - State

    private(set) var state: AuthStateKind = .initializing
    private(set) var openRegistration: Bool = false

    // MARK: - Private

    private let authSession: AuthSession
    private var observationTask: Task<Void, Never>?

    // MARK: - Initialization

    init(authSession: AuthSession = KoinHelper.shared.getAuthSession()) {
        self.authSession = authSession
        startObserving()
    }

    /// Call this to stop observation when done.
    /// (deinit can't access MainActor properties directly)
    func stopObserving() {
        observationTask?.cancel()
        observationTask = nil
    }

    // MARK: - Observation

    private func startObserving() {
        observationTask = Task { [weak self] in
            guard let self else { return }

            for await authState in self.authSession.authState {
                guard !Task.isCancelled else { break }
                self.mapState(authState)
            }
        }
    }

    private func mapState(_ authState: AuthState) {
        switch onEnum(of: authState) {
        case .initializing:
            state = .initializing

        case .needsServerUrl:
            state = .needsServerUrl

        case .checkingServer:
            state = .checkingServer

        case .needsSetup:
            state = .needsSetup

        case .needsLogin(let login):
            state = .needsLogin
            openRegistration = login.openRegistration

        case .pendingApproval:
            state = .pendingApproval

        case .authenticated:
            state = .authenticated
        }
    }
}

// MARK: - State Kind

/// Simplified auth state for SwiftUI pattern matching.
///
/// We flatten the Kotlin sealed class to a simple enum because:
/// 1. SwiftUI `switch` works better with simple enums
/// 2. Associated data lives in AuthStateObserver properties
/// 3. Animations work correctly with Equatable conformance
enum AuthStateKind: Equatable {
    case initializing
    case needsServerUrl
    case checkingServer
    case needsSetup
    case needsLogin
    case pendingApproval
    case authenticated

    /// States that show the auth flow UI
    var requiresAuth: Bool {
        switch self {
        case .authenticated:
            return false
        default:
            return true
        }
    }

    /// States that show the server setup flow
    var isServerFlow: Bool {
        switch self {
        case .needsServerUrl, .checkingServer:
            return true
        default:
            return false
        }
    }
}
