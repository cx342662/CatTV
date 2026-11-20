import SwiftUI
import AVKit
import CatTVCore

@MainActor
final class PlaybackModel: ObservableObject {
    let player = AVPlayer()
    @Published private(set) var failure: String?
    private var onProgress: ((Double) -> Void)?
    private var timeObserver: Any?
    private var observation: NSKeyValueObservation?
    private var timer: Task<Void, Never>?
    func play(episode: Episode, position: Double = 0, progress: @escaping (Double) -> Void) throws {
        guard let url = URL(string: episode.address), ["http", "https"].contains(url.scheme?.lowercased() ?? "") else { throw SourceError.invalidURL }
        guard ["m3u8", "mp4", "mov", "m4v", "mp3", "m4a"].contains(url.pathExtension.lowercased()) else {
            throw SourceError.invalidConfiguration("此地址需要解析或格式适配，当前不能直接播放")
        }
        try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try AVAudioSession.sharedInstance().setActive(true)
        saveProgress()
        onProgress = progress
        if let timeObserver { player.removeTimeObserver(timeObserver); self.timeObserver = nil }
        let item = AVPlayerItem(url: url)
        observation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            if item.status == .failed {
                let message = item.error?.localizedDescription ?? "播放失败"
                Task { @MainActor [weak self] in self?.failure = message }
            }
        }
        player.allowsExternalPlayback = true
        player.replaceCurrentItem(with: item)
        if position > 0 { player.seek(to: CMTime(seconds: position, preferredTimescale: 600)) }
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 5, preferredTimescale: 1), queue: .main) { [weak self] time in
            Task { @MainActor [weak self] in self?.onProgress?(time.seconds) }
        }
        player.play()
    }
    func setStopTimer(minutes: Int?) {
        timer?.cancel()
        guard let minutes else { timer = nil; return }
        timer = Task { [weak self] in
            do { try await Task.sleep(nanoseconds: UInt64(minutes) * 60 * 1_000_000_000) }
            catch { return }
            self?.stop()
        }
    }
    private func saveProgress() {
        if player.currentItem != nil { onProgress?(player.currentTime().seconds) }
    }
    func stop() {
        saveProgress()
        if let timeObserver { player.removeTimeObserver(timeObserver); self.timeObserver = nil }
        timer?.cancel()
        timer = nil
        player.pause()
        player.replaceCurrentItem(with: nil)
        observation = nil
        onProgress = nil
    }
    deinit { timer?.cancel() }
}
struct PlayerHost: UIViewControllerRepresentable {
    let player: AVPlayer
    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player
        controller.allowsPictureInPicturePlayback = true
        controller.canStartPictureInPictureAutomaticallyFromInline = true
        return controller
    }
    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {
        if controller.player !== player { controller.player = player }
    }
}
