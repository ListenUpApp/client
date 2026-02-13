import ActivityKit
import SwiftUI
import WidgetKit

// MARK: - Shared Activity Attributes (must match the app's definition exactly)

struct AudiobookActivityAttributes: ActivityAttributes {
    let bookTitle: String
    let authorName: String
    let coverBlurHash: String?

    struct ContentState: Codable, Hashable {
        let chapterTitle: String
        let isPlaying: Bool
        let progress: Float
        let chapterProgress: Float
        let elapsedFormatted: String
        let remainingFormatted: String
    }
}

// MARK: - Brand Color

private extension Color {
    static let listenUpOrange = Color(red: 1.0, green: 0.42, blue: 0.29) // #FF6B4A
}

// MARK: - Widget

struct AudiobookLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: AudiobookActivityAttributes.self) { context in
            EmptyView() // Lock screen handled by system Now Playing controls
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    ZStack {
                        Circle()
                            .stroke(Color.white.opacity(0.2), lineWidth: 3)
                        Circle()
                            .trim(from: 0, to: CGFloat(context.state.progress))
                            .stroke(Color.listenUpOrange, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                        Image(systemName: context.state.isPlaying ? "pause.fill" : "play.fill")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                    }
                    .frame(width: 44, height: 44)
                }

                DynamicIslandExpandedRegion(.center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(context.attributes.bookTitle)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        Text(context.state.chapterTitle)
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.7))
                            .lineLimit(1)
                    }
                }

                DynamicIslandExpandedRegion(.trailing) {
                    Text(context.state.remainingFormatted)
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.7))
                }

                DynamicIslandExpandedRegion(.bottom) {
                    ProgressView(value: context.state.chapterProgress)
                        .tint(Color.listenUpOrange)
                        .padding(.horizontal, 4)
                }
            } compactLeading: {
                Image(systemName: context.state.isPlaying ? "waveform" : "pause.fill")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.listenUpOrange)
            } compactTrailing: {
                ZStack {
                    Circle()
                        .stroke(Color.white.opacity(0.2), lineWidth: 2)
                    Circle()
                        .trim(from: 0, to: CGFloat(context.state.progress))
                        .stroke(Color.listenUpOrange, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                }
                .frame(width: 16, height: 16)
            } minimal: {
                ZStack {
                    Circle()
                        .stroke(Color.white.opacity(0.2), lineWidth: 2)
                    Circle()
                        .trim(from: 0, to: CGFloat(context.state.progress))
                        .stroke(Color.listenUpOrange, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                }
            }
        }
    }

    // MARK: - Lock Screen Banner

    @ViewBuilder
    private func lockScreenView(context: ActivityViewContext<AudiobookActivityAttributes>) -> some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.listenUpOrange.opacity(0.3))
                .frame(width: 48, height: 48)
                .overlay {
                    Image(systemName: "book.closed.fill")
                        .foregroundStyle(Color.listenUpOrange)
                }

            VStack(alignment: .leading, spacing: 4) {
                Text(context.attributes.bookTitle)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)

                Text(context.state.chapterTitle)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.7))
                    .lineLimit(1)

                ProgressView(value: context.state.progress)
                    .tint(Color.listenUpOrange)
            }

            Spacer()

            Image(systemName: context.state.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                .font(.title)
                .foregroundStyle(Color.listenUpOrange)
        }
        .padding(16)
        .background(Color.black.opacity(0.8))
    }
}
