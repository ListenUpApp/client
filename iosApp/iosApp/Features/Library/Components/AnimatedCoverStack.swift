import SwiftUI
import Shared
import UIKit

/// Displays overlapping book covers that cycle through all books in a series.
///
/// Features:
/// - Shows up to 5 overlapping covers with depth shadows
/// - Timer-based cycling animation (3 second interval)
/// - BlurHash placeholders for missing covers
/// - Smooth spring transitions between cycles
struct AnimatedCoverStack: View {
    let books: [Book]
    var cycleDurationSeconds: Double = 3.0
    var maxVisibleCovers: Int = 5

    @State private var cycleOffset = 0
    @State private var timer: Timer?

    /// Books to display, limited and offset for cycling
    private var visibleBooks: [Book] {
        guard !books.isEmpty else { return [] }
        let count = min(books.count, maxVisibleCovers)
        var result: [Book] = []
        for i in 0 ..< count {
            let index = (cycleOffset + i) % books.count
            result.append(books[index])
        }
        return result
    }

    var body: some View {
        GeometryReader { geo in
            let coverWidth = geo.size.width * 0.7
            let coverHeight = geo.size.height
            let stackOffset: CGFloat = 15

            ZStack {
                ForEach(Array(visibleBooks.enumerated().reversed()), id: \.element.idString) { index, book in
                    CoverView(book: book)
                        .frame(width: coverWidth, height: coverHeight)
                        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                        .shadow(
                            color: .black.opacity(0.15 - Double(index) * 0.02),
                            radius: 8 - CGFloat(index) * 1,
                            x: 0,
                            y: 4 - CGFloat(index)
                        )
                        .offset(x: CGFloat(index) * stackOffset)
                        .zIndex(Double(visibleBooks.count - index))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
        .onAppear {
            startCycling()
        }
        .onDisappear {
            stopCycling()
        }
    }

    // MARK: - Cycling

    private func startCycling() {
        guard books.count > maxVisibleCovers else { return }

        timer = Timer.scheduledTimer(withTimeInterval: cycleDurationSeconds, repeats: true) { _ in
            withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                cycleOffset = (cycleOffset + 1) % books.count
            }
        }
    }

    private func stopCycling() {
        timer?.invalidate()
        timer = nil
    }
}

/// Single cover view with local image or BlurHash fallback.
private struct CoverView: View {
    let book: Book

    private var cachedImage: UIImage? {
        guard let path = book.coverPath else { return nil }
        return UIImage(contentsOfFile: path)
    }

    var body: some View {
        if let image = cachedImage {
            Image(uiImage: image)
                .resizable()
                .aspectRatio(contentMode: .fill)
        } else if let blurHash = book.coverBlurHash {
            BlurHashView(blurHash: blurHash)
        } else {
            // Fallback placeholder
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
}

// MARK: - Preview

#Preview("Animated Cover Stack") {
    VStack(spacing: 32) {
        // Preview with mock data
        CoverStackPreview(coverCount: 8)
            .frame(height: 160)
            .padding(.horizontal)

        CoverStackPreview(coverCount: 3)
            .frame(height: 160)
            .padding(.horizontal)

        CoverStackPreview(coverCount: 1)
            .frame(height: 160)
            .padding(.horizontal)

        Spacer()
    }
    .padding(.top, 32)
    .background(Color(.systemBackground))
}

/// Preview helper for cover stack
private struct CoverStackPreview: View {
    let coverCount: Int

    var body: some View {
        GeometryReader { geo in
            let coverWidth = geo.size.width * 0.7
            let coverHeight = geo.size.height
            let stackOffset: CGFloat = 15

            ZStack {
                ForEach((0 ..< min(coverCount, 5)).reversed(), id: \.self) { index in
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color(hue: Double(index) * 0.15, saturation: 0.6, brightness: 0.8),
                                    Color(hue: Double(index) * 0.15 + 0.05, saturation: 0.5, brightness: 0.6)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: coverWidth, height: coverHeight)
                        .shadow(
                            color: .black.opacity(0.15 - Double(index) * 0.02),
                            radius: 8 - CGFloat(index),
                            x: 0,
                            y: 4 - CGFloat(index)
                        )
                        .offset(x: CGFloat(index) * stackOffset)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
    }
}
