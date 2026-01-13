import SwiftUI
import Shared

/// Observes SeriesDetailViewModel's state StateFlow for SwiftUI consumption.
///
/// Maps Kotlin's SeriesDetailUiState to Swift-native properties that drive
/// the series detail UI including loading, error, and content states.
@Observable
@MainActor
final class SeriesDetailObserver {

    // MARK: - Observed State

    /// Current UI state from the ViewModel
    private(set) var uiState: SeriesDetailUiState?

    // MARK: - Computed Properties

    /// Whether the screen is loading
    var isLoading: Bool { uiState?.isLoading ?? true }

    /// Error message if any
    var error: String? { uiState?.error }

    /// Series name
    var seriesName: String { uiState?.seriesName ?? "" }

    /// Series description
    var seriesDescription: String? { uiState?.seriesDescription }

    /// Cover image path
    var coverPath: String? { uiState?.coverPath }

    /// Total duration formatted (e.g., "87h 5m")
    var totalDuration: String { uiState?.formatTotalDuration() ?? "" }

    /// Books in the series (sorted by sequence)
    var books: [Book] {
        guard let list = uiState?.books else { return [] }
        return Array(list)
    }

    /// Number of books in series
    var bookCount: Int { books.count }

    // MARK: - Private

    private let viewModel: SeriesDetailViewModel
    private var observationTask: Task<Void, Never>?

    // MARK: - Initialization

    init(viewModel: SeriesDetailViewModel) {
        self.viewModel = viewModel
        startObserving()
    }

    /// Call this to stop observation when done.
    func stopObserving() {
        observationTask?.cancel()
        observationTask = nil
    }

    // MARK: - Actions

    /// Load a series by ID
    func loadSeries(seriesId: String) {
        viewModel.loadSeries(seriesId: seriesId)
    }

    // MARK: - Private

    private func startObserving() {
        observationTask = Task { [weak self] in
            guard let self else { return }

            for await state in self.viewModel.state {
                guard !Task.isCancelled else { break }
                self.uiState = state
            }
        }
    }
}
