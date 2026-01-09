import SwiftUI

/// Full screen player view for audiobook playback.
///
/// Based on mockup showing:
/// - Large book cover art
/// - Chapter title and book info
/// - Progress slider with timestamps
/// - Playback controls (skip back, play/pause, skip forward)
/// - Bottom controls (bookmark, sleep timer, speed, chapters)
struct FullScreenPlayerView: View {
    @Binding var isPresented: Bool

    // TODO: Replace with actual playback state
    @State private var progress: Double = 0.4
    @State private var isPlaying = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 32) {
                Spacer()

                // Book cover
                bookCover

                // Chapter info
                chapterInfo

                // Progress slider
                progressSection

                // Playback controls
                playbackControls

                Spacer()

                // Bottom controls
                bottomControls
            }
            .padding()
            .background(Color(.systemBackground))
            .navigationTitle("About")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: { isPresented = false }) {
                        Image(systemName: "chevron.down")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: {}) {
                        Image(systemName: "ellipsis")
                    }
                }
            }
        }
    }

    // MARK: - Book Cover

    private var bookCover: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color.gray.opacity(0.2))
            .aspectRatio(0.7, contentMode: .fit)
            .frame(maxWidth: 280)
            .overlay {
                Image(systemName: "book.closed.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(.secondary)
            }
            .shadow(radius: 20, y: 10)
    }

    // MARK: - Chapter Info

    private var chapterInfo: some View {
        VStack(spacing: 4) {
            Text("Chapter 29: The Skies Above")
                .font(.title3.bold())

            Text("A Song of Ice and Fire, Book 1")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Progress Section

    private var progressSection: some View {
        VStack(spacing: 8) {
            Slider(value: $progress)
                .tint(Color.listenUpOrange)

            HStack {
                Text("5:40:29")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Spacer()

                Text("-2:25:14")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal)
    }

    // MARK: - Playback Controls

    private var playbackControls: some View {
        HStack(spacing: 40) {
            // Skip backward
            Button(action: {}) {
                Image(systemName: "gobackward.10")
                    .font(.title)
                    .foregroundStyle(.primary)
            }

            // Play/Pause
            Button(action: { isPlaying.toggle() }) {
                Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .font(.system(size: 72))
                    .foregroundStyle(Color.listenUpOrange)
            }

            // Skip forward
            Button(action: {}) {
                Image(systemName: "goforward.10")
                    .font(.title)
                    .foregroundStyle(.primary)
            }
        }
    }

    // MARK: - Bottom Controls

    private var bottomControls: some View {
        HStack(spacing: 40) {
            Button(action: {}) {
                Image(systemName: "bookmark")
                    .font(.title2)
            }

            Button(action: {}) {
                Image(systemName: "moon.zzz")
                    .font(.title2)
            }

            Button(action: {}) {
                Text("2x")
                    .font(.title3.bold())
            }

            Button(action: {}) {
                Image(systemName: "list.bullet")
                    .font(.title2)
            }
        }
        .foregroundStyle(.secondary)
    }
}

// MARK: - Preview

#Preview {
    FullScreenPlayerView(isPresented: .constant(true))
}
