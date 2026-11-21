import SwiftUI
import AVFoundation

@MainActor
final class MediaTracksModel: ObservableObject {
    @Published private(set) var audio: [AVMediaSelectionOption] = []
    @Published private(set) var subtitles: [AVMediaSelectionOption] = []
    @Published private(set) var loading = false
    @Published private(set) var failure: String?
    @Published private(set) var revision = 0
    private var item: AVPlayerItem?
    private var audioGroup: AVMediaSelectionGroup?
    private var subtitleGroup: AVMediaSelectionGroup?
    func load(_ item: AVPlayerItem?) async {
        self.item = item
        audio = []; subtitles = []; audioGroup = nil; subtitleGroup = nil; failure = nil
        guard let item else { failure = "请先开始播放"; return }
        loading = true
        defer { loading = false }
        do {
            let audible = try await item.asset.loadMediaSelectionGroup(for: .audible)
            let legible = try await item.asset.loadMediaSelectionGroup(for: .legible)
            try Task.checkCancellation()
            guard self.item === item else { return }
            audioGroup = audible; subtitleGroup = legible
            audio = audible?.options ?? []; subtitles = legible?.options ?? []
        } catch is CancellationError {} catch { failure = error.localizedDescription }
    }
    func selected(_ option: AVMediaSelectionOption, subtitle: Bool) -> Bool {
        guard let group = subtitle ? subtitleGroup : audioGroup else { return false }
        return item?.currentMediaSelection.selectedMediaOption(in: group) === option
    }
    func select(_ option: AVMediaSelectionOption, subtitle: Bool) {
        guard let group = subtitle ? subtitleGroup : audioGroup, group.options.contains(where: { $0 === option }) else { return }
        item?.select(option, in: group); revision += 1
    }
    func automatic(subtitle: Bool) {
        guard let group = subtitle ? subtitleGroup : audioGroup else { return }
        item?.selectMediaOptionAutomatically(in: group); revision += 1
    }
    var canDisableSubtitles: Bool { subtitleGroup?.allowsEmptySelection == true }
    func disableSubtitles() {
        guard let group = subtitleGroup, group.allowsEmptySelection else { return }
        item?.select(nil, in: group); revision += 1
    }
}
struct MediaTracksView: View {
    @EnvironmentObject var playback: PlaybackModel
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = MediaTracksModel()
    var body: some View {
        NavigationStack {
            List {
                if model.loading { ProgressView("正在读取媒体轨道") }
                if let failure = model.failure { Text(failure).foregroundStyle(.secondary) }
                PlaybackSkipView()
                ExternalSubtitleSettings()
                Section("音轨") {
                    if model.audio.isEmpty && !model.loading { Text("当前媒体没有可选音轨").foregroundStyle(.secondary) }
                    if !model.audio.isEmpty { Button("自动选择音轨") { model.automatic(subtitle: false) } }
                    ForEach(model.audio.indices, id: \.self) { index in
                        option(model.audio[index], subtitle: false)
                    }
                }
                Section("内嵌字幕") {
                    if model.subtitles.isEmpty && !model.loading { Text("当前媒体没有可选内嵌字幕").foregroundStyle(.secondary) }
                    if !model.subtitles.isEmpty { Button("自动选择字幕") { model.automatic(subtitle: true) } }
                    if model.canDisableSubtitles { Button("关闭字幕") { model.disableSubtitles() } }
                    ForEach(model.subtitles.indices, id: \.self) { index in
                        option(model.subtitles[index], subtitle: true)
                    }
                }
            }.navigationTitle("播放设置").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("关闭") { dismiss() } } }
                .task(id: playback.player.currentItem.map(ObjectIdentifier.init)) { await model.load(playback.player.currentItem) }
        }
    }
    private func option(_ option: AVMediaSelectionOption, subtitle: Bool) -> some View {
        Button { model.select(option, subtitle: subtitle) } label: {
            HStack {
                Text(option.displayName)
                Spacer()
                if model.selected(option, subtitle: subtitle) { Image(systemName: "checkmark") }
            }
        }
    }
}
