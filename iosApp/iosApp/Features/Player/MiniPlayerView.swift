import SwiftUI

/// Mini player shown at the bottom of the screen during playback.
///
/// Based on mockup showing:
/// - Book cover thumbnail
/// - Title and chapter info
/// - Progress bar
/// - Play/pause controls
struct MiniPlayerView: View {
    /// Height of the mini player for safe area insets.
    static let height: CGFloat = 64

    var onTap: () -> Void

    // TODO: Replace with actual playback state
    @State private var progress: Double = 0.4

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 0) {
                // Progress bar at top
                GeometryReader { geometry in
                    Rectangle()
                        .fill(Color.listenUpOrange)
                        .frame(width: geometry.size.width * progress)
                }
                .frame(height: 2)
                .background(Color.gray.opacity(0.3))

                // Content
                HStack(spacing: 12) {
                    // Book cover placeholder
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: 44, height: 44)
                        .overlay {
                            Image(systemName: "book.closed.fill")
                                .foregroundStyle(.secondary)
                        }

                    // Title and chapter
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Book Title")
                            .font(.subheadline.weight(.medium))
                            .lineLimit(1)

                        Text("Chapter 29: Title")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    // Time remaining
                    Text("-1:04")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    // Play/pause button
                    Button(action: {}) {
                        Image(systemName: "play.fill")
                            .font(.title3)
                            .foregroundStyle(Color.listenUpOrange)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal)
                .padding(.vertical, 8)
            }
            .frame(height: Self.height)
            .background(.ultraThinMaterial)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Preview

#Preview {
    VStack {
        Spacer()
        MiniPlayerView(onTap: {})
    }
}
