import MediaPlayer
import UIKit
import Shared

/// Manages the system Now Playing Info Center (lock screen, Control Center, AirPlay).
///
/// Observes `NowPlayingObserver` state and pushes metadata + playback position
/// to `MPNowPlayingInfoCenter`. Registers remote command handlers for
/// play/pause, skip, seek, and chapter navigation.
@MainActor
final class NowPlayingInfoCenterManager {

    private let observer: NowPlayingObserver
    private var updateTask: Task<Void, Never>?
    private var coverImageTask: Task<Void, Never>?
    private var lastCoverPath: String?
    private var cachedArtwork: MPMediaItemArtwork?

    init(observer: NowPlayingObserver) {
        self.observer = observer
        registerRemoteCommands()
        startUpdating()
    }

    deinit {
        updateTask?.cancel()
        coverImageTask?.cancel()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    // MARK: - Remote Commands

    private func registerRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()

        // Play / Pause
        center.playCommand.isEnabled = true
        center.playCommand.addTarget { [weak self] _ in
            self?.observer.togglePlayback()
            return .success
        }

        center.pauseCommand.isEnabled = true
        center.pauseCommand.addTarget { [weak self] _ in
            self?.observer.togglePlayback()
            return .success
        }

        center.togglePlayPauseCommand.isEnabled = true
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            self?.observer.togglePlayback()
            return .success
        }

        // Skip backward 10s
        center.skipBackwardCommand.isEnabled = true
        center.skipBackwardCommand.preferredIntervals = [10]
        center.skipBackwardCommand.addTarget { [weak self] _ in
            self?.observer.skipBackward(seconds: 10)
            return .success
        }

        // Skip forward 30s
        center.skipForwardCommand.isEnabled = true
        center.skipForwardCommand.preferredIntervals = [30]
        center.skipForwardCommand.addTarget { [weak self] _ in
            self?.observer.skipForward(seconds: 30)
            return .success
        }

        // Seek (scrubber on lock screen)
        center.changePlaybackPositionCommand.isEnabled = true
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let self,
                  let positionEvent = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            let positionMs = Int64(positionEvent.positionTime * 1000)
            self.observer.seekTo(positionMs: positionMs)
            return .success
        }

        // Next/previous chapter via next/previous track
        center.nextTrackCommand.isEnabled = true
        center.nextTrackCommand.addTarget { [weak self] _ in
            guard let self else { return .commandFailed }
            let nextIndex = self.observer.chapterIndex + 1
            if nextIndex < self.observer.totalChapters {
                self.observer.selectChapter(index: nextIndex)
                return .success
            }
            return .noSuchContent
        }

        center.previousTrackCommand.isEnabled = true
        center.previousTrackCommand.addTarget { [weak self] _ in
            guard let self else { return .commandFailed }
            // If more than 3s into chapter, restart it; otherwise go to previous
            if self.observer.chapterPositionMs > 3000 {
                self.observer.selectChapter(index: self.observer.chapterIndex)
            } else if self.observer.chapterIndex > 0 {
                self.observer.selectChapter(index: self.observer.chapterIndex - 1)
            }
            return .success
        }

        // Playback speed
        center.changePlaybackRateCommand.isEnabled = true
        center.changePlaybackRateCommand.supportedPlaybackRates = [0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0]
        center.changePlaybackRateCommand.addTarget { [weak self] event in
            guard let self,
                  let rateEvent = event as? MPChangePlaybackRateCommandEvent else {
                return .commandFailed
            }
            self.observer.setSpeed(rateEvent.playbackRate)
            return .success
        }
    }

    // MARK: - Info Center Updates

    private func startUpdating() {
        // Poll at ~1Hz to keep the scrubber position smooth
        updateTask = Task {
            while !Task.isCancelled {
                updateNowPlayingInfo()
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }

    private func updateNowPlayingInfo() {
        guard observer.isVisible else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }

        var info = [String: Any]()

        // Metadata
        info[MPMediaItemPropertyTitle] = observer.chapterTitle ?? observer.bookTitle
        info[MPMediaItemPropertyArtist] = observer.authorName
        info[MPMediaItemPropertyAlbumTitle] = observer.bookTitle

        // Timing — use book-level position for the scrubber
        let positionSec = Double(observer.bookPositionMs) / 1000.0
        let durationSec = Double(observer.bookDurationMs) / 1000.0
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = positionSec
        info[MPMediaItemPropertyPlaybackDuration] = durationSec
        info[MPNowPlayingInfoPropertyPlaybackRate] = observer.isPlaying ? Double(observer.playbackSpeed) : 0.0
        info[MPNowPlayingInfoPropertyDefaultPlaybackRate] = Double(observer.playbackSpeed)

        // Chapter info
        info[MPNowPlayingInfoPropertyChapterNumber] = observer.chapterIndex
        info[MPNowPlayingInfoPropertyChapterCount] = observer.totalChapters

        // Artwork (loaded async, cached)
        if let artwork = cachedArtwork {
            info[MPMediaItemPropertyArtwork] = artwork
        }
        loadArtworkIfNeeded()

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        MPNowPlayingInfoCenter.default().playbackState = observer.isPlaying ? .playing : .paused
    }

    private func loadArtworkIfNeeded() {
        guard observer.coverPath != lastCoverPath else { return }
        lastCoverPath = observer.coverPath

        guard let path = observer.coverPath else {
            cachedArtwork = nil
            return
        }

        coverImageTask?.cancel()
        coverImageTask = Task.detached(priority: .utility) {
            guard let image = UIImage(contentsOfFile: path) else { return }
            let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            await MainActor.run {
                self.cachedArtwork = artwork
            }
        }
    }

    private func clearNowPlaying() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }
}
