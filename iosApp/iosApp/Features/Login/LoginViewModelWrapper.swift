import Foundation
import Shared

/// Memory-safe Swift wrapper for Kotlin's LoginViewModel.
///
/// This wrapper bridges Kotlin's StateFlow to SwiftUI's @Observable pattern
/// using SKIE's native flow collection for reactive updates.
///
/// **Key Features:**
/// - Uses SKIE's `collect()` for efficient flow observation
/// - Uses `[weak self]` to prevent retain cycles
/// - Cancels observation task on deinit
/// - All state updates happen on Main thread
///
/// Usage:
/// ```swift
/// @State private var viewModel: LoginViewModelWrapper
///
/// init(dependencies: Dependencies = .shared) {
///     _viewModel = State(initialValue:
///         LoginViewModelWrapper(
///             viewModel: dependencies.loginViewModel
///         )
///     )
/// }
/// ```
@Observable
final class LoginViewModelWrapper {
    private let kotlinVM: LoginViewModel
    private var observationTask: Task<Void, Never>?

    // Swift properties mirroring Kotlin UiState
    var isLoading: Bool = false
    var isSuccess: Bool = false
    var emailError: String? = nil
    var passwordError: String? = nil
    var generalError: String? = nil

    init(viewModel: LoginViewModel) {
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

                    // Map status to Swift properties
                    switch onEnum(of: state.status) {
                    case .idle:
                        self.isLoading = false
                        self.isSuccess = false
                        self.clearErrors()

                    case .loading:
                        self.isLoading = true
                        self.isSuccess = false
                        self.clearErrors()

                    case .success:
                        self.isLoading = false
                        self.isSuccess = true
                        self.clearErrors()

                    case .error(let error):
                        self.isLoading = false
                        self.isSuccess = false
                        self.mapError(error.type)
                    }
                }
            }
        }
    }

    private func clearErrors() {
        emailError = nil
        passwordError = nil
        generalError = nil
    }

    private func mapError(_ errorType: LoginErrorType) {
        clearErrors()

        switch onEnum(of: errorType) {
        case .invalidCredentials:
            generalError = NSLocalizedString("auth.invalid_credentials", comment: "")

        case .networkError(let error):
            generalError = error.detail ?? NSLocalizedString("auth.unable_to_connect", comment: "")

        case .serverError(let error):
            generalError = error.detail ?? NSLocalizedString("auth.server_error", comment: "")

        case .validationError(let error):
            switch error.field {
            case .email:
                emailError = NSLocalizedString("auth.invalid_email", comment: "")
            case .password:
                passwordError = NSLocalizedString("auth.enter_password", comment: "")
            }
        }
    }

    // MARK: - Actions

    func login(email: String, password: String) {
        kotlinVM.onLoginSubmit(email: email, password: password)
    }

    func clearError() {
        kotlinVM.clearError()
    }

    // MARK: - Cleanup

    deinit {
        observationTask?.cancel()
    }
}
