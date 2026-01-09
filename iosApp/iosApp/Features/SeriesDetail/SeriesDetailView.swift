import SwiftUI

/// Series detail screen showing all books in a series.
///
/// Will show:
/// - Series cover/art
/// - Series title and author
/// - Number of books
/// - Total duration
/// - List of books in order
struct SeriesDetailView: View {
    let seriesId: String

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Series header
                headerSection

                // Books list
                booksSection
            }
            .padding()
        }
        .background(Color(.systemBackground))
        .navigationTitle("Series")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(spacing: 16) {
            // Series art placeholder
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.gray.opacity(0.2))
                .aspectRatio(1.5, contentMode: .fit)
                .frame(maxWidth: 280)
                .overlay {
                    Image(systemName: "books.vertical.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(.secondary)
                }

            VStack(spacing: 4) {
                Text("Series Title")
                    .font(.title2.bold())

                Text("by Author Name")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                Text("5 Books")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - Books Section

    private var booksSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Books in Series")
                .font(.headline)

            ForEach(1...3, id: \.self) { book in
                bookRow(number: book, title: "Book \(book) Title")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func bookRow(number: Int, title: String) -> some View {
        HStack {
            Text("#\(number)")
                .font(.caption.bold())
                .foregroundStyle(Color.listenUpOrange)
                .frame(width: 30)

            RoundedRectangle(cornerRadius: 6)
                .fill(Color.gray.opacity(0.2))
                .frame(width: 50, height: 70)
                .overlay {
                    Image(systemName: "book.closed.fill")
                        .foregroundStyle(.secondary)
                }

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.medium))

                Text("10hr 30min")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .foregroundStyle(.tertiary)
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        SeriesDetailView(seriesId: "preview-series-id")
    }
}
