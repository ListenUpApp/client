import SwiftUI
import Shared

/// Observes LibraryViewModel's uiState StateFlow for SwiftUI consumption.
///
/// Maps Kotlin's LibraryUiState to Swift-native properties that drive
/// the library UI including loading, empty, and error states for all tabs.
@Observable
@MainActor
final class LibraryObserver {

    // MARK: - Observed State

    /// Current UI state from the ViewModel
    private(set) var uiState: LibraryUiState?

    // MARK: - Books

    /// List of books from uiState
    var books: [Book] { uiState?.books as? [Book] ?? [] }

    /// Progress map (bookId -> 0.0-1.0)
    var bookProgress: [String: Float] {
        guard let progress = uiState?.bookProgress as? [String: KotlinFloat] else { return [:] }
        return progress.mapValues { $0.floatValue }
    }

    /// Sort state for books tab
    var booksSortState: SortState? { uiState?.booksSortState }

    // MARK: - Series

    /// List of series with their books
    var series: [SeriesWithBooks] { uiState?.series as? [SeriesWithBooks] ?? [] }

    /// Sort state for series tab
    var seriesSortState: SortState? { uiState?.seriesSortState }

    // MARK: - Authors

    /// List of authors with book counts
    var authors: [ContributorWithBookCount] { uiState?.authors as? [ContributorWithBookCount] ?? [] }

    /// Sort state for authors tab
    var authorsSortState: SortState? { uiState?.authorsSortState }

    // MARK: - Narrators

    /// List of narrators with book counts
    var narrators: [ContributorWithBookCount] { uiState?.narrators as? [ContributorWithBookCount] ?? [] }

    /// Sort state for narrators tab
    var narratorsSortState: SortState? { uiState?.narratorsSortState }

    // MARK: - Loading States

    /// Whether the library is loading (initial load not complete)
    var isLoading: Bool { uiState?.isLoading ?? true }

    /// Whether the library has loaded but is empty
    var isEmpty: Bool { uiState?.isEmpty ?? false }

    /// Whether a sync is in progress
    var isSyncing: Bool { uiState?.isSyncing ?? false }

    /// Error message if sync failed, nil otherwise.
    var errorMessage: String? {
        guard let syncState = uiState?.syncState else { return nil }
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

    // MARK: - Books Sort Events

    /// Change books sort category
    func setBooksSortCategory(_ category: SortCategory) {
        viewModel.onEvent(event: LibraryUiEventBooksCategoryChanged(category: category))
    }

    /// Toggle books sort direction
    func toggleBooksSortDirection() {
        viewModel.onEvent(event: LibraryUiEventBooksDirectionToggled.shared)
    }

    // MARK: - Series Sort Events

    /// Change series sort category
    func setSeriesSortCategory(_ category: SortCategory) {
        viewModel.onEvent(event: LibraryUiEventSeriesCategoryChanged(category: category))
    }

    /// Toggle series sort direction
    func toggleSeriesSortDirection() {
        viewModel.onEvent(event: LibraryUiEventSeriesDirectionToggled.shared)
    }

    // MARK: - Authors Sort Events

    /// Change authors sort category
    func setAuthorsSortCategory(_ category: SortCategory) {
        viewModel.onEvent(event: LibraryUiEventAuthorsCategoryChanged(category: category))
    }

    /// Toggle authors sort direction
    func toggleAuthorsSortDirection() {
        viewModel.onEvent(event: LibraryUiEventAuthorsDirectionToggled.shared)
    }

    // MARK: - Narrators Sort Events

    /// Change narrators sort category
    func setNarratorsSortCategory(_ category: SortCategory) {
        viewModel.onEvent(event: LibraryUiEventNarratorsCategoryChanged(category: category))
    }

    /// Toggle narrators sort direction
    func toggleNarratorsSortDirection() {
        viewModel.onEvent(event: LibraryUiEventNarratorsDirectionToggled.shared)
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
