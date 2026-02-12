import SwiftUI
import Shared

/// Bridges PlaybackManager state flows to SwiftUI for the Now Playing UI.
///
/// Collects multiple Kotlin StateFlows via SKIE and exposes them as
/// `@Observable` properties. Uses a separate Task per flow, all cancelled
/// together on `stop()`.
@Observable
@MainActor
final class NowPlayingObserver {

    // MARK: - Published State

    /// Whether the mini player should be visible (a book is loaded)
    private(set) var isVisible: Bool = false

    /// Whether audio is currently playing
    private(set) var isPlaying: Bool = false

    /// Whether the player is buffering
    private(set) var isBuffering: Bool = false

    /// Current book title
    private(set) var bookTitle: String = ""

    /// Author name(s)
    private(set) var authorName: String = ""

    /// Local cover image path (nil if no cover)
    private(set) var coverPath: String?

    /// Cover blur hash for placeholder
    private(set) var coverBlurHash: String?

    /// Current chapter title
    private(set) var chapterTitle: String?

    /// Current chapter index (0-based)
    private(set) var chapterIndex: Int = 0

    /// Total number of chapters
    private(set) var totalChapters: Int = 0

    /// Book-level progress (0.0–1.0)
    var bookProgress: Float {
        guard bookDurationMs > 0 else { return 0 }
        return Float(bookPositionMs) / Float(bookDurationMs)
    }

    /// Current position in book (ms)
    private(set) var bookPositionMs: Int64 = 0

    /// Total book duration (ms)
    private(set) var bookDurationMs: Int64 = 0

    /// Current position within the current chapter (ms)
    var chapterPositionMs: Int64 {
        guard let info = currentChapterInfo else { return 0 }
        return max(0, bookPositionMs - Int64(info.startMs))
    }

    /// Total duration of the current chapter (ms)
    var chapterDurationMs: Int64 {
        guard let info = currentChapterInfo else { return 0 }
        return Int64(info.endMs - info.startMs)
    }

    /// Current playback speed
    private(set) var playbackSpeed: Float = 1.0

    // MARK: - Private

    private let playbackManager: PlaybackManager
    private let audioPlayer: AudioPlayer
    private let bookRepository: BookRepository
    private let imageStorage: ImageStorage

    private var observationTasks: [Task<Void, Never>] = []
    private var currentChapterInfo: PlaybackManager.ChapterInfo?
    private var currentBookIdValue: String?

    // MARK: - Initialization

    init(
        playbackManager: PlaybackManager,
        audioPlayer: AudioPlayer,
        bookRepository: BookRepository,
        imageStorage: ImageStorage
    ) {
        self.playbackManager = playbackManager
        self.audioPlayer = audioPlayer
        self.bookRepository = bookRepository
        self.imageStorage = imageStorage
        startObserving()
    }

    /// Convenience initializer using Dependencies
    convenience init(deps: Dependencies) {
        self.init(
            playbackManager: deps.playbackManager,
            audioPlayer: deps.audioPlayer,
            bookRepository: deps.bookRepository,
            imageStorage: deps.imageStorage
        )
    }

    // MARK: - Actions

    /// Toggle play/pause
    func togglePlayback() {
        if isPlaying {
            audioPlayer.pause()
            playbackManager.setPlaying(playing: false)
        } else {
            audioPlayer.play()
            playbackManager.setPlaying(playing: true)
        }
    }

    /// Seek to a book-relative position
    func seekTo(positionMs: Int64) {
        audioPlayer.seekTo(positionMs: positionMs)
    }

    /// Set playback speed
    func setSpeed(_ speed: Float) {
        audioPlayer.setSpeed(speed: speed)
        playbackManager.onSpeedChanged(speed: speed)
    }

    /// Skip forward by seconds
    func skipForward(seconds: Int = 10) {
        let newPosition = bookPositionMs + Int64(seconds * 1000)
        let clamped = min(newPosition, bookDurationMs)
        audioPlayer.seekTo(positionMs: clamped)
    }

    /// Skip backward by seconds
    func skipBackward(seconds: Int = 10) {
        let newPosition = bookPositionMs - Int64(seconds * 1000)
        let clamped = max(newPosition, 0)
        audioPlayer.seekTo(positionMs: clamped)
    }

    /// Jump to a specific chapter by index
    func selectChapter(index: Int) {
        let chapters = Array(playbackManager.chapters.value)
        guard index >= 0, index < chapters.count else { return }
        let chapter = chapters[index]
        audioPlayer.seekTo(positionMs: chapter.startTime)
    }

    /// Stop all observations — call when done
    func stop() {
        observationTasks.forEach { $0.cancel() }
        observationTasks.removeAll()
    }

    // MARK: - Observation

    private func startObserving() {
        // Observe currentBookId
        observationTasks.append(Task { [weak self] in
            guard let self else { return }
            for await bookId in self.playbackManager.currentBookId {
                guard !Task.isCancelled else { break }
                let idValue = bookId as? String
                self.isVisible = idValue != nil
                if idValue != self.currentBookIdValue {
                    self.currentBookIdValue = idValue
                    if let id = idValue {
                        await self.loadBookInfo(bookId: id)
                    } else {
                        self.bookTitle = ""
                        self.authorName = ""
                        self.coverPath = nil
                        self.coverBlurHash = nil
                    }
                }
            }
        })

        // Observe isPlaying
        observationTasks.append(Task { [weak self] in
            guard let self else { return }
            for await playing in self.playbackManager.isPlaying {
                guard !Task.isCancelled else { break }
                self.isPlaying = playing.boolValue
            }
        })

        // Observe currentPositionMs
        observationTasks.append(Task { [weak self] in
            guard let self else { return }
            for await position in self.playbackManager.currentPositionMs {
                guard !Task.isCancelled else { break }
                self.bookPositionMs = position.int64Value
            }
        })

        // Observe totalDurationMs
        observationTasks.append(Task { [weak self] in
            guard let self else { return }
            for await duration in self.playbackManager.totalDurationMs {
                guard !Task.isCancelled else { break }
                self.bookDurationMs = duration.int64Value
            }
        })

        // Observe playbackSpeed
        observationTasks.append(Task { [weak self] in
            guard let self else { return }
            for await speed in self.playbackManager.playbackSpeed {
                guard !Task.isCancelled else { break }
                self.playbackSpeed = speed.floatValue
            }
        })

        // Observe currentChapter
        observationTasks.append(Task { [weak self] in
            guard let self else { return }
            for await chapterInfo in self.playbackManager.currentChapter {
                guard !Task.isCancelled else { break }
                if let info = chapterInfo {
                    self.currentChapterInfo = info
                    self.chapterTitle = info.title
                    self.chapterIndex = Int(info.index)
                    self.totalChapters = Int(info.totalChapters)
                } else {
                    self.currentChapterInfo = nil
                    self.chapterTitle = nil
                    self.chapterIndex = 0
                    self.totalChapters = 0
                }
            }
        })

        // Observe audio player state for buffering detection
        observationTasks.append(Task { [weak self] in
            guard let self else { return }
            for await state in self.audioPlayer.state {
                guard !Task.isCancelled else { break }
                self.isBuffering = state == .buffering
            }
        })
    }

    /// Load book metadata when currentBookId changes
    private func loadBookInfo(bookId: String) async {
        guard let book = try? await bookRepository.getBook(id: bookId) else { return }
        bookTitle = book.title
        authorName = book.authorNames
        coverPath = book.coverPath
        coverBlurHash = book.coverBlurHash
    }
}
