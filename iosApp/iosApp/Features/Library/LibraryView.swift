import SwiftUI
import Shared

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
    @Environment(CurrentUserObserver.self) private var userObserver
    @State private var searchText = ""

    private var user: User? { userObserver.user }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                // Header
                VStack(alignment: .leading, spacing: 8) {
                    Text("Hello, \(user?.displayName ?? "User")!")
                        .font(.title3)
                        .foregroundStyle(.secondary)

                    Text("What you want to read?")
                        .font(.largeTitle.bold())
                }

                // Search bar
                searchBar

                // Placeholder content
                contentPlaceholder
            }
            .padding()
        }
        .background(Color(.systemBackground))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: UserProfileDestination()) {
                    UserAvatarView(user: user, size: 32)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Search Bar

    private var searchBar: some View {
        HStack(spacing: 12) {
            TextField("Search book", text: $searchText)
                .textFieldStyle(.plain)

            Button(action: {}) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.white)
                    .padding(10)
                    .background(Color.listenUpOrange, in: Circle())
            }
        }
        .padding(.leading, 16)
        .padding(.trailing, 4)
        .padding(.vertical, 4)
        .background(Color(.secondarySystemBackground), in: Capsule())
    }

    // MARK: - Content Placeholder

    private var contentPlaceholder: some View {
        VStack(spacing: 16) {
            Spacer()
                .frame(height: 60)

            Image(systemName: "books.vertical")
                .font(.system(size: 64))
                .foregroundStyle(.secondary)

            Text("Library Screen")
                .font(.title2.bold())

            Text("To be implemented")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 40)
    }
}

#Preview {
    NavigationStack {
        LibraryView()
    }
    .environment(CurrentUserObserver())
}

#Preview("Dark Mode") {
    NavigationStack {
        LibraryView()
    }
    .environment(CurrentUserObserver())
    .preferredColorScheme(.dark)
}
