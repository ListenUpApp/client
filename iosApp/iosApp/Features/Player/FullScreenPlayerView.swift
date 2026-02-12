import SwiftUI
import Shared
import UIKit

/// Full-screen audiobook player with blurred cover art background
/// and Liquid Glass controls card.
///
/// Layout:
/// - Blurred cover art background (full-bleed)
/// - Top bar: dismiss chevron + ellipsis menu
/// - Centered cover art at 65% screen width
/// - Chapter info below cover
/// - Bottom-anchored glass controls card with slider, transport, and actions
struct FullScreenPlayerView: View {
    let observer: NowPlayingObserver
    @Binding var isPresented: Bool

    @State private var sliderPosition: Double = 0
    @State private var isDraggingSlider: Bool = false

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // MARK: - Blurred Background
                backgroundLayer

                // MARK: - Content
                VStack(spacing: 0) {
                    // Top bar
                    topBar

                    Spacer()

                    // Cover art
                    coverArt(screenWidth: geometry.size.width)

                    Spacer()
                        .frame(height: 24)

                    // Chapter info
                    chapterInfo

                    Spacer()

                    // Controls card
                    controlsCard
                }
            }
        }
        .ignoresSafeArea(.all, edges: .bottom)
        .statusBarHidden(false)
        .onChange(of: observer.bookPositionMs) { _, newValue in
            if !isDraggingSlider {
                sliderPosition = Double(newValue)
            }
        }
        .onAppear {
            sliderPosition = Double(observer.bookPositionMs)
        }
    }

    // MARK: - Background

    private var backgroundLayer: some View {
        ZStack {
            // Cover art as blurred background
            BookCoverImage(
                coverPath: observer.coverPath,
                blurHash: observer.coverBlurHash
            )
            .ignoresSafeArea()
            .blur(radius: 50)
            .scaleEffect(1.2) // Prevent blur edges from showing

            // Dark overlay
            Color.black.opacity(0.3)
                .ignoresSafeArea()
        }
    }

    // MARK: - Top Bar

    private var topBar: some View {
        HStack {
            Button(action: { isPresented = false }) {
                Image(systemName: "chevron.down")
                    .font(.title3.weight(.medium))
                    .foregroundStyle(.primary)
                    .frame(width: 44, height: 44)
            }

            Spacer()

            Button(action: { /* TODO: options menu */ }) {
                Image(systemName: "ellipsis")
                    .font(.title3.weight(.medium))
                    .foregroundStyle(.primary)
                    .frame(width: 44, height: 44)
            }
        }
        .padding(.horizontal, 8)
    }

    // MARK: - Cover Art

    private func coverArt(screenWidth: CGFloat) -> some View {
        let coverWidth = screenWidth * 0.65

        return BookCoverImage(
            coverPath: observer.coverPath,
            blurHash: observer.coverBlurHash
        )
        .frame(width: coverWidth, height: coverWidth)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .shadow(color: .black.opacity(0.15), radius: 8, x: 0, y: 4)
    }

    // MARK: - Chapter Info

    private var chapterInfo: some View {
        VStack(spacing: 6) {
            if observer.totalChapters > 0 {
                Text(String(
                    format: NSLocalizedString("player.chapter_of", comment: ""),
                    "\(observer.chapterIndex + 1)",
                    "\(observer.totalChapters)"
                ))
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            if let chapterTitle = observer.chapterTitle {
                Text(chapterTitle)
                    .font(.title3.bold())
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            } else {
                Text(observer.bookTitle)
                    .font(.title3.bold())
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }

            Text(observer.authorName)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .padding(.horizontal, 24)
    }

    // MARK: - Controls Card

    private var controlsCard: some View {
        VStack(spacing: 20) {
            // Progress slider
            progressSection

            // Transport controls
            transportControls

            // Bottom action row
            bottomActions
        }
        .padding(.horizontal, 24)
        .padding(.top, 28)
        .padding(.bottom, 40)
        .background {
            UnevenRoundedRectangle(
                topLeadingRadius: 30,
                bottomLeadingRadius: 0,
                bottomTrailingRadius: 0,
                topTrailingRadius: 30
            )
            .fill(.thickMaterial)
            .overlay {
                UnevenRoundedRectangle(
                    topLeadingRadius: 30,
                    bottomLeadingRadius: 0,
                    bottomTrailingRadius: 0,
                    topTrailingRadius: 30
                )
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

    // MARK: - Progress Section

    private var progressSection: some View {
        VStack(spacing: 8) {
            Slider(
                value: $sliderPosition,
                in: 0...max(Double(observer.bookDurationMs), 1),
                onEditingChanged: { editing in
                    isDraggingSlider = editing
                    if !editing {
                        observer.seekTo(positionMs: Int64(sliderPosition))
                    }
                }
            )
            .tint(Color.listenUpOrange)

            HStack {
                Text(formatTime(Int64(isDraggingSlider ? sliderPosition : Double(observer.bookPositionMs))))
                    .font(.caption)
                    .fontDesign(.rounded)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()

                Spacer()

                Text(formatTimeRemaining(observer.bookDurationMs - Int64(isDraggingSlider ? sliderPosition : Double(observer.bookPositionMs))))
                    .font(.caption)
                    .fontDesign(.rounded)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
        }
    }

    // MARK: - Transport Controls

    private var transportControls: some View {
        HStack(spacing: 40) {
            // Skip backward
            Button(action: {
                observer.skipBackward(seconds: 10)
            }) {
                Image(systemName: "gobackward.10")
                    .font(.title)
                    .foregroundStyle(.primary)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(String(
                format: NSLocalizedString("player.skip_backward", comment: ""),
                "10"
            ))

            // Play/Pause
            Button(action: {
                let generator = UIImpactFeedbackGenerator(style: .medium)
                generator.impactOccurred()
                observer.togglePlayback()
            }) {
                ZStack {
                    Circle()
                        .fill(Color.listenUpOrange)
                        .frame(width: 64, height: 64)

                    Image(systemName: observer.isPlaying ? "pause.fill" : "play.fill")
                        .font(.title.weight(.semibold))
                        .foregroundStyle(.white)
                        .contentTransition(.symbolEffect(.replace.downUp))
                }
            }

            // Skip forward
            Button(action: {
                observer.skipForward(seconds: 10)
            }) {
                Image(systemName: "goforward.10")
                    .font(.title)
                    .foregroundStyle(.primary)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(String(
                format: NSLocalizedString("player.skip_forward", comment: ""),
                "10"
            ))
        }
    }

    // MARK: - Bottom Actions

    private var bottomActions: some View {
        HStack(spacing: 32) {
            // Bookmark
            Button(action: { /* TODO: bookmark */ }) {
                Image(systemName: "bookmark")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(NSLocalizedString("player.bookmark", comment: ""))

            // Sleep timer
            Button(action: { /* TODO: sleep timer */ }) {
                Image(systemName: "moon.zzz")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(NSLocalizedString("player.sleep_timer", comment: ""))

            // Speed
            Button(action: { /* TODO: speed picker */ }) {
                Text(formatSpeed(observer.playbackSpeed))
                    .font(.callout.weight(.medium))
                    .fontDesign(.rounded)
                    .foregroundStyle(.secondary)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(NSLocalizedString("player.speed", comment: ""))

            // Chapters
            Button(action: { /* TODO: chapter list */ }) {
                Image(systemName: "list.bullet")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(NSLocalizedString("player.chapters", comment: ""))
        }
    }

    // MARK: - Formatting Helpers

    /// Format milliseconds as "H:MM:SS" or "M:SS"
    private func formatTime(_ ms: Int64) -> String {
        let totalSeconds = max(0, ms / 1000)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60

        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            return String(format: "%d:%02d", minutes, seconds)
        }
    }

    /// Format milliseconds as "-H:MM:SS" or "-M:SS"
    private func formatTimeRemaining(_ ms: Int64) -> String {
        let totalSeconds = max(0, ms / 1000)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60

        if hours > 0 {
            return String(format: "-%d:%02d:%02d", hours, minutes, seconds)
        } else {
            return String(format: "-%d:%02d", minutes, seconds)
        }
    }

    /// Format speed as "1x" or "1.5x"
    private func formatSpeed(_ speed: Float) -> String {
        if speed == Float(Int(speed)) {
            return "\(Int(speed))x"
        } else {
            return String(format: "%.1fx", speed)
        }
    }
}

// MARK: - Preview

#Preview {
    Color.blue
        .ignoresSafeArea()
        .sheet(isPresented: .constant(true)) {
            Text("Full screen player preview requires observer")
        }
}
