import SwiftUI
import Shared
import UIKit

/// Series detail screen showing all books in a series.
///
/// Features:
/// - Hero cover image (series cover or first book cover)
/// - Series title and stats (book count, total duration)
/// - Expandable description
/// - Ordered list of books with covers and metadata
/// - Navigation to book details
struct SeriesDetailView: View {
    let seriesId: String

    @Environment(\.dependencies) private var deps
    @State private var observer: SeriesDetailObserver?

    var body: some View {
        Group {
            if let observer, !observer.isLoading {
                content(observer: observer)
            } else {
                loadingView
            }
        }
        .background(Color(.systemBackground))
        .navigationTitle("Series")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button(action: {}) {
                        Label("Edit", systemImage: "pencil")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .onAppear {
            if observer == nil {
                let vm = deps.createSeriesDetailViewModel()
                observer = SeriesDetailObserver(viewModel: vm)
                observer?.loadSeries(seriesId: seriesId)
            }
        }
        .onDisappear {
            observer?.stopObserving()
        }
    }

    // MARK: - Content

    private func content(observer: SeriesDetailObserver) -> some View {
        ScrollView {
            VStack(spacing: 24) {
                // Header section
                headerSection(observer: observer)

                // Description (if available)
                if let description = observer.seriesDescription, !description.isEmpty {
                    descriptionSection(description: description)
                        .padding(.horizontal)
                }

                // Books list
                booksSection(observer: observer)
                    .padding(.horizontal)
            }
            .padding(.bottom, 32)
        }
    }

    // MARK: - Header Section

    private func headerSection(observer: SeriesDetailObserver) -> some View {
        VStack(spacing: 16) {
            // Cover image
            coverImage(observer: observer)
                .frame(width: 200, height: 200)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .shadow(color: .black.opacity(0.3), radius: 20, x: 0, y: 10)

            // Title and stats
            VStack(spacing: 8) {
                Text(observer.seriesName)
                    .font(.title2.bold())
                    .multilineTextAlignment(.center)

                HStack(spacing: 16) {
                    Label("\(observer.bookCount) \(observer.bookCount == 1 ? "book" : "books")", systemImage: "books.vertical")

                    Label(observer.totalDuration, systemImage: "clock")
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
            .padding(.horizontal)
        }
        .padding(.top, 16)
    }

    private func coverImage(observer: SeriesDetailObserver) -> some View {
        BookCoverImage(coverPath: observer.coverPath, blurHash: nil)
    }

    // MARK: - Description Section

    private func descriptionSection(description: String) -> some View {
        ExpandableText(title: "About", text: description, lineLimit: 3)
    }

    // MARK: - Books Section

    private func booksSection(observer: SeriesDetailObserver) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Books in Series")
                .font(.headline)

            ForEach(Array(observer.books.enumerated()), id: \.element.idString) { index, book in
                bookRow(book: book, sequence: book.seriesSequence ?? "\(index + 1)")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func bookRow(book: Book, sequence: String) -> some View {
        NavigationLink(value: BookDestination(id: book.idString)) {
            HStack(spacing: 16) {
                // Sequence number badge
                Text("#\(sequence)")
                    .font(.caption.bold())
                    .foregroundStyle(Color.listenUpOrange)
                    .frame(width: 36)

                // Cover thumbnail
                bookCoverThumbnail(book: book)
                    .frame(width: 50, height: 50)
                    .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))

                // Book info
                VStack(alignment: .leading, spacing: 4) {
                    Text(book.title)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.primary)
                        .lineLimit(2)

                    HStack(spacing: 8) {
                        Text(book.authorNames)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)

                        Text("•")
                            .foregroundStyle(.secondary)

                        Text(book.formatDuration())
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .padding(12)
            .background {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(.ultraThinMaterial)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(
                                LinearGradient(
                                    colors: [.white.opacity(0.3), .white.opacity(0.1)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ),
                                lineWidth: 0.5
                            )
                    }
            }
        }
        .buttonStyle(.pressScaleRow)
    }

    private func bookCoverThumbnail(book: Book) -> some View {
        BookCoverImage(book: book)
    }

    // MARK: - Loading View

    private var loadingView: some View {
        VStack(spacing: 16) {
            ProgressView()
            Text("Loading...")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        SeriesDetailView(seriesId: "preview-series-id")
    }
}
