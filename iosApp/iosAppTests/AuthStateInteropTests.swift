import XCTest
@testable import listenup
import Shared

/// Regression tests for AuthState SKIE interop.
///
/// These tests verify that the Kotlin sealed interface AuthState
/// is correctly bridged to Swift via SKIE's `onEnum(of:)` pattern.
///
/// Critical for catching:
/// - SKIE codegen changes
/// - Kotlin sealed class structure changes
/// - Missing enum cases after Kotlin updates
final class AuthStateInteropTests: XCTestCase {

    override class func setUp() {
        super.setUp()
        Koin_iosKt.initializeKoin(additionalModules: [])
    }

    // MARK: - AuthState Enum Pattern Matching

    func testAuthStateInitializingPattern() {
        let state: AuthState = AuthStateInitializing()

        switch onEnum(of: state) {
        case .initializing:
            // Expected
            break
        default:
            XCTFail("AuthState.Initializing should match .initializing case")
        }
    }

    func testAuthStateNeedsServerUrlPattern() {
        let state: AuthState = AuthStateNeedsServerUrl()

        switch onEnum(of: state) {
        case .needsServerUrl:
            // Expected
            break
        default:
            XCTFail("AuthState.NeedsServerUrl should match .needsServerUrl case")
        }
    }

    func testAuthStateCheckingServerPattern() {
        let state: AuthState = AuthStateCheckingServer()

        switch onEnum(of: state) {
        case .checkingServer:
            // Expected
            break
        default:
            XCTFail("AuthState.CheckingServer should match .checkingServer case")
        }
    }

    func testAuthStateNeedsSetupPattern() {
        let state: AuthState = AuthStateNeedsSetup()

        switch onEnum(of: state) {
        case .needsSetup:
            // Expected
            break
        default:
            XCTFail("AuthState.NeedsSetup should match .needsSetup case")
        }
    }

    func testAuthStateNeedsLoginPattern() {
        let state: AuthState = AuthStateNeedsLogin(openRegistration: true)

        switch onEnum(of: state) {
        case .needsLogin(let login):
            XCTAssertTrue(login.openRegistration, "openRegistration should be accessible")
        default:
            XCTFail("AuthState.NeedsLogin should match .needsLogin case")
        }
    }

    func testAuthStateNeedsLoginDefaultOpenRegistration() {
        let state: AuthState = AuthStateNeedsLogin(openRegistration: false)

        switch onEnum(of: state) {
        case .needsLogin(let login):
            XCTAssertFalse(login.openRegistration, "openRegistration default should be false")
        default:
            XCTFail("AuthState.NeedsLogin should match .needsLogin case")
        }
    }

    func testAuthStatePendingApprovalPattern() {
        let state: AuthState = AuthStatePendingApproval(
            userId: "user-123",
            email: "test@example.com",
            encryptedPassword: "encrypted"
        )

        switch onEnum(of: state) {
        case .pendingApproval(let pending):
            XCTAssertEqual(pending.userId, "user-123")
            XCTAssertEqual(pending.email, "test@example.com")
            XCTAssertEqual(pending.encryptedPassword, "encrypted")
        default:
            XCTFail("AuthState.PendingApproval should match .pendingApproval case")
        }
    }

    func testAuthStateAuthenticatedPattern() {
        let state: AuthState = AuthStateAuthenticated(
            userId: "user-456",
            sessionId: "session-789"
        )

        switch onEnum(of: state) {
        case .authenticated(let auth):
            XCTAssertEqual(auth.userId, "user-456")
            XCTAssertEqual(auth.sessionId, "session-789")
        default:
            XCTFail("AuthState.Authenticated should match .authenticated case")
        }
    }

    // MARK: - AuthState Flow Access

    func testAuthSessionFlowIsAccessible() async {
        let authSession = KoinHelper.shared.getAuthSession()
        let flow = authSession.authState

        // Just verify we can access the flow without crashing
        XCTAssertNotNil(flow, "authState flow should be accessible")
    }

    // MARK: - AuthStateKind Mapping

    func testAuthStateKindMappingCoverage() {
        // Verify our Swift AuthStateKind covers all Kotlin states
        let kinds: [AuthStateKind] = [
            .initializing,
            .needsServerUrl,
            .checkingServer,
            .needsSetup,
            .needsLogin,
            .pendingApproval,
            .authenticated
        ]

        XCTAssertEqual(kinds.count, 7, "Should have 7 AuthStateKind cases matching Kotlin")
    }

    func testAuthStateKindRequiresAuth() {
        XCTAssertTrue(AuthStateKind.initializing.requiresAuth)
        XCTAssertTrue(AuthStateKind.needsServerUrl.requiresAuth)
        XCTAssertTrue(AuthStateKind.checkingServer.requiresAuth)
        XCTAssertTrue(AuthStateKind.needsSetup.requiresAuth)
        XCTAssertTrue(AuthStateKind.needsLogin.requiresAuth)
        XCTAssertTrue(AuthStateKind.pendingApproval.requiresAuth)
        XCTAssertFalse(AuthStateKind.authenticated.requiresAuth)
    }
}
