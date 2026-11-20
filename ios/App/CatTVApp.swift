import SwiftUI
import CatTVCore

@main
struct CatTVApp: App {
    @StateObject private var library = LibraryModel()
    @StateObject private var userLibrary = UserLibrary()
    @StateObject private var playback = PlaybackModel()
    @StateObject private var badges = BadgeStore()
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(library).environmentObject(userLibrary).environmentObject(playback).environmentObject(badges)
                .onReceive(badges.$failure) { if let message = $0 { library.error = message } }
                .background(WebSnifferHost(sniffer: playback.sniffer).frame(width: 1, height: 1).opacity(0.001).allowsHitTesting(false).accessibilityHidden(true))
                .onReceive(playback.$failure) { if let message = $0 { library.error = message } }
                .alert("提示", isPresented: Binding(get: { library.error != nil }, set: { if !$0 { library.error = nil } })) {
                    Button("确定") { library.error = nil }
                } message: { Text(library.error ?? "") }
        }
    }
}
struct RootView: View {
    @EnvironmentObject var library: LibraryModel
    @State private var search = ""
    var body: some View {
        TabView {
            NavigationStack {
                ScrollView {
                    VStack(spacing: 12) {
                        if let config = library.configuration {
                            Menu {
                                ForEach(config.sites) { site in Button(site.name) { library.select(site) } }
                            } label: { Label(library.site?.name ?? "选择影视源", systemImage: "chevron.down") }
                        }
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack {
                                Button("首页") { library.load() }
                                ForEach(library.categories) { category in
                                    Button(category.name) { library.load(category: category.id) }
                                }
                            }.buttonStyle(.bordered).padding(.horizontal, 16)
                        }
                        if let category = library.query.category {
                            ForEach(library.filters[category] ?? []) { filter in
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack { Text(filter.name); ForEach(filter.value) { option in
                                        Button(option.n) { library.selectFilter(key: filter.key, value: option.v) }
                                            .buttonStyle(.bordered).tint(library.query.filters[filter.key] == option.v ? .accentColor : .secondary)
                                    } }.padding(.horizontal, 16)
                                }
                            }
                        }
                        if library.busy { ProgressView().padding() }
                        if library.configuration == nil {
                            Text("请在设置的配置中心导入你的影视源配置").foregroundStyle(.secondary).padding(24)
                        } else if !library.busy && library.videos.isEmpty {
                            Text("暂无内容，请切换分类或影视源").foregroundStyle(.secondary).padding(24)
                        }
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 16) {
                            ForEach(library.videos) { video in
                                NavigationLink { DetailView(summary: video, source: library.site) } label: {
                                    VStack(alignment: .leading, spacing: 5) {
                                        AsyncImage(url: URL(string: video.poster, relativeTo: library.site.flatMap { URL(string: $0.api) })?.absoluteURL) { image in
                                            image.resizable().scaledToFill()
                                        } placeholder: { Rectangle().fill(Color.secondary.opacity(0.15)).overlay(Image(systemName: "film")) }
                                        .aspectRatio(2 / 3, contentMode: .fit).clipped().clipShape(RoundedRectangle(cornerRadius: 16))
                                        Text(video.name).font(.system(size: 13)).lineLimit(1)
                                        MediaBadgeRow(text: video.name + " " + video.remarks)
                                        Text(video.remarks).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                                    }
                                }.buttonStyle(.plain)
                            }
                        }.padding(.horizontal, 16)
                        if library.canLoadMore { Button("加载更多") { library.loadMore() }.disabled(library.busy).padding() }
                    }
                }.navigationTitle("CatTV").navigationBarTitleDisplayMode(.inline)
                    .searchable(text: $search, prompt: "搜索影视")
                    .onSubmit(of: .search) { library.load(search: search) }
                    .refreshable { library.load() }
                    .task { library.load() }
            }.tabItem { Label("首页", image: "ic_tab_home") }
            NavigationStack { SavedLibraryView(following: false) }.tabItem { Label("记录", image: "ic_tab_records") }
            NavigationStack { SavedLibraryView(following: true) }.tabItem { Label("追剧", image: "ic_tab_following") }
            NavigationStack { ConfigurationView() }.tabItem { Label("我的", image: "ic_tab_mine") }
        }
    }
}
struct ConfigurationView: View {
    @EnvironmentObject var library: LibraryModel
    @State private var editor: ConfigurationDocument?
    @State private var showEditor = false
    @State private var showScanner = false
    @State private var showQRCode = false
    var body: some View {
        Form {
            Section("配置中心") {
                TextField("配置地址", text: $library.configurationURL).keyboardType(.URL)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                Button("导入配置") { Task { await library.importConfiguration() } }.disabled(library.importing)
                Button("手动新增") { editor = nil; showEditor = true }
                Button("扫描二维码") { showScanner = true }
                if library.collection.active?.origin != nil { Button("配置二维码") { showQRCode = true } }
            }
            Section("已保存配置") {
                ForEach(library.collection.documents) { document in
                    HStack {
                        Button(document.name) { library.activate(document.id) }
                        if document.id == library.collection.activeID { Image(systemName: "checkmark") }
                        Spacer()
                        Button("编辑") { editor = document; showEditor = true }
                    }.swipeActions { Button("删除", role: .destructive) { library.removeConfiguration(document.id) } }
                }
            }
            Section("偏好设置") {
                NavigationLink("媒体徽章") { BadgeSettingsView() }
                NavigationLink("缓存管理") { CacheSettingsView() }
            }
            Section("影视源") {
                ForEach(library.configuration?.sites ?? []) { site in
                    Button { library.select(site) } label: {
                        HStack { Text(site.name); Spacer(); if site.id == library.site?.id { Image(systemName: "checkmark") } }
                    }
                }
            }
        }.navigationTitle("设置")
            .sheet(isPresented: $showEditor) { ConfigurationEditor(document: editor) }
            .sheet(isPresented: $showScanner) { QRScannerSheet { library.configurationURL = $0 } }
            .sheet(isPresented: $showQRCode) { ConfigurationQRCode(text: library.collection.active?.origin?.absoluteString ?? "") }
    }
}
struct DetailView: View {
    let summary: Video
    let source: SourceSite?
    @EnvironmentObject var userLibrary: UserLibrary
    @EnvironmentObject var library: LibraryModel
    @EnvironmentObject var playback: PlaybackModel
    @State private var video: Video?
    @State private var lineIndex = 0
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                PlayerHost(player: playback.player).frame(height: 220).background(.black)
                if playback.resolving { ProgressView("正在解析播放地址") }
                VStack(alignment: .leading, spacing: 8) {
                    Text((video ?? summary).name).font(.title3.bold())
                    Text((video ?? summary).remarks).foregroundStyle(.secondary)
                    MediaBadgeRow(text: (video ?? summary).name + " " + (video ?? summary).remarks, player: true)
                    Text((video ?? summary).content).font(.subheadline).textSelection(.enabled)
                }.padding(16).frame(maxWidth: .infinity, alignment: .leading).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
                if let video {
                    if let source {
                        Button(userLibrary.isFollowing(source: source, video: video) ? "取消追剧" : "追剧") { userLibrary.toggleFollowing(source: source, video: video) }
                    }
                    Text("线路").font(.headline)
                    ScrollView(.horizontal) {
                        HStack {
                            ForEach(video.lines) { line in
                                Button(line.name) { lineIndex = line.id }.buttonStyle(.bordered)
                            }
                        }
                    }
                    Text("选集").font(.headline)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 85))]) {
                        ForEach(video.lines.first(where: { $0.id == lineIndex })?.episodes ?? []) { episode in
                            Button(episode.name) {
                                if let source, let line = video.lines.first(where: { $0.id == lineIndex }) {
                                    playback.start(video: video, source: source, line: line, episode: episode) { item in
                                        userLibrary.resumePosition(source: source, video: video, episode: item)
                                    } progress: { item, position in
                                        userLibrary.record(source: source, video: video, episode: item, position: position)
                                    }
                                }
                            }.buttonStyle(.bordered)
                        }
                    }
                    HStack {
                        Button("上一集") { playback.advance(-1) }
                        Button("下一集") { playback.advance(1) }
                        Button("停止播放") { playback.stop() }
                    }.buttonStyle(.bordered)
                    Menu("定时退出播放") {
                        ForEach([15, 30, 60, 90], id: \.self) { minutes in
                            Button("\(minutes) 分钟") { playback.setStopTimer(minutes: minutes) }
                        }
                        Button("取消定时") { playback.setStopTimer(minutes: nil) }
                    }
                }
            }.padding(6)
        }.navigationBarTitleDisplayMode(.inline)
            .task {
                do { video = try await library.detail(summary, source: source) }
                catch is CancellationError {} catch { library.error = error.localizedDescription }
            }
            .onReceive(userLibrary.$failure) { if let message = $0 { library.error = message } }

    }
}

struct SavedLibraryView: View {
    let following: Bool
    @EnvironmentObject var userLibrary: UserLibrary
    var body: some View {
        List {
            let entries = following ? userLibrary.following : userLibrary.history
            if entries.isEmpty { Text(following ? "暂无追剧" : "暂无播放记录").foregroundStyle(.secondary) }
            ForEach(entries) { entry in
                NavigationLink {
                    DetailView(summary: entry.video, source: entry.source)
                } label: {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(entry.video.name)
                        Text(entry.source.name).font(.caption).foregroundStyle(.secondary)
                        if !following { Text("播放至 \(Int(entry.position)) 秒").font(.caption).foregroundStyle(.secondary) }
                    }
                }
            }.onDelete { offsets in
                if following {
                    for offset in offsets { let entry = entries[offset]; userLibrary.toggleFollowing(source: entry.source, video: entry.video) }
                } else { userLibrary.removeHistory(at: offsets) }
            }
        }.navigationTitle(following ? "追剧" : "记录")
    }
}
