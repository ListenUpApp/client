import SwiftUI
import Shared

/// Observes ContributorDetailViewModel's state StateFlow for SwiftUI consumption.
///
/// Maps Kotlin's ContributorDetailUiState to Swift-native properties that drive
/// the contributor detail UI including loading, error, and content states.
@Observable
@MainActor
final class ContributorDetailObserver {

    // MARK: - Observed State

    /// Current UI state from the ViewModel
    private(set) var uiState: ContributorDetailUiState?

    // MARK: - Computed Properties

    /// Whether the screen is loading
    var isLoading: Bool { uiState?.isLoading ?? true }

    /// Error message if any
    var error: String? { uiState?.error }

    /// The contributor being displayed
    var contributor: Contributor? { uiState?.contributor }

    /// Contributor name
    var name: String { contributor?.name ?? "" }

    /// Contributor description/biography
    var bio: String? { contributor?.description_ }

    /// Contributor image path
    var imagePath: String? { contributor?.imagePath }

    /// Contributor image blur hash
    var imageBlurHash: String? { contributor?.imageBlurHash }

    /// Contributor aliases (a.k.a. names)
    var aliases: [String] {
        guard let list = contributor?.aliases else { return [] }
        return Array(list)
    }

    /// Birth date (ISO 8601 format)
    var birthDate: String? { contributor?.birthDate }

    /// Death date (ISO 8601 format)
    var deathDate: String? { contributor?.deathDate }

    /// Website URL
    var website: String? { contributor?.website }

    /// Role sections (Author, Narrator, etc.)
    var roleSections: [RoleSection] {
        guard let list = uiState?.roleSections else { return [] }
        return Array(list)
    }

    /// Progress map for books (bookId -> progress 0.0-1.0)
    var bookProgress: [String: Float] {
        guard let progress = uiState?.bookProgress as? [String: KotlinFloat] else { return [:] }
        return progress.mapValues { $0.floatValue }
    }

    /// Total book count across all roles
    var totalBookCount: Int {
        roleSections.reduce(0) { $0 + Int($1.bookCount) }
    }

    /// Whether delete confirmation dialog should show
    var showDeleteConfirmation: Bool { uiState?.showDeleteConfirmation ?? false }

    /// Whether deletion is in progress
    var isDeleting: Bool { uiState?.isDeleting ?? false }

    // MARK: - Private

    private let viewModel: ContributorDetailViewModel
    private var observationTask: Task<Void, Never>?

    // MARK: - Initialization

    init(viewModel: ContributorDetailViewModel) {
        self.viewModel = viewModel
        startObserving()
    }

    /// Call this to stop observation when done.
    func stopObserving() {
        observationTask?.cancel()
        observationTask = nil
    }

    // MARK: - Actions

    /// Load a contributor by ID
    func loadContributor(contributorId: String) {
        viewModel.loadContributor(contributorId: contributorId)
    }

    /// Request deletion (shows confirmation)
    func onDeleteContributor() {
        viewModel.onDeleteContributor()
    }

    /// Confirm deletion
    func onConfirmDelete(onDeleted: @escaping () -> Void) {
        viewModel.onConfirmDelete(onDeleted: onDeleted)
    }

    /// Dismiss delete confirmation
    func onDismissDelete() {
        viewModel.onDismissDelete()
    }

    /// Clear error
    func onClearError() {
        viewModel.onClearError()
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
