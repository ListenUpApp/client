import SwiftUI
import Shared

/// Circular avatar displaying user initials or image.
///
/// Shows:
/// - User's uploaded avatar image if available
/// - Colored circle with initials as fallback
/// - Gray placeholder when user is nil
struct UserAvatarView: View {
    let user: User?
    let size: CGFloat

    init(user: User?, size: CGFloat = 36) {
        self.user = user
        self.size = size
    }

    var body: some View {
        if let user {
            // TODO: Support user.hasImageAvatar with AsyncImage when server URL is available
            initialsAvatar(for: user)
        } else {
            placeholderAvatar
        }
    }

    // MARK: - Private Views

    private func initialsAvatar(for user: User) -> some View {
        Circle()
            .fill(avatarColor(for: user))
            .frame(width: size, height: size)
            .overlay {
                Text(user.initials)
                    .font(.system(size: size * 0.4, weight: .medium))
                    .foregroundStyle(.white)
            }
    }

    private var placeholderAvatar: some View {
        Circle()
            .fill(Color.gray.opacity(0.3))
            .frame(width: size, height: size)
            .overlay {
                Image(systemName: "person.fill")
                    .font(.system(size: size * 0.5))
                    .foregroundStyle(.secondary)
            }
    }

    private func avatarColor(for user: User) -> Color {
        Color(hex: user.avatarColor) ?? avatarColorForUserId(user.idString)
    }
}

// MARK: - Preview

#Preview("With User") {
    // Can't easily create a User in preview without Kotlin, so show placeholder
    UserAvatarView(user: nil, size: 40)
}

#Preview("Placeholder") {
    UserAvatarView(user: nil, size: 40)
}
