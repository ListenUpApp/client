import XCTest
@testable import listenup
import Shared

/// Regression tests for Koin dependency injection interop.
///
/// These tests verify that Koin can resolve all dependencies used by iOS.
/// If a Kotlin or SKIE update breaks the bridge, these tests will catch it.
///
/// Run these after any:
/// - Kotlin version update
/// - SKIE version update
/// - Koin module changes
/// - New dependency additions
final class KoinInteropTests: XCTestCase {

    override class func setUp() {
        super.setUp()
        // Initialize Koin once for all tests
        Koin_iosKt.initializeKoin(additionalModules: [])
    }

    // MARK: - KoinHelper Resolution

    func testKoinHelperExists() {
        // Verify KoinHelper singleton is accessible
        let helper = KoinHelper.shared
        XCTAssertNotNil(helper, "KoinHelper.shared should exist")
    }

    func testResolveAuthSession() {
        let authSession = KoinHelper.shared.getAuthSession()
        XCTAssertNotNil(authSession, "Should resolve AuthSession")
        XCTAssertNotNil(authSession.authState, "AuthSession should have authState flow")
    }

    func testResolveServerConfig() {
        let serverConfig = KoinHelper.shared.getServerConfig()
        XCTAssertNotNil(serverConfig, "Should resolve ServerConfig")
    }

    func testResolveLoginViewModel() {
        let viewModel = KoinHelper.shared.getLoginViewModel()
        XCTAssertNotNil(viewModel, "Should resolve LoginViewModel")
        XCTAssertNotNil(viewModel.state, "LoginViewModel should have state flow")
    }

    func testResolveRegisterViewModel() {
        let viewModel = KoinHelper.shared.getRegisterViewModel()
        XCTAssertNotNil(viewModel, "Should resolve RegisterViewModel")
    }

    func testResolveServerConnectViewModel() {
        let viewModel = KoinHelper.shared.getServerConnectViewModel()
        XCTAssertNotNil(viewModel, "Should resolve ServerConnectViewModel")
    }

    func testResolveServerSelectViewModel() {
        let viewModel = KoinHelper.shared.getServerSelectViewModel()
        XCTAssertNotNil(viewModel, "Should resolve ServerSelectViewModel")
    }

    func testResolveGetInstanceUseCase() {
        let useCase = KoinHelper.shared.getInstanceUseCase()
        XCTAssertNotNil(useCase, "Should resolve GetInstanceUseCase")
    }

    // MARK: - Dependencies Container

    func testDependenciesSharedExists() {
        let deps = Dependencies.shared
        XCTAssertNotNil(deps, "Dependencies.shared should exist")
    }

    func testDependenciesResolvesViewModels() {
        let deps = Dependencies.shared

        XCTAssertNotNil(deps.loginViewModel, "Should resolve loginViewModel")
        XCTAssertNotNil(deps.registerViewModel, "Should resolve registerViewModel")
        XCTAssertNotNil(deps.serverConnectViewModel, "Should resolve serverConnectViewModel")
        XCTAssertNotNil(deps.serverSelectViewModel, "Should resolve serverSelectViewModel")
    }

    func testDependenciesResolvesSettings() {
        let deps = Dependencies.shared

        XCTAssertNotNil(deps.authSession, "Should resolve authSession")
        XCTAssertNotNil(deps.serverConfig, "Should resolve serverConfig")
    }
}
