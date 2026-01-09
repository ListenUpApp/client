import Foundation
import Shared

/// Memory-safe Swift wrapper for Kotlin's RegisterViewModel.
///
/// This wrapper bridges Kotlin's StateFlow to SwiftUI's @Observable pattern
/// using SKIE's native flow collection for reactive updates.
@Observable
final class RegisterViewModelWrapper {
    private let kotlinVM: RegisterViewModel
    private var observationTask: Task<Void, Never>?

    // Swift properties mirroring Kotlin UiState
    var isLoading: Bool = false
    var isSuccess: Bool = false
    var error: String? = nil

    init(viewModel: RegisterViewModel) {
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

                    switch onEnum(of: state.status) {
                    case .idle:
                        self.isLoading = false
                        self.isSuccess = false
                        self.error = nil

                    case .loading:
                        self.isLoading = true
                        self.isSuccess = false
                        self.error = nil

                    case .success:
                        self.isLoading = false
                        self.isSuccess = true
                        self.error = nil

                    case .error(let errorState):
                        self.isLoading = false
                        self.isSuccess = false
                        self.error = errorState.message
                    }
                }
            }
        }
    }

    // MARK: - Actions

    func register(email: String, password: String, firstName: String, lastName: String) {
        kotlinVM.onRegisterSubmit(
            email: email,
            password: password,
            firstName: firstName,
            lastName: lastName
        )
    }

    func clearError() {
        kotlinVM.clearError()
    }

    // MARK: - Cleanup

    deinit {
        observationTask?.cancel()
    }
}
