import SwiftUI

/// Contributor (Author/Narrator) detail screen.
///
/// Will show:
/// - Contributor photo
/// - Name and role
/// - Biography
/// - List of their audiobooks
struct ContributorDetailView: View {
    let contributorId: String

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Contributor header
                headerSection

                // Bio section
                bioSection

                // Books section
                booksSection
            }
            .padding()
        }
        .background(Color(.systemBackground))
        .navigationTitle("Author")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(spacing: 16) {
            // Photo placeholder
            Circle()
                .fill(Color.gray.opacity(0.2))
                .frame(width: 120, height: 120)
                .overlay {
                    Image(systemName: "person.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(.secondary)
                }

            VStack(spacing: 4) {
                Text("Contributor Name")
                    .font(.title2.bold())

                Text("Author")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                Text("12 Audiobooks")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: - Bio Section

    private var bioSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("About")
                .font(.headline)

            Text("Biography placeholder. This will show information about the author or narrator, their background, and notable works.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Books Section

    private var booksSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Audiobooks")
                .font(.headline)

            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 16) {
                ForEach(1...4, id: \.self) { _ in
                    bookCard
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var bookCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.gray.opacity(0.2))
                .aspectRatio(0.7, contentMode: .fit)
                .overlay {
                    Image(systemName: "book.closed.fill")
                        .foregroundStyle(.secondary)
                }

            Text("Book Title")
                .font(.caption.weight(.medium))
                .lineLimit(2)
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        ContributorDetailView(contributorId: "preview-contributor-id")
    }
}
