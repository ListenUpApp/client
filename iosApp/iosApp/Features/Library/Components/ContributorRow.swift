import SwiftUI
import Shared
import UIKit

/// Role type for contributor (used for accessibility labels)
enum ContributorRole {
    case author
    case narrator

    var label: String {
        switch self {
        case .author: "Author"
        case .narrator: "Narrator"
        }
    }
}

/// Row displaying a contributor (author/narrator) with avatar, name, and book count.
///
/// Features:
/// - Avatar with image or colored initials fallback
/// - Glass card styling with subtle border
/// - Navigation to contributor detail
/// - Press scale animation
struct ContributorRow: View {
    let contributor: ContributorWithBookCount
    let role: ContributorRole

    @State private var isPressed = false

    private var contributorId: String {
        String(describing: contributor.contributor.id)
    }

    private var contributorName: String {
        contributor.contributor.name
    }

    private var contributorImagePath: String? {
        contributor.contributor.imagePath
    }

    private var bookCountLabel: String {
        let count = contributor.bookCount
        return "\(count) \(count == 1 ? "book" : "books")"
    }

    var body: some View {
        NavigationLink(value: ContributorDestination(id: contributorId)) {
            rowContent
        }
        .buttonStyle(.plain)
        .animation(.spring(response: 0.2, dampingFraction: 0.7), value: isPressed)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in isPressed = true }
                .onEnded { _ in isPressed = false }
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(contributorName), \(role.label)")
        .accessibilityValue(bookCountLabel)
        .accessibilityHint("Double tap to view \(role.label.lowercased()) details")
    }

    private var rowContent: some View {
        HStack(spacing: 16) {
            avatarView
                .frame(width: 48, height: 48)

            VStack(alignment: .leading, spacing: 2) {
                Text(contributorName)
                    .font(.body.weight(.medium))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Text(bookCountLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .padding(16)
        .background(rowBackground)
        .scaleEffect(isPressed ? 0.98 : 1.0)
    }

    private var avatarView: some View {
        ZStack {
            Circle()
                .fill(avatarColor)

            if let path = contributorImagePath,
               let uiImage = UIImage(contentsOfFile: path) {
                Image(uiImage: uiImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .clipShape(Circle())
            } else {
                Text(initials)
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
            }
        }
        .clipShape(Circle())
    }

    private var initials: String {
        let components = contributorName.split(separator: " ")
        if components.count >= 2 {
            let first = components[0].prefix(1)
            let last = components[components.count - 1].prefix(1)
            return "\(first)\(last)".uppercased()
        } else if let first = components.first {
            return String(first.prefix(2)).uppercased()
        }
        return "?"
    }

    private var avatarColor: Color {
        let hash = contributorId.hashValue
        let hue = Double(abs(hash) % 360) / 360.0
        return Color(hue: hue, saturation: 0.5, brightness: 0.7)
    }

    private var rowBackground: some View {
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

// MARK: - Preview

#Preview("Contributor Row") {
    NavigationStack {
        ScrollView {
            VStack(spacing: 12) {
                ContributorRowPreview(name: "Patrick Rothfuss", bookCount: 3, role: .author)
                ContributorRowPreview(name: "Tim Gerard Reynolds", bookCount: 12, role: .narrator)
                ContributorRowPreview(name: "Brandon Sanderson", bookCount: 47, role: .author)
            }
            .padding()
        }
        .background(Color(.systemBackground))
        .navigationTitle("Authors")
    }
}

/// Preview helper since we can't create Kotlin objects in previews
private struct ContributorRowPreview: View {
    let name: String
    let bookCount: Int
    let role: ContributorRole

    @State private var isPressed = false

    var body: some View {
        rowContent
            .scaleEffect(isPressed ? 0.98 : 1.0)
            .animation(.spring(response: 0.2, dampingFraction: 0.7), value: isPressed)
            .onTapGesture {}
            .simultaneousGesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in isPressed = true }
                    .onEnded { _ in isPressed = false }
            )
    }

    private var rowContent: some View {
        HStack(spacing: 16) {
            Circle()
                .fill(Color(hue: Double(name.hashValue.magnitude % 360) / 360.0, saturation: 0.5, brightness: 0.7))
                .frame(width: 48, height: 48)
                .overlay {
                    Text(initials)
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .foregroundStyle(.white)
                }

            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.body.weight(.medium))
                    .foregroundStyle(.primary)
                Text("\(bookCount) \(bookCount == 1 ? "book" : "books")")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .padding(16)
        .background(rowBackground)
    }

    private var rowBackground: some View {
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

    private var initials: String {
        let components = name.split(separator: " ")
        if components.count >= 2 {
            return "\(components[0].prefix(1))\(components.last!.prefix(1))".uppercased()
        }
        return String(name.prefix(2)).uppercased()
    }
}
