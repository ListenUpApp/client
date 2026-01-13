import SwiftUI

/// Tabs available in the Library screen.
///
/// Each tab represents a different view of the user's audiobook collection.
/// Matches Android's `LibraryTab` enum structure.
enum LibraryTab: String, CaseIterable, Identifiable {
    case books
    case series
    case authors
    case narrators

    var id: String { rawValue }

    /// Display title for the tab
    var title: String {
        switch self {
        case .books: "Books"
        case .series: "Series"
        case .authors: "Authors"
        case .narrators: "Narrators"
        }
    }

    /// SF Symbol icon for the tab
    var icon: String {
        switch self {
        case .books: "book.fill"
        case .series: "books.vertical.fill"
        case .authors: "person.fill"
        case .narrators: "waveform.circle.fill"
        }
    }
}
