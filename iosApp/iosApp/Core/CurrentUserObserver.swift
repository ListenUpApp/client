import SwiftUI
import Shared

/// Observes the current user from Kotlin's UserRepository.
///
/// Provides reactive access to the logged-in user's profile data.
/// Use this to display user avatar, name, and other profile info.
@Observable
@MainActor
final class CurrentUserObserver {

    // MARK: - State

    private(set) var user: User?

    // MARK: - Private

    private let userRepository: UserRepository
    private var observationTask: Task<Void, Never>?

    // MARK: - Initialization

    init(userRepository: UserRepository = KoinHelper.shared.getUserRepository()) {
        self.userRepository = userRepository
        startObserving()
    }

    func stopObserving() {
        observationTask?.cancel()
        observationTask = nil
    }

    // MARK: - Observation

    private func startObserving() {
        observationTask = Task { [weak self] in
            guard let self else { return }

            for await user in self.userRepository.observeCurrentUser() {
                guard !Task.isCancelled else { break }
                self.user = user
            }
        }
    }
}
