import SwiftUI
import Shared
import UIKit

/// Reusable book cover image component with fallback chain.
///
/// Display priority:
/// 1. Local file image (if coverPath exists and is valid)
/// 2. BlurHash placeholder (if blurHash exists)
/// 3. Gradient placeholder with book icon
///
/// Usage:
/// ```swift
/// BookCoverImage(coverPath: book.coverPath, blurHash: book.coverBlurHash)
///     .frame(width: 100, height: 100)
///     .clipShape(RoundedRectangle(cornerRadius: 8))
/// ```
struct BookCoverImage: View {
    let coverPath: String?
    let blurHash: String?

    /// Convenience initializer from a Book object
    init(book: Book) {
        self.coverPath = book.coverPath
        self.blurHash = book.coverBlurHash
    }

    /// Direct initializer for custom sources
    init(coverPath: String?, blurHash: String? = nil) {
        self.coverPath = coverPath
        self.blurHash = blurHash
    }

    var body: some View {
        if let path = coverPath,
           let uiImage = UIImage(contentsOfFile: path) {
            Image(uiImage: uiImage)
                .resizable()
                .aspectRatio(contentMode: .fill)
        } else if let blurHash {
            BlurHashView(blurHash: blurHash)
        } else {
            placeholder
        }
    }

    private var placeholder: some View {
        ZStack {
            LinearGradient(
                colors: [Color.gray.opacity(0.3), Color.gray.opacity(0.2)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Image(systemName: "book.closed.fill")
                .font(.system(size: 24))
                .foregroundStyle(.secondary)
        }
    }
}

// MARK: - Preview

#Preview("With Cover") {
    BookCoverImage(coverPath: nil, blurHash: "LEHV6nWB2yk8pyo0adR*.7kCMdnj")
        .frame(width: 100, height: 100)
        .clipShape(RoundedRectangle(cornerRadius: 8))
}

#Preview("Placeholder") {
    BookCoverImage(coverPath: nil, blurHash: nil)
        .frame(width: 100, height: 100)
        .clipShape(RoundedRectangle(cornerRadius: 8))
}
