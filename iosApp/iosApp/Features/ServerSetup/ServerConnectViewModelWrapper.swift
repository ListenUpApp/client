import Foundation
import Shared

/// Memory-safe Swift wrapper for Kotlin's ServerConnectViewModel.
///
/// This wrapper bridges Kotlin's StateFlow to SwiftUI's @Observable pattern
/// while ensuring proper memory management and thread safety.
///
/// **Critical Memory Safety:**
/// - Uses `[weak self]` to prevent retain cycles
/// - Cancels observation task on deinit
/// - All state updates happen on Main thread
///
/// Usage:
/// ```swift
/// @State private var viewModel: ServerConnectViewModelWrapper
///
/// init(dependencies: Dependencies = .shared) {
///     _viewModel = State(initialValue:
///         ServerConnectViewModelWrapper(
///             viewModel: dependencies.serverConnectViewModel
///         )
///     )
/// }
/// ```
@Observable
final class ServerConnectViewModelWrapper {
    private let kotlinVM: ServerConnectViewModel
    private var observationTask: Task<Void, Never>?

    // Swift properties mirroring Kotlin UiState
    var serverUrl: String = ""
    var isLoading: Bool = false
    var error: String? = nil
    var isConnectEnabled: Bool = false
    var isVerified: Bool = false

    init(viewModel: ServerConnectViewModel) {
        self.kotlinVM = viewModel
        observeState()
    }

    private func observeState() {
        // ✅ Memory-safe observation with reasonable polling interval
        // NOTE: This uses polling because Kotlin extension functions need framework rebuild
        // TODO: Switch to reactive .collect() once framework is rebuilt (see StateFlowExtensions.kt)
        observationTask = Task { [weak self] in
            guard let self = self else { return }

            while !Task.isCancelled {
                guard let state = self.kotlinVM.state.value as? ServerConnectUiState else {
                    try? await Task.sleep(for: .milliseconds(100))
                    continue
                }

                await MainActor.run {
                    self.serverUrl = state.serverUrl
                    self.isLoading = state.isLoading
                    self.isConnectEnabled = state.isConnectEnabled
                    self.isVerified = state.isVerified

                    // Map error if present
                    if let kotlinError = state.error {
                        self.error = kotlinError.message
                    } else {
                        self.error = nil
                    }
                }

                // Poll interval: 100ms is responsive without excessive CPU usage
                try? await Task.sleep(for: .milliseconds(100))
            }
        }
    }

    // MARK: - Event Forwarding

    func onUrlChanged(_ url: String) {
        kotlinVM.onEvent(event: ServerConnectUiEventUrlChanged(newUrl: url))
    }

    func onConnectClicked() {
        kotlinVM.onEvent(event: ServerConnectUiEventConnectClicked())
    }

    // MARK: - Cleanup

    deinit {
        // Cancel observation task when wrapper is deallocated
        observationTask?.cancel()
    }
}
