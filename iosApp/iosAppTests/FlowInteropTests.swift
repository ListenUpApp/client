import XCTest
@testable import listenup
import Shared

/// Regression tests for SKIE StateFlow/Flow interop.
///
/// These tests verify that Kotlin Flows are correctly exposed
/// as Swift AsyncSequences via SKIE.
///
/// Critical for catching:
/// - SKIE async sequence generation changes
/// - Flow collection behavior changes
/// - Cancellation handling changes
final class FlowInteropTests: XCTestCase {

    override class func setUp() {
        super.setUp()
        Koin_iosKt.initializeKoin(additionalModules: [])
    }

    // MARK: - StateFlow Iteration

    func testAuthStateFlowIsAsyncSequence() async {
        let authSession = KoinHelper.shared.getAuthSession()
        let flow = authSession.authState

        // Verify flow conforms to AsyncSequence (SKIE generates this)
        // This will fail at compile time if SKIE changes the generation
        var iterator = flow.makeAsyncIterator()

        // Get first value (should be immediate for StateFlow)
        let firstValue = await iterator.next()
        XCTAssertNotNil(firstValue, "StateFlow should emit initial value")
    }

    func testLoginStateFlowIsAsyncSequence() async {
        let viewModel = KoinHelper.shared.getLoginViewModel()
        let flow = viewModel.state

        var iterator = flow.makeAsyncIterator()
        let firstValue = await iterator.next()

        XCTAssertNotNil(firstValue, "StateFlow should emit initial value")
    }

    // MARK: - StateFlow Current Value

    func testLoginStateFlowHasValue() {
        let viewModel = KoinHelper.shared.getLoginViewModel()
        let flow = viewModel.state

        // StateFlow should have synchronous access to current value
        // SKIE exposes this via the `value` property
        let currentValue = flow.value
        XCTAssertNotNil(currentValue, "StateFlow should have current value")
    }

    // MARK: - Flow For-Await Pattern

    func testAuthStateFlowForAwaitPattern() async {
        let authSession = KoinHelper.shared.getAuthSession()

        // Verify the for-await pattern works (SKIE's primary API)
        var receivedValue = false

        // Use Task with timeout to avoid hanging
        let task = Task {
            for await state in authSession.authState {
                receivedValue = true
                // Just check first value and break
                _ = state
                break
            }
        }

        // Wait briefly for value
        try? await Task.sleep(nanoseconds: 100_000_000) // 100ms
        task.cancel()

        XCTAssertTrue(receivedValue, "Should receive at least one value from StateFlow")
    }

    // MARK: - Task Cancellation

    func testFlowObservationIsCancellable() async {
        let authSession = KoinHelper.shared.getAuthSession()

        let task = Task {
            for await _ in authSession.authState {
                // Infinite loop if not cancelled
            }
        }

        // Cancel immediately
        task.cancel()

        // Verify task respects cancellation
        let result = await task.result
        switch result {
        case .success:
            // Task completed (due to cancellation check)
            break
        case .failure:
            // CancellationError is also acceptable
            break
        }
        // If we get here without hanging, cancellation works
    }
}
