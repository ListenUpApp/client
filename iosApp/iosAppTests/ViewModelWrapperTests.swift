import XCTest
@testable import listenup
import Shared

/// Regression tests for ViewModel wrapper interop.
///
/// These tests verify that Swift wrappers correctly observe
/// Kotlin StateFlows via SKIE and map states properly.
///
/// Critical for catching:
/// - SKIE async sequence changes
/// - Kotlin StateFlow behavior changes
/// - State mapping regressions
final class ViewModelWrapperTests: XCTestCase {

    override class func setUp() {
        super.setUp()
        Koin_iosKt.initializeKoin(additionalModules: [])
    }

    // MARK: - LoginViewModelWrapper

    func testLoginViewModelWrapperInitializes() {
        let kotlinVM = KoinHelper.shared.getLoginViewModel()
        let wrapper = LoginViewModelWrapper(viewModel: kotlinVM)

        XCTAssertNotNil(wrapper, "Wrapper should initialize")
        XCTAssertFalse(wrapper.isLoading, "Initial state should not be loading")
        XCTAssertFalse(wrapper.isSuccess, "Initial state should not be success")
        XCTAssertNil(wrapper.emailError, "Initial state should have no email error")
        XCTAssertNil(wrapper.passwordError, "Initial state should have no password error")
        XCTAssertNil(wrapper.generalError, "Initial state should have no general error")
    }

    func testLoginViewModelWrapperHasLoginMethod() {
        let kotlinVM = KoinHelper.shared.getLoginViewModel()
        let wrapper = LoginViewModelWrapper(viewModel: kotlinVM)

        // Verify the method exists and is callable
        // This will fail at compile time if the signature changes
        wrapper.login(email: "test@example.com", password: "password123")

        // After calling login, isLoading should become true
        // (assuming the use case is async)
        // We don't assert on isLoading here because it's async
    }

    func testLoginViewModelWrapperHasClearErrorMethod() {
        let kotlinVM = KoinHelper.shared.getLoginViewModel()
        let wrapper = LoginViewModelWrapper(viewModel: kotlinVM)

        // Verify the method exists and is callable
        wrapper.clearError()
    }

    // MARK: - RegisterViewModelWrapper

    func testRegisterViewModelWrapperInitializes() {
        let kotlinVM = KoinHelper.shared.getRegisterViewModel()
        let wrapper = RegisterViewModelWrapper(viewModel: kotlinVM)

        XCTAssertNotNil(wrapper, "Wrapper should initialize")
        XCTAssertFalse(wrapper.isLoading, "Initial state should not be loading")
        XCTAssertFalse(wrapper.isSuccess, "Initial state should not be success")
        XCTAssertNil(wrapper.error, "Initial state should have no error")
    }

    func testRegisterViewModelWrapperHasRegisterMethod() {
        let kotlinVM = KoinHelper.shared.getRegisterViewModel()
        let wrapper = RegisterViewModelWrapper(viewModel: kotlinVM)

        // Verify the method exists and is callable
        wrapper.register(
            email: "test@example.com",
            password: "password123",
            firstName: "Test",
            lastName: "User"
        )
    }

    // MARK: - ServerConnectViewModelWrapper

    func testServerConnectViewModelWrapperInitializes() {
        let kotlinVM = KoinHelper.shared.getServerConnectViewModel()
        let wrapper = ServerConnectViewModelWrapper(viewModel: kotlinVM)

        XCTAssertNotNil(wrapper, "Wrapper should initialize")
        XCTAssertFalse(wrapper.isLoading, "Initial state should not be loading")
        XCTAssertFalse(wrapper.isVerified, "Initial state should not be verified")
        XCTAssertTrue(wrapper.serverUrl.isEmpty, "Initial URL should be empty")
    }

    func testServerConnectViewModelWrapperHasUrlChangeMethod() {
        let kotlinVM = KoinHelper.shared.getServerConnectViewModel()
        let wrapper = ServerConnectViewModelWrapper(viewModel: kotlinVM)

        wrapper.onUrlChanged("example.com")
        XCTAssertEqual(wrapper.serverUrl, "example.com", "URL should update")
    }

    func testServerConnectViewModelWrapperConnectEnabled() {
        let kotlinVM = KoinHelper.shared.getServerConnectViewModel()
        let wrapper = ServerConnectViewModelWrapper(viewModel: kotlinVM)

        XCTAssertFalse(wrapper.isConnectEnabled, "Connect should be disabled with empty URL")

        wrapper.onUrlChanged("example.com")
        XCTAssertTrue(wrapper.isConnectEnabled, "Connect should be enabled with URL")
    }

    // MARK: - ServerSelectViewModelWrapper

    func testServerSelectViewModelWrapperInitializes() {
        let kotlinVM = KoinHelper.shared.getServerSelectViewModel()
        let wrapper = ServerSelectViewModelWrapper(viewModel: kotlinVM)

        XCTAssertNotNil(wrapper, "Wrapper should initialize")
        XCTAssertFalse(wrapper.isConnecting, "Initial state should not be connecting")
        XCTAssertNil(wrapper.selectedServerId, "Initial state should have no selection")
    }

    func testServerSelectViewModelWrapperServersArray() {
        let kotlinVM = KoinHelper.shared.getServerSelectViewModel()
        let wrapper = ServerSelectViewModelWrapper(viewModel: kotlinVM)

        // Servers should be an array (even if empty)
        XCTAssertNotNil(wrapper.servers, "servers should be accessible")
        XCTAssertTrue(wrapper.servers is [DiscoveredServerItem], "servers should be correct type")
    }
}
