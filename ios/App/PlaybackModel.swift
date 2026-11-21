import SwiftUI
import AVKit
import MediaPlayer
import CatTVCore

@MainActor
final class PlaybackModel: ObservableObject {
    @Published private(set) var subtitleCues: [SubtitleCue] = []
    @Published private(set) var subtitleName: String?
    @Published var subtitleOffset = 0.0
    func setSubtitles(_ cues: [SubtitleCue], name: String) { subtitleCues = cues; subtitleName = name; subtitleOffset = 0 }
    func clearSubtitles() { subtitleCues = []; subtitleName = nil; subtitleOffset = 0 }
    func importSubtitles(from url: URL) async throws {
        let expected = token
        guard player.currentItem != nil else { throw SourceError.invalidConfiguration("请先开始播放") }
        let cues = try await Task.detached {
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            return try SubtitleDocument.decode(Data(contentsOf: url))
        }.value
        guard expected == token, player.currentItem != nil else { throw SourceError.invalidConfiguration("播放内容已经切换，请重新导入字幕") }
        setSubtitles(cues, name: url.lastPathComponent)
    }
    let player = AVPlayer()
    let sniffer = WebMediaSniffer()
    @Published private(set) var failure: String?
    @Published private(set) var title = ""
    @Published private(set) var episodeName = ""
    @Published private(set) var isPlaying = false
    @Published private(set) var isLive = false
    @Published private(set) var allowLANProxy = UserDefaults.standard.bool(forKey: "CatTV.allowLANProxy")
    @Published private(set) var usingLANProxy = false
    @Published private(set) var elapsedSeconds = 0.0
    private var lastProgressSave = 0.0
    @Published private(set) var resolving = false
    @Published private(set) var activeEpisodeID: Int?
    @Published private(set) var activeLineID: Int?
    @Published private(set) var skipSettings = PlaybackSkipSettings()
    private var resumeSeekPending = false
    private var skipIntroApplied = false
    private var skipEndApplied = false
    private var skipKey: String? {
        guard let source, let video, !isLive else { return nil }
        return "CatTV.skip." + LibraryIdentity.video(source: source, videoID: video.id)
    }
    func setSkip(intro: Double, outro: Double) {
        guard let skipKey else { return }
        skipSettings = PlaybackSkipSettings(intro: intro, outro: outro)
        do { UserDefaults.standard.set(try JSONEncoder().encode(skipSettings), forKey: skipKey) }
        catch { failure = error.localizedDescription }
    }
    private func loadSkipSettings() {
        skipSettings = PlaybackSkipSettings()
        if let skipKey, let data = UserDefaults.standard.data(forKey: skipKey) {
            do {
                let saved = try JSONDecoder().decode(PlaybackSkipSettings.self, from: data)
                skipSettings = PlaybackSkipSettings(intro: saved.intro, outro: saved.outro)
            } catch { failure = "跳过设置读取失败：\(error.localizedDescription)" }
        }
    }
    private let resolver = PlaybackResolver()
    private var proxy: MediaHTTPProxy?
    private var onProgress: ((Episode, Double) -> Void)?
    private var resume: ((Episode) -> Double)?
    private var video: Video?
    private var source: SourceSite?
    private var selectedParser: ParserEndpoint?
    private var line: PlaybackLine?
    private var task: Task<Void, Never>?
    private var token = UUID()
    private var timeObserver: Any?
    private var observation: NSKeyValueObservation?
    private var rateObservation: NSKeyValueObservation?
    private var notifications: [NSObjectProtocol] = []
    private var remoteTargets: [(MPRemoteCommand, Any)] = []
    private var timer: Task<Void, Never>?
    private var currentEpisode: Episode?
    private var interruptedPlaying = false
    init() {
        let center = NotificationCenter.default
        notifications.append(center.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main) { [weak self] notification in
            guard let item = notification.object as? AVPlayerItem else { return }
            Task { @MainActor [weak self] in
                guard let self, item === self.player.currentItem else { return }
                guard !self.skipEndApplied else { return }
                self.skipEndApplied = true
                self.advance(1)
            }
        })
        notifications.append(center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] notification in
            let type = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
            let options = notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
            Task { @MainActor [weak self] in self?.interruption(type: type, options: options) }
        })
        notifications.append(center.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] notification in
            let reason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt
            Task { @MainActor [weak self] in
                if reason == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue { self?.player.pause() }
            }
        })
        rateObservation = player.observe(\.timeControlStatus, options: [.new]) { [weak self] player, _ in
            let playing = player.timeControlStatus == .playing
            Task { @MainActor [weak self] in self?.isPlaying = playing; self?.updateNowPlaying() }
        }
        installRemoteCommands()
    }
    func start(video: Video, source: SourceSite, line: PlaybackLine, episode: Episode, parser: ParserEndpoint? = nil, isLive: Bool = false, resume: @escaping (Episode) -> Double, progress: @escaping (Episode, Double) -> Void) {
        saveProgress()
        player.pause()
        if let timeObserver { player.removeTimeObserver(timeObserver); self.timeObserver = nil }
        player.replaceCurrentItem(with: nil)
        observation = nil
        currentEpisode = nil
        self.video = video; self.source = source; self.line = line
        selectedParser = parser
        self.isLive = isLive
        loadSkipSettings()
        self.resume = resume; onProgress = progress
        title = video.name
        activeLineID = line.id
        clearSubtitles()
        open(episode)
    }
    private func open(_ episode: Episode) {
        if currentEpisode?.id != episode.id { clearSubtitles() }
        task?.cancel()
        token = UUID()
        let expected = token
        resolving = true
        failure = nil
        guard let source, let line else { resolving = false; return }
        saveProgress()
        player.pause()
        let useLANProxy = allowLANProxy
        task = Task {
            do {
                var resource = try await resolver.resolveResource(site: source, episode: episode, line: line.name, parser: selectedParser)
                if resource.requiresWebParsing { resource = try await sniffer.sniff(resource, timeout: source.timeout ?? 30) }
                guard !Task.isCancelled, token == expected else { return }
                let nextProxy: MediaHTTPProxy?
                let url: URL
                if resource.headers.isEmpty { nextProxy = nil; url = resource.url }
                else {
                    let created = MediaHTTPProxy(origin: resource.url, headers: resource.headers, exposure: useLANProxy ? .localNetwork : .loopback)
                    do { url = try await created.start() }
                    catch { await created.stop(); throw error }
                    nextProxy = created
                }
                guard !Task.isCancelled, token == expected else { await nextProxy?.stop(); return }
                if nextProxy != nil && useLANProxy && !allowLANProxy { await nextProxy?.stop(); throw CancellationError() }
                do { try play(url: url, episode: episode, position: resume?(episode) ?? 0) }
                catch { await nextProxy?.stop(); throw error }
                usingLANProxy = nextProxy != nil && useLANProxy
                player.allowsExternalPlayback = nextProxy == nil || useLANProxy
                let previous = proxy
                proxy = nextProxy
                await previous?.stop()
            } catch is CancellationError {} catch {
                if !Task.isCancelled, token == expected { failure = error.localizedDescription }
            }
            if token == expected { resolving = false }
        }
    }
    private func play(url: URL, episode: Episode, position: Double) throws {
        try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try AVAudioSession.sharedInstance().setActive(true)
        if let timeObserver { player.removeTimeObserver(timeObserver); self.timeObserver = nil }
        let item = AVPlayerItem(url: url)
        observation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            if item.status == .failed {
                let message = item.error?.localizedDescription ?? "播放失败"
                Task { @MainActor [weak self] in self?.failure = message }
            }
        }
        skipIntroApplied = false; skipEndApplied = false
        elapsedSeconds = 0; lastProgressSave = 0
        currentEpisode = episode
        activeEpisodeID = episode.id
        episodeName = episode.name
        player.allowsExternalPlayback = true
        player.replaceCurrentItem(with: item)
        resumeSeekPending = position.isFinite && position > 0
        if resumeSeekPending {
            player.seek(to: CMTime(seconds: position, preferredTimescale: 600)) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, self.player.currentItem === item else { return }
                    self.resumeSeekPending = false
                }
            }
        }
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 1, preferredTimescale: 1), queue: .main) { [weak self] time in
            Task { @MainActor [weak self] in
                guard let self, self.player.currentItem === item else { return }
                if time.seconds.isFinite {
                    self.elapsedSeconds = time.seconds
                    if !self.isLive, !self.resolving, !self.resumeSeekPending, self.player.timeControlStatus == .playing {
                        let duration = item.duration.seconds
                        if !self.skipIntroApplied, duration.isFinite, duration > 0 {
                            self.skipIntroApplied = true
                            if let target = self.skipSettings.introTarget(position: time.seconds, duration: duration) {
                                self.player.seek(to: CMTime(seconds: target, preferredTimescale: 600))
                            }
                        }
                        if !self.skipEndApplied, self.skipSettings.shouldAdvance(position: time.seconds, duration: duration) {
                            self.skipEndApplied = true
                            self.advance(1)
                            return
                        }
                    }
                    if abs(time.seconds - self.lastProgressSave) >= 5 {
                        self.onProgress?(episode, time.seconds); self.lastProgressSave = time.seconds
                    }
                }
                self.updateNowPlaying()
            }
        }
        player.play()
        updateNowPlaying()
    }
    func advance(_ direction: Int) {
        guard !isLive else { return }
        guard let line, let currentEpisode, let index = line.episodes.firstIndex(where: { $0.id == currentEpisode.id }) else { return }
        let next = index + direction
        guard line.episodes.indices.contains(next) else { player.pause(); saveProgress(); return }
        open(line.episodes[next])
    }
    func toggle() {
        guard player.currentItem != nil else { return }
        if player.rate == 0 { player.play() } else { player.pause(); saveProgress() }
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
        let seconds = player.currentTime().seconds
        if let currentEpisode, player.currentItem != nil, seconds.isFinite { onProgress?(currentEpisode, seconds) }
    }
    func setLANProxy(_ enabled: Bool) {
        allowLANProxy = enabled
        UserDefaults.standard.set(enabled, forKey: "CatTV.allowLANProxy")
        if !enabled && usingLANProxy { stop() }
    }
    func stop() {
        clearSubtitles()
        usingLANProxy = false
        saveProgress()
        task?.cancel(); token = UUID(); resolving = false
        sniffer.cancel()
        let previousProxy = proxy; proxy = nil
        Task { await previousProxy?.stop() }
        if let timeObserver { player.removeTimeObserver(timeObserver); self.timeObserver = nil }
        timer?.cancel(); timer = nil
        player.pause(); player.replaceCurrentItem(with: nil)
        observation = nil; onProgress = nil; resume = nil
        video = nil; source = nil; line = nil; currentEpisode = nil
        title = ""; episodeName = ""; activeEpisodeID = nil; activeLineID = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        do { try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation) }
        catch { failure = error.localizedDescription }
    }
    private func updateNowPlaying() {
        guard player.currentItem != nil else { return }
        var info: [String: Any] = [MPMediaItemPropertyTitle: title, MPMediaItemPropertyAlbumTitle: episodeName, MPNowPlayingInfoPropertyPlaybackRate: player.rate]
        if #available(iOS 18.0, *) { info[MPNowPlayingInfoPropertyExcludeFromSuggestions] = true }
        let elapsed = player.currentTime().seconds
        let duration = player.currentItem?.duration.seconds ?? .nan
        if elapsed.isFinite { info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsed }
        if duration.isFinite { info[MPMediaItemPropertyPlaybackDuration] = duration }
        info[MPNowPlayingInfoPropertyIsLiveStream] = isLive
        let commands = MPRemoteCommandCenter.shared()
        commands.nextTrackCommand.isEnabled = !isLive
        commands.previousTrackCommand.isEnabled = !isLive
        commands.changePlaybackPositionCommand.isEnabled = !isLive && duration.isFinite
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
    private func interruption(type: UInt?, options: UInt) {
        if type == AVAudioSession.InterruptionType.began.rawValue {
            interruptedPlaying = player.rate != 0
            player.pause(); saveProgress()
        } else if type == AVAudioSession.InterruptionType.ended.rawValue, interruptedPlaying, AVAudioSession.InterruptionOptions(rawValue: options).contains(.shouldResume) {
            do { try AVAudioSession.sharedInstance().setActive(true); player.play() }
            catch { failure = error.localizedDescription }
            interruptedPlaying = false
        }
    }
    private func installRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        func register(_ command: MPRemoteCommand, action: @escaping @MainActor (PlaybackModel) -> Void) {
            let target = command.addTarget { [weak self] _ in
                guard let self else { return .commandFailed }
                Task { @MainActor in action(self) }
                return .success
            }
            remoteTargets.append((command, target))
        }
        register(center.playCommand) { $0.player.play() }
        register(center.pauseCommand) { $0.player.pause(); $0.saveProgress() }
        register(center.togglePlayPauseCommand) { $0.toggle() }
        register(center.stopCommand) { $0.stop() }
        register(center.nextTrackCommand) { $0.advance(1) }
        register(center.previousTrackCommand) { $0.advance(-1) }
        let target = center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let self, let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            let position = event.positionTime
            Task { @MainActor in self.player.seek(to: CMTime(seconds: position, preferredTimescale: 600)); self.updateNowPlaying() }
            return .success
        }
        remoteTargets.append((center.changePlaybackPositionCommand, target))
    }
    deinit {
        task?.cancel(); timer?.cancel()
        for notification in notifications { NotificationCenter.default.removeObserver(notification) }
        for (command, target) in remoteTargets { command.removeTarget(target) }
    }
}
struct PlayerHost: UIViewControllerRepresentable {
    let player: AVPlayer
    var subtitles: [SubtitleCue] = []
    var subtitleOffset = 0.0
    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player
        controller.allowsPictureInPicturePlayback = true
        controller.canStartPictureInPictureAutomaticallyFromInline = true
        controller.updatesNowPlayingInfoCenter = false
        controller.loadViewIfNeeded()
        context.coordinator.attach(to: controller, player: player)
        return controller
    }
    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {
        if controller.player !== player { controller.player = player }
        context.coordinator.attach(to: controller, player: player)
        context.coordinator.cues = subtitles
        context.coordinator.offset = subtitleOffset
        context.coordinator.refresh()
    }
    static func dismantleUIViewController(_ controller: AVPlayerViewController, coordinator: Coordinator) { coordinator.detach() }
    final class Coordinator {
        let label = UILabel()
        var cues: [SubtitleCue] = []
        var offset = 0.0
        private var player: AVPlayer?
        private var observer: Any?
        func attach(to controller: AVPlayerViewController, player: AVPlayer) {
            if label.superview == nil, let overlay = controller.contentOverlayView {
                label.translatesAutoresizingMaskIntoConstraints = false
                label.numberOfLines = 0
                label.textAlignment = .center
                label.font = .boldSystemFont(ofSize: 18)
                label.textColor = .white
                label.layer.shadowColor = UIColor.black.cgColor
                label.layer.shadowOpacity = 1
                label.layer.shadowRadius = 3
                label.layer.shadowOffset = CGSize(width: 0, height: 1)
                label.isUserInteractionEnabled = false
                label.accessibilityIdentifier = "external-subtitle"
                overlay.addSubview(label)
                NSLayoutConstraint.activate([
                    label.leadingAnchor.constraint(equalTo: overlay.safeAreaLayoutGuide.leadingAnchor, constant: 20),
                    label.trailingAnchor.constraint(equalTo: overlay.safeAreaLayoutGuide.trailingAnchor, constant: -20),
                    label.bottomAnchor.constraint(equalTo: overlay.safeAreaLayoutGuide.bottomAnchor, constant: -48)
                ])
            }
            guard self.player !== player else { return }
            detachObserver()
            self.player = player
            observer = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.2, preferredTimescale: 600), queue: .main) { [weak self] _ in self?.refresh() }
        }
        func refresh() {
            let text = SubtitleDocument.text(at: player?.currentTime().seconds ?? 0, offset: offset, cues: cues)
            if label.text != text { label.text = text }
            label.isHidden = text.isEmpty
        }
        private func detachObserver() {
            if let observer { player?.removeTimeObserver(observer) }
            observer = nil
            player = nil
        }
        func detach() { detachObserver(); label.removeFromSuperview() }
        deinit { detachObserver() }
    }
}