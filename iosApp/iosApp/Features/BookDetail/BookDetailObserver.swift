import SwiftUI
import Shared

/// Observes BookDetailViewModel's state StateFlow for SwiftUI consumption.
///
/// Maps Kotlin's BookDetailUiState to Swift-native properties that drive
/// the book detail UI including loading, error, and content states.
@Observable
@MainActor
final class BookDetailObserver {

    // MARK: - Observed State

    /// Current UI state from the ViewModel
    private(set) var uiState: BookDetailUiState?

    // MARK: - Computed Properties

    /// Whether the screen is loading
    var isLoading: Bool { uiState?.isLoading ?? true }

    /// Error message if any
    var error: String? { uiState?.error }

    /// The book being displayed
    var book: Book? { uiState?.book }

    /// Book title
    var title: String { book?.title ?? "" }

    /// Book subtitle (filtered for redundancy)
    var subtitle: String? { uiState?.subtitle }

    /// Series info (e.g., "The Stormlight Archive #1")
    var series: String? { uiState?.series }

    /// Book description/synopsis
    var bookDescription: String { uiState?.description_ ?? "" }

    /// Narrator names as string
    var narrators: String { uiState?.narrators ?? "" }

    /// Author names from the book
    var authors: String { book?.authorNames ?? "" }

    /// Publish year
    var year: Int? {
        guard let y = uiState?.year else { return nil }
        return y.intValue
    }

    /// Rating (0-5 scale)
    var rating: Double? {
        guard let r = uiState?.rating else { return nil }
        return r.doubleValue
    }

    /// Progress (0.0-1.0) if reading
    var progress: Float? {
        guard let p = uiState?.progress else { return nil }
        return p.floatValue
    }

    /// Time remaining formatted (e.g., "2h 15m left")
    var timeRemaining: String? { uiState?.timeRemainingFormatted }

    /// Whether the book is complete (99%+ progress)
    var isComplete: Bool { uiState?.isComplete ?? false }

    /// Chapters list
    var chapters: [ChapterUiModel] {
        guard let list = uiState?.chapters else { return [] }
        return Array(list)
    }

    /// Genres list
    var genres: [String] {
        guard let list = uiState?.genresList else { return [] }
        return Array(list)
    }

    /// Book cover path
    var coverPath: String? { book?.coverPath }

    /// Book cover blur hash
    var coverBlurHash: String? { book?.coverBlurHash }

    /// Book duration formatted
    var duration: String { book?.formatDuration() ?? "" }

    /// Book duration in milliseconds
    var durationMs: Int64 { book?.duration ?? 0 }

    // MARK: - Private

    private let viewModel: BookDetailViewModel
    private let playbackManager: PlaybackManager
    private let audioPlayer: AudioPlayer
    private var observationTask: Task<Void, Never>?

    // MARK: - Initialization

    init(viewModel: BookDetailViewModel, playbackManager: PlaybackManager, audioPlayer: AudioPlayer) {
        self.viewModel = viewModel
        self.playbackManager = playbackManager
        self.audioPlayer = audioPlayer
        startObserving()
    }

    /// Call this to stop observation when done.
    func stopObserving() {
        observationTask?.cancel()
        observationTask = nil
    }

    // MARK: - Actions

    /// Load a book by ID
    func loadBook(bookId: String) {
        viewModel.loadBook(bookId: bookId)
    }

    /// Start or resume playback for this book
    func play() {
        guard let book = book else {
            
            return
        }
        
        playbackManager.activateBook(bookId: book.id)

        Task {
            print("[BookDetail] Preparing playback...")
            guard let result = try? await playbackManager.prepareForPlayback(bookId: book.id) else {
                return
            }

            try? await playbackManager.startPlayback(
                player: audioPlayer,
                resumePositionMs: result.resumePositionMs,
                resumeSpeed: result.resumeSpeed
            )
        }
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
