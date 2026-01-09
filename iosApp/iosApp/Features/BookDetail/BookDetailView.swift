import SwiftUI

/// Book detail screen showing audiobook information.
///
/// Based on mockup showing:
/// - Book cover
/// - Title and series info
/// - Rating, duration, year
/// - Stream Now button
/// - Metadata (Author, Series, Narrators, Genres)
/// - Description (expandable)
/// - Chapter list
struct BookDetailView: View {
    let bookId: String

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Book cover
                bookCover

                // Title and info
                titleSection

                // Action buttons
                actionButtons

                // Metadata grid
                metadataSection

                // Description
                descriptionSection

                // Chapters
                chaptersSection
            }
            .padding()
        }
        .background(Color(.systemBackground))
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: {}) {
                    Image(systemName: "ellipsis")
                }
            }
        }
    }

    // MARK: - Book Cover

    private var bookCover: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color.gray.opacity(0.2))
            .aspectRatio(0.7, contentMode: .fit)
            .frame(maxWidth: 200)
            .overlay {
                Image(systemName: "book.closed.fill")
                    .font(.system(size: 48))
                    .foregroundStyle(.secondary)
            }
    }

    // MARK: - Title Section

    private var titleSection: some View {
        VStack(spacing: 8) {
            Text("Book Title")
                .font(.title2.bold())

            Text("Series Name, Book 1")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            HStack(spacing: 16) {
                Label("5.0", systemImage: "star.fill")
                    .foregroundStyle(Color.listenUpOrange)

                Label("33hr 46 min", systemImage: "clock")
                    .foregroundStyle(.secondary)

                Text("2023")
                    .foregroundStyle(.secondary)
            }
            .font(.caption)
        }
    }

    // MARK: - Action Buttons

    private var actionButtons: some View {
        HStack(spacing: 16) {
            Button(action: {}) {
                Label("Stream Now", systemImage: "play.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.listenUpOrange)

            Button(action: {}) {
                Image(systemName: "square.and.arrow.down")
                    .font(.title2)
                    .padding(14)
            }
            .buttonStyle(.bordered)
        }
    }

    // MARK: - Metadata Section

    private var metadataSection: some View {
        Grid(alignment: .leading, horizontalSpacing: 16, verticalSpacing: 12) {
            GridRow {
                Text("Author")
                    .foregroundStyle(.secondary)
                Text("Author Name")
                    .foregroundStyle(Color.listenUpOrange)
            }
            GridRow {
                Text("Series")
                    .foregroundStyle(.secondary)
                Text("Series Name")
                    .foregroundStyle(Color.listenUpOrange)
            }
            GridRow {
                Text("Narrators")
                    .foregroundStyle(.secondary)
                Text("Narrator Name")
                    .foregroundStyle(Color.listenUpOrange)
            }
            GridRow {
                Text("Genres")
                    .foregroundStyle(.secondary)
                Text("Science Fiction & Fantasy")
            }
        }
        .font(.subheadline)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Description Section

    private var descriptionSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Book description placeholder. This will show the book's synopsis and details about the story.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(3)

            Button("Read more") {}
                .font(.subheadline)
                .foregroundStyle(Color.listenUpOrange)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Chapters Section

    private var chaptersSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Chapters")
                    .font(.headline)

                Text("23")
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.listenUpOrange.opacity(0.2), in: Capsule())
                    .foregroundStyle(Color.listenUpOrange)
            }

            ForEach(1...3, id: \.self) { chapter in
                chapterRow(number: chapter, title: "Chapter \(chapter) - Title", duration: "25:04:00")
            }

            Button("See More") {}
                .font(.subheadline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 8))
        }
    }

    private func chapterRow(number: Int, title: String, duration: String) -> some View {
        HStack {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.gray.opacity(0.2))
                .frame(width: 44, height: 44)
                .overlay {
                    Image(systemName: "book.closed.fill")
                        .foregroundStyle(.secondary)
                }

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.medium))

                Text(duration)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Button(action: {}) {
                Image(systemName: "play.circle")
                    .font(.title2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        BookDetailView(bookId: "preview-book-id")
    }
}
