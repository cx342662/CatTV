import SwiftUI
import CatTVCore

@MainActor
final class LiveViewModel: ObservableObject {
    @Published private(set) var sources: [LiveSource] = []
    @Published private(set) var selected: LiveSource?
    @Published private(set) var channels: [LiveChannel] = []
    @Published private(set) var loading = false
    @Published private(set) var failure: String?
    private let repository = LiveRepository()
    private var task: Task<Void, Never>?
    private var generation = UUID()
    private var initialized = false
    func configure(_ configuration: SourceConfiguration?, origin: URL?) {
        guard !initialized else { return }
        initialized = true
        let definitions = configuration?.lives ?? []
        sources = LiveCatalog.sources(definitions, baseURL: origin)
        if sources.isEmpty && !definitions.isEmpty { failure = "配置中的直播定义暂未识别" }
        let saved = UserDefaults.standard.string(forKey: "CatTV.lastLiveSource")
        if let source = sources.first(where: { $0.id == saved }) ?? sources.first { select(source) }
    }
    func select(_ source: LiveSource) {
        task?.cancel(); generation = UUID()
        let expected = generation
        selected = source; channels = []; failure = nil; loading = true
        UserDefaults.standard.set(source.id, forKey: "CatTV.lastLiveSource")
        task = Task {
            do {
                let result = try await repository.channels(source: source)
                if !Task.isCancelled && generation == expected { channels = result }
            } catch is CancellationError {} catch {
                if !Task.isCancelled && generation == expected { failure = error.localizedDescription }
            }
            if generation == expected { loading = false }
        }
    }
    func cancel() { task?.cancel(); generation = UUID(); loading = false }
}
struct LiveView: View {
    @EnvironmentObject var library: LibraryModel
    @StateObject private var model = LiveViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var search = ""
    @State private var channel: LiveChannel?
    private var filtered: [LiveChannel] {
        model.channels.filter { search.isEmpty || $0.name.localizedCaseInsensitiveContains(search) || $0.group.localizedCaseInsensitiveContains(search) }
    }
    private var groups: [String] { filtered.reduce(into: []) { if !$0.contains($1.group) { $0.append($1.group) } } }
    var body: some View {
        NavigationStack {
            List {
                if !model.sources.isEmpty {
                    Menu(model.selected?.name ?? "选择直播源") { ForEach(model.sources) { source in Button(source.name) { model.select(source) } } }
                }
                if model.loading { ProgressView("正在加载直播列表") }
                if let failure = model.failure {
                    Text(failure).foregroundStyle(.secondary)
                    if let selected = model.selected { Button("重试") { model.select(selected) } }
                }
                if model.sources.isEmpty && model.failure == nil { Text("当前配置没有直播源").foregroundStyle(.secondary) }
                ForEach(groups, id: \.self) { group in
                    Section(group) {
                        ForEach(filtered.filter { $0.group == group }) { item in
                            Button { channel = item } label: {
                                HStack(spacing: 12) {
                                    if let logo = item.logo { AsyncImage(url: URL(string: logo)) { image in image.resizable().scaledToFit() } placeholder: { Color.clear }.frame(width: 44, height: 44) }
                                    Text(item.name).foregroundStyle(.primary)
                                }
                            }
                        }
                    }
                }
            }.navigationTitle("直播").navigationBarTitleDisplayMode(.inline)
                .searchable(text: $search, prompt: "搜索频道")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("关闭") { model.cancel(); dismiss() } } }
                .task { model.configure(library.configuration, origin: library.collection.active?.origin) }
                .sheet(item: $channel) { item in
                    LivePlayerView(channel: item, previous: { move(-1) }, next: { move(1) })
                }
        }
    }
    private func move(_ direction: Int) {
        guard let channel, !model.channels.isEmpty, let index = model.channels.firstIndex(where: { $0.id == channel.id }) else { return }
        self.channel = model.channels[(index + direction + model.channels.count) % model.channels.count]
    }
}
private struct LivePlayerView: View {
    let channel: LiveChannel
    let previous: () -> Void
    let next: () -> Void
    @EnvironmentObject var playback: PlaybackModel
    @EnvironmentObject var library: LibraryModel
    @Environment(\.dismiss) private var dismiss
    @State private var stream = 0
    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                PlayerHost(player: playback.player).frame(height: 240).background(.black)
                Text(channel.name).font(.title3.bold())
                Menu("线路 \(stream + 1)") {
                    ForEach(channel.streams.indices, id: \.self) { index in Button("线路 \(index + 1)") { stream = index; play() } }
                }
                HStack(spacing: 24) { Button("上一频道", action: previous); Button("下一频道", action: next); Button("停止播放") { playback.stop() } }
                if playback.resolving { ProgressView("正在准备直播") }
                Spacer()
            }.padding(.horizontal, 6).navigationTitle("直播").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("返回频道") { dismiss() } } }
                .task(id: channel.id) { stream = 0; play() }
        }
    }
    private func play() {
        do {
            let (video, source) = try channel.playback(stream: stream)
            guard let line = video.lines.first, let episode = line.episodes.first else { throw SourceError.invalidConfiguration("直播地址缺失") }
            playback.start(video: video, source: source, line: line, episode: episode, isLive: true, resume: { _ in 0 }, progress: { _, _ in })
        } catch { library.error = error.localizedDescription }
    }
}
