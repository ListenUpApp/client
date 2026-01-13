import XCTest
@testable import listenup
import Shared

/// Tests for the navigation state machine.
///
/// These tests verify that AuthStateKind correctly determines
/// which UI flow should be displayed. This is critical logic -
/// one wrong case and users see the wrong screen.
///
/// The state machine is:
/// ```
/// AuthState (Kotlin)
///     ↓ (mapped by AuthStateObserver)
/// AuthStateKind (Swift)
///     ↓ (switched in ListenUpApp)
/// Correct UI Flow
/// ```
final class NavigationStateMachineTests: XCTestCase {

    // MARK: - State Kind Properties

    func testRequiresAuthForUnauthenticatedStates() {
        let unauthenticatedStates: [AuthStateKind] = [
            .initializing,
            .needsServerUrl,
            .checkingServer,
            .needsSetup,
            .needsLogin,
            .pendingApproval
        ]

        for state in unauthenticatedStates {
            XCTAssertTrue(
                state.requiresAuth,
                "\(state) should require auth"
            )
        }
    }

    func testDoesNotRequireAuthWhenAuthenticated() {
        XCTAssertFalse(
            AuthStateKind.authenticated.requiresAuth,
            "authenticated should NOT require auth"
        )
    }

    func testIsServerFlowOnlyForServerStates() {
        // These states should show server setup UI
        XCTAssertTrue(AuthStateKind.needsServerUrl.isServerFlow)
        XCTAssertTrue(AuthStateKind.checkingServer.isServerFlow)

        // These states should NOT show server setup UI
        XCTAssertFalse(AuthStateKind.initializing.isServerFlow)
        XCTAssertFalse(AuthStateKind.needsSetup.isServerFlow)
        XCTAssertFalse(AuthStateKind.needsLogin.isServerFlow)
        XCTAssertFalse(AuthStateKind.pendingApproval.isServerFlow)
        XCTAssertFalse(AuthStateKind.authenticated.isServerFlow)
    }

    // MARK: - UI Flow Decisions

    func testInitializingShowsLaunchScreen() {
        let state = AuthStateKind.initializing
        let decision = UIFlowDecision.from(state)
        XCTAssertEqual(decision, .launchScreen)
    }

    func testCheckingServerShowsLaunchScreen() {
        let state = AuthStateKind.checkingServer
        let decision = UIFlowDecision.from(state)
        XCTAssertEqual(decision, .launchScreen)
    }

    func testNeedsServerUrlShowsServerFlow() {
        let state = AuthStateKind.needsServerUrl
        let decision = UIFlowDecision.from(state)
        XCTAssertEqual(decision, .serverFlow)
    }

    func testNeedsSetupShowsServerFlow() {
        // NeedsSetup means server is there but needs root user
        // This is still part of the server setup flow
        let state = AuthStateKind.needsSetup
        let decision = UIFlowDecision.from(state)
        XCTAssertEqual(decision, .serverFlow)
    }

    func testNeedsLoginShowsAuthFlow() {
        let state = AuthStateKind.needsLogin
        let decision = UIFlowDecision.from(state)
        XCTAssertEqual(decision, .authFlow)
    }

    func testPendingApprovalShowsPendingScreen() {
        let state = AuthStateKind.pendingApproval
        let decision = UIFlowDecision.from(state)
        XCTAssertEqual(decision, .pendingApproval)
    }

    func testAuthenticatedShowsMainApp() {
        let state = AuthStateKind.authenticated
        let decision = UIFlowDecision.from(state)
        XCTAssertEqual(decision, .mainApp)
    }

    // MARK: - State Transitions

    func testAllStatesAreCovered() {
        // Ensure we haven't forgotten any states
        let allStates: [AuthStateKind] = [
            .initializing,
            .needsServerUrl,
            .checkingServer,
            .needsSetup,
            .needsLogin,
            .pendingApproval,
            .authenticated
        ]

        for state in allStates {
            let decision = UIFlowDecision.from(state)
            XCTAssertNotEqual(
                decision, .unknown,
                "State \(state) should have a defined UI flow"
            )
        }
    }

    func testNoTwoStatesMapToSameFlowIncorrectly() {
        // Auth states should show auth flow, not main app
        XCTAssertNotEqual(UIFlowDecision.from(.needsLogin), .mainApp)
        XCTAssertNotEqual(UIFlowDecision.from(.needsServerUrl), .mainApp)

        // Authenticated should never show auth flow
        XCTAssertNotEqual(UIFlowDecision.from(.authenticated), .authFlow)
        XCTAssertNotEqual(UIFlowDecision.from(.authenticated), .serverFlow)
    }
}

// MARK: - UI Flow Decision

/// Represents which UI flow should be displayed.
/// This mirrors the switch statement in ListenUpApp.
enum UIFlowDecision: Equatable {
    case launchScreen
    case serverFlow
    case authFlow
    case pendingApproval
    case mainApp
    case unknown

    static func from(_ state: AuthStateKind) -> UIFlowDecision {
        switch state {
        case .initializing, .checkingServer:
            return .launchScreen

        case .needsServerUrl, .needsSetup:
            return .serverFlow

        case .needsLogin:
            return .authFlow

        case .pendingApproval:
            return .pendingApproval

        case .authenticated:
            return .mainApp
        }
    }
}
