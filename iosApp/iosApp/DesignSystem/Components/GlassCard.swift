import SwiftUI

/// Clean surface card with solid background and strong shadow.
///
/// This is the foundational component of the modern clean design system.
/// It provides a solid background that adapts to light/dark mode
/// and creates depth through shadows.
///
/// Usage:
/// ```swift
/// GlassCard {
///     VStack {
///         Text("Content goes here")
///     }
/// }
/// ```
///
/// Customization:
/// ```swift
/// GlassCard(padding: 32, cornerRadius: 20) {
///     // content
/// }
/// ```
struct GlassCard<Content: View>: View {
    let content: Content
    var padding: CGFloat
    var cornerRadius: CGFloat

    init(
        padding: CGFloat = 24,
        cornerRadius: CGFloat = 28,
        @ViewBuilder content: () -> Content
    ) {
        self.padding = padding
        self.cornerRadius = cornerRadius
        self.content = content()
    }

    var body: some View {
        content
            .padding(padding)
            .background {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(Color(.systemBackground))
                    .overlay {
                        RoundedRectangle(cornerRadius: cornerRadius)
                            .stroke(Color.white.opacity(0.2), lineWidth: 0.5)
                    }
                    .shadow(color: .black.opacity(0.1), radius: 20, x: 0, y: 10)
            }
    }
}

// MARK: - Previews

#Preview("Glass Card - Light") {
    ZStack {
        // Gradient background to show translucency
        LinearGradient(
            colors: [.blue, .purple],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()

        GlassCard {
            VStack(alignment: .leading, spacing: 16) {
                Text("Liquid Glass")
                    .font(.title.bold())

                Text("This card demonstrates the ultra-modern frosted glass aesthetic with translucent background and depth.")
                    .font(.body)
                    .foregroundStyle(.secondary)

                HStack {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                    Text("Ultra-thin material")
                }

                HStack {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                    Text("Adaptive to theme")
                }
            }
        }
        .padding()
    }
}

#Preview("Glass Card - Dark") {
    ZStack {
        // Gradient background to show translucency
        LinearGradient(
            colors: [
                Color(hex: "8B3A3A"),
                Color(hex: "E8704A")
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()

        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Server Connection")
                    .font(.largeTitle.bold())

                Text("Enter your ListenUp server URL")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
    }
    .preferredColorScheme(.dark)
}

#Preview("Glass Card - Compact") {
    ZStack {
        LinearGradient(colors: [.blue, .cyan], startPoint: .topLeading, endPoint: .bottomTrailing)
            .ignoresSafeArea()

        GlassCard(padding: 16, cornerRadius: 16) {
            HStack {
                Image(systemName: "info.circle")
                Text("Compact glass card")
                Spacer()
            }
        }
        .padding()
    }
}
