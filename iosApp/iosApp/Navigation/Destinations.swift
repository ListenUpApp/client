import Foundation

// MARK: - Navigation Destinations

/// Type-safe navigation destinations for the app.
///
/// Using separate structs (vs. enum with associated values) because:
/// 1. NavigationStack requires Hashable conformance
/// 2. Each destination can evolve independently
/// 3. Cleaner pattern matching in navigationDestination(for:)

/// Navigate to book detail screen.
struct BookDestination: Hashable {
    let id: String
}

/// Navigate to series detail screen.
struct SeriesDestination: Hashable {
    let id: String
}

/// Navigate to contributor (author/narrator) detail screen.
struct ContributorDestination: Hashable {
    let id: String
}

/// Navigate to current user's profile.
struct UserProfileDestination: Hashable {}

/// Navigate to settings.
struct SettingsDestination: Hashable {}
