import Foundation
import Shared

/// Memory-safe Swift wrapper for Kotlin's ServerConnectViewModel.
///
/// This wrapper bridges Kotlin's StateFlow to SwiftUI's @Observable pattern
/// using SKIE's native flow collection for reactive updates.
///
/// **Key Features:**
/// - Uses SKIE's `for await` for efficient flow observation
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
        // Use SKIE's native flow collection
        observationTask = Task { [weak self] in
            guard let self = self else { return }

            for await state in self.kotlinVM.state {
                guard !Task.isCancelled else { break }

                await MainActor.run { [weak self] in
                    guard let self = self else { return }

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
