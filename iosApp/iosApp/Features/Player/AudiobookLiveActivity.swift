import ActivityKit
import WidgetKit
import SwiftUI

// MARK: - Activity Attributes

/// Defines the Live Activity for audiobook playback.
/// Static attributes are set once; dynamic ContentState updates in real time.
struct AudiobookActivityAttributes: ActivityAttributes {
    /// Static data — set when the activity starts
    let bookTitle: String
    let authorName: String
    let coverBlurHash: String?

    /// Dynamic data — updated as playback progresses
    struct ContentState: Codable, Hashable {
        let chapterTitle: String
        let isPlaying: Bool
        let progress: Float // 0.0–1.0 book-level
        let chapterProgress: Float // 0.0–1.0 chapter-level
        let elapsedFormatted: String // "2h 14m"
        let remainingFormatted: String // "4h 32m left"
    }
}
