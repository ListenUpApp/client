import XCTest
@testable import listenup
import Shared

/// Tests for AuthStateObserver - the critical bridge between Kotlin and SwiftUI.
///
/// These tests verify that:
/// - Observer initializes correctly
/// - State mapping is accurate
/// - Observation lifecycle is correct
@MainActor
final class AuthStateObserverTests: XCTestCase {

    override class func setUp() {
        super.setUp()
        Koin_iosKt.initializeKoin(additionalModules: [])
    }

    // MARK: - Initialization

    func testObserverInitializes() {
        let observer = AuthStateObserver()
        XCTAssertNotNil(observer, "Observer should initialize")
    }

    func testObserverHasInitialState() {
        let observer = AuthStateObserver()

        // Initial state should be one of the valid states
        // (typically .initializing, but depends on stored auth)
        let validStates: [AuthStateKind] = [
            .initializing,
            .needsServerUrl,
            .checkingServer,
            .needsSetup,
            .needsLogin,
            .pendingApproval,
            .authenticated
        ]

        XCTAssertTrue(
            validStates.contains(observer.state),
            "Initial state should be a valid AuthStateKind"
        )
    }

    func testObserverOpenRegistrationDefaultsFalse() {
        let observer = AuthStateObserver()
        // openRegistration should have a sensible default
        // (false until we know the server supports it)
        XCTAssertFalse(observer.openRegistration, "openRegistration should default to false")
    }

    // MARK: - State Mapping

    func testStateKindEquatable() {
        XCTAssertEqual(AuthStateKind.initializing, AuthStateKind.initializing)
        XCTAssertEqual(AuthStateKind.authenticated, AuthStateKind.authenticated)
        XCTAssertNotEqual(AuthStateKind.initializing, AuthStateKind.authenticated)
    }

    func testStateKindIsServerFlow() {
        XCTAssertTrue(AuthStateKind.needsServerUrl.isServerFlow)
        XCTAssertTrue(AuthStateKind.checkingServer.isServerFlow)
        XCTAssertFalse(AuthStateKind.needsLogin.isServerFlow)
        XCTAssertFalse(AuthStateKind.authenticated.isServerFlow)
    }

    // MARK: - Lifecycle

    func testStopObservingDoesNotCrash() {
        let observer = AuthStateObserver()
        observer.stopObserving()
        // Should not crash
    }

    func testStopObservingMultipleTimesDoesNotCrash() {
        let observer = AuthStateObserver()
        observer.stopObserving()
        observer.stopObserving()
        observer.stopObserving()
        // Should not crash
    }
}
