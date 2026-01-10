import SwiftUI
import Shared

/// Observes LibraryViewModel's uiState StateFlow for SwiftUI consumption.
///
/// Maps Kotlin's LibraryUiState to Swift-native properties that drive
/// the library grid UI including loading, empty, and error states.
@Observable
@MainActor
final class LibraryObserver {

    // MARK: - Observed State

    /// Current UI state from the ViewModel
    private(set) var uiState: LibraryUiState?

    /// Convenience: list of books from uiState
    var books: [Book] { uiState?.books as? [Book] ?? [] }

    /// Convenience: progress map (bookId -> 0.0-1.0)
    var bookProgress: [String: Float] {
        guard let progress = uiState?.bookProgress as? [String: KotlinFloat] else { return [:] }
        return progress.mapValues { $0.floatValue }
    }

    /// Whether the library is loading (initial load not complete)
    var isLoading: Bool { uiState?.isLoading ?? true }

    /// Whether the library has loaded but is empty
    var isEmpty: Bool { uiState?.isEmpty ?? false }

    /// Whether a sync is in progress
    var isSyncing: Bool { uiState?.isSyncing ?? false }

    /// Error message if sync failed, nil otherwise.
    /// Returns a generic message for now - SKIE sealed class handling to be refined.
    var errorMessage: String? {
        guard let syncState = uiState?.syncState else { return nil }
        // Check if sync is in an error state using onEnum
        switch onEnum(of: syncState) {
        case .error(let error):
            return error.message
        default:
            return nil
        }
    }

    // MARK: - Private

    private let viewModel: LibraryViewModel
    private var observationTask: Task<Void, Never>?

    // MARK: - Initialization

    init(viewModel: LibraryViewModel) {
        self.viewModel = viewModel
        startObserving()
    }

    /// Call this to stop observation when done.
    /// (deinit can't access MainActor properties directly)
    func stopObserving() {
        observationTask?.cancel()
        observationTask = nil
    }

    // MARK: - Lifecycle

    /// Call when the library screen appears
    func onScreenVisible() {
        viewModel.onScreenVisible()
    }

    /// Trigger a manual refresh
    func refresh() {
        viewModel.onEvent(event: LibraryUiEventRefreshRequested.shared)
    }

    // MARK: - Private

    private func startObserving() {
        observationTask = Task { [weak self] in
            guard let self else { return }

            for await state in self.viewModel.uiState {
                guard !Task.isCancelled else { break }
                self.uiState = state
            }
        }
    }
}
