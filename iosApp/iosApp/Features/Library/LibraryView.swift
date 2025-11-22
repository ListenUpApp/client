import SwiftUI

/// Library screen placeholder.
///
/// TODO: Implement once Android LibraryScreen is complete.
/// Will feature:
/// - Search bar
/// - Tab navigation (Home, Library, Series, Saved)
/// - Book grid with covers
/// - Filter/sort controls
/// - Mini player at bottom
struct LibraryView: View {
    var body: some View {
        VStack(spacing: 24) {
            // Header
            VStack(alignment: .leading, spacing: 8) {
                Text("Hello, User!")
                    .font(.title3)
                    .foregroundStyle(.secondary)

                Text("What you want to read?")
                    .font(.largeTitle.bold())
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal)

            Spacer()

            // Placeholder
            VStack(spacing: 16) {
                Image(systemName: "books.vertical")
                    .font(.system(size: 64))
                    .foregroundStyle(.secondary)

                Text("Library Screen")
                    .font(.title2.bold())

                Text("To be implemented")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()
        }
        .padding(.top)
        .background(Color(.systemBackground))
    }
}

#Preview {
    NavigationStack {
        LibraryView()
            .navigationTitle("Library")
    }
}

#Preview("Dark Mode") {
    NavigationStack {
        LibraryView()
            .navigationTitle("Library")
    }
    .preferredColorScheme(.dark)
}
