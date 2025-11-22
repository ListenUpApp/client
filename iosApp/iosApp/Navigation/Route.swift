import Foundation

/// Type-safe navigation routes for ListenUp iOS app.
///
/// Mirrors the Android route structure for consistency across platforms.
/// Routes are organized by authentication state:
///
/// **Unauthenticated flow:**
/// - `.serverSetup` → User enters and verifies server URL
/// - `.login` → User enters credentials
///
/// **Authenticated flow:**
/// - `.library` → Main screen showing audiobook collection
enum Route: Hashable {
    /// Server setup screen - first step in unauthenticated flow.
    /// User enters and verifies their ListenUp server URL.
    case serverSetup

    /// Login screen - second step in unauthenticated flow.
    /// User enters credentials after server is verified.
    case login

    /// Library screen - main authenticated screen.
    /// Shows user's audiobook collection.
    case library
}
