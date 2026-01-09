import SwiftUI
import Shared

/// Root tab view for the main authenticated app experience.
///
/// Structure:
/// - TabView with Home, Library, Discover tabs
/// - Each tab wraps content in NavigationStack
/// - iPad gets sidebar-adaptable style
/// - ZStack overlay for MiniPlayerView
/// - fullScreenCover for FullScreenPlayerView
struct MainTabView: View {
    @State private var selectedTab: Tab = .home
    @State private var showFullScreenPlayer = false

    // TODO: Replace with actual NowPlayingViewModel observation
    @State private var isPlaying = false

    var body: some View {
        ZStack(alignment: .bottom) {
            TabView(selection: $selectedTab) {
                tab(.home, icon: "house.fill") { HomeView() }
                tab(.library, icon: "books.vertical.fill") { LibraryView() }
                tab(.discover, icon: "sparkles") { DiscoverView() }
            }
            .tabViewStyle(.sidebarAdaptable)

            // Mini player overlay
            if isPlaying {
                MiniPlayerView(onTap: { showFullScreenPlayer = true })
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.3), value: isPlaying)
        .tint(Color.listenUpOrange)
        .fullScreenCover(isPresented: $showFullScreenPlayer) {
            FullScreenPlayerView(isPresented: $showFullScreenPlayer)
        }
    }

    // MARK: - Tab Builder

    private func tab<Content: View>(
        _ tab: Tab,
        icon: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        NavigationStack {
            content()
                .navigationDestinations()
        }
        .tabItem {
            Label(tab.title, systemImage: icon)
        }
        .tag(tab)
        .safeAreaInset(edge: .bottom) {
            if isPlaying {
                Color.clear.frame(height: MiniPlayerView.height)
            }
        }
    }
}

// MARK: - Tab Enum

extension MainTabView {
    enum Tab: Hashable {
        case home
        case library
        case discover

        var title: String {
            switch self {
            case .home: "Home"
            case .library: "Library"
            case .discover: "Discover"
            }
        }
    }
}

// MARK: - Navigation Destinations

private extension View {
    func navigationDestinations() -> some View {
        self
            .navigationDestination(for: BookDestination.self) { destination in
                BookDetailView(bookId: destination.id)
            }
            .navigationDestination(for: SeriesDestination.self) { destination in
                SeriesDetailView(seriesId: destination.id)
            }
            .navigationDestination(for: ContributorDestination.self) { destination in
                ContributorDetailView(contributorId: destination.id)
            }
            .navigationDestination(for: UserProfileDestination.self) { _ in
                UserProfileView()
            }
            .navigationDestination(for: SettingsDestination.self) { _ in
                SettingsView()
            }
    }
}

// MARK: - Preview

#Preview {
    MainTabView()
        .environment(CurrentUserObserver())
}
