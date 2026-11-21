import SwiftUI
import CatTVCore
import UniformTypeIdentifiers

@main
struct CatTVApp: App {
    @StateObject private var library = LibraryModel()
    @StateObject private var userLibrary = UserLibrary()
    @StateObject private var playback = PlaybackModel()
    @StateObject private var badges = BadgeStore()
    @StateObject private var chrome = NavigationChrome()
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(library).environmentObject(userLibrary).environmentObject(playback).environmentObject(badges).environmentObject(chrome)
                .onReceive(badges.$failure) { if let message = $0 { library.error = message } }
                .onReceive(userLibrary.$failure) { if let message = $0 { library.error = message } }
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
    @EnvironmentObject var chrome: NavigationChrome
    @State private var selectedTab = 0
    @State private var search = ""
    @State private var showSearch = false
    @State private var showLive = false
    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                ScrollView {
                    VStack(spacing: 12) {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack {
                                Button("首页") { library.load() }.buttonStyle(CatalogChipStyle(selected: library.query.category == nil))
                                ForEach(library.categories) { category in
                                    Button(category.name) { library.load(category: category.id) }.buttonStyle(CatalogChipStyle(selected: library.query.category == category.id))
                                }
                            }.padding(.horizontal, 16)
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
                        } else if library.configuration?.sites.isEmpty == true {
                            Text("当前配置为直播源，请打开直播").foregroundStyle(.secondary).padding(24)
                        } else if !library.busy && library.videos.isEmpty {
                            Text("暂无内容，请切换分类或影视源").foregroundStyle(.secondary).padding(24)
                        }
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: 3), spacing: 16) {
                            ForEach(library.videos) { video in
                                CatalogTile(video: video, source: library.site)
                            }
                        }.padding(.horizontal, 16)
                        if library.canLoadMore { Button("加载更多") { library.loadMore() }.disabled(library.busy).padding() }
                        Color.clear.frame(height: 100)
                    }
                }.navigationTitle("").navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) { sourceSelector }
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button { search = ""; showSearch = true } label: { Image(systemName: "magnifyingglass").font(.system(size: 20)) }.accessibilityLabel("搜索")
                        }
                    }
                    .sheet(isPresented: $showSearch) { GlobalSearchView(initialQuery: search, sites: library.configuration?.sites ?? []) }
                    .refreshable { library.load() }
                    .task { library.load() }
            }.tabItem { Label("首页", image: "ic_tab_home") }.tag(0).toolbar(.hidden, for: .tabBar)
            NavigationStack { SavedLibraryView(following: false) }.tabItem { Label("记录", image: "ic_tab_records") }.tag(1).toolbar(.hidden, for: .tabBar)
            NavigationStack { SavedLibraryView(following: true) }.tabItem { Label("追剧", image: "ic_tab_following") }.tag(2).toolbar(.hidden, for: .tabBar)
            NavigationStack { ConfigurationView() }.tabItem { Label("我的", image: "ic_tab_mine") }.tag(3).toolbar(.hidden, for: .tabBar)
        }.overlay(alignment: .bottom) {
            if !chrome.detailVisible { FloatingNavigation(selection: $selectedTab) { showLive = true } }
        }.sheet(isPresented: $showLive) { LiveView() }
    }
    private var sourceSelector: some View {
        Group {
            if let configuration = library.configuration, !configuration.sites.isEmpty {
                Menu { ForEach(configuration.sites) { site in Button(site.name) { library.select(site) } } } label: { sourceLabel(library.site?.name ?? "选择影视源") }
            } else { Button { selectedTab = 3 } label: { sourceLabel("配置中心") } }
        }
    }
    private func sourceLabel(_ name: String) -> some View {
        HStack(spacing: 7) {
            Image("cattv_home_logo").resizable().renderingMode(.original).scaledToFit().frame(width: 26, height: 26)
            Text(name).font(.system(size: 14, weight: .medium)).lineLimit(1)
            Image(systemName: "chevron.down").font(.system(size: 9, weight: .semibold))
        }.padding(.horizontal, 10).padding(.vertical, 6).background { CatGlassSurface(shape: Capsule()) }.foregroundStyle(.primary)
    }
}
struct ConfigurationView: View {
    @AppStorage("CatTV.epgURL") private var epgURL = ""
    @EnvironmentObject var library: LibraryModel
    @EnvironmentObject var playback: PlaybackModel
    @State private var editor: ConfigurationDocument?
    @State private var showEditor = false
    @State private var showScanner = false
    @State private var showQRCode = false
    @State private var showFileImporter = false
    @FocusState private var addressFocused: Bool
    var body: some View {
        Form {
            Section("配置中心") {
                TextField("配置地址", text: $library.configurationURL).focused($addressFocused).keyboardType(.URL)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                Button("导入配置") { addressFocused = false; Task { await library.importConfiguration() } }.disabled(library.importing)
                Button("手动新增") { editor = nil; showEditor = true }
                Button("导入配置文件") { addressFocused = false; showFileImporter = true }
                Button("扫描二维码") { showScanner = true }
                if library.collection.active?.origin != nil { Button("配置二维码") { showQRCode = true } }
            }
            Section("已保存配置") {
                ForEach(library.collection.documents) { document in
                    HStack {
                        Button(document.name) { library.activate(document.id) }
                        if document.id == library.collection.activeID { Image(systemName: "checkmark").accessibilityLabel("当前配置").accessibilityIdentifier("active-configuration") }
                        Spacer()
                        Button("编辑") { editor = document; showEditor = true }
                    }.swipeActions { Button("删除", role: .destructive) { library.removeConfiguration(document.id) } }
                }
            }
            Section("AirPlay") {
                Toggle("允许局域网媒体代理", isOn: Binding(get: { playback.allowLANProxy }, set: { playback.setLANProxy($0) }))
                Text("用于让同一网络的 AirPlay 设备访问需请求头的媒体。开启后重新播放生效；关闭会停止当前局域网代理播放。").font(.caption).foregroundStyle(.secondary)
            }
            Section("偏好设置") {
                TextField("节目单地址", text: $epgURL).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                NavigationLink("媒体徽章") { BadgeSettingsView() }
                NavigationLink("缓存管理") { CacheSettingsView() }
                NavigationLink("关于 CatTV") { AboutView() }
            }
            Section("影视源") {
                ForEach(library.configuration?.sites ?? []) { site in
                    Button { library.select(site) } label: {
                        HStack { Text(site.name); Spacer(); if site.id == library.site?.id { Image(systemName: "checkmark") } }
                    }
                }
            }
            Color.clear.frame(height: 100).listRowBackground(Color.clear).listRowSeparator(.hidden)
        }.navigationTitle("设置").navigationBarTitleDisplayMode(.inline)
            .fileImporter(isPresented: $showFileImporter, allowedContentTypes: [.json, .plainText, .data]) { result in
                switch result {
                case .success(let url): Task { await library.importFile(url) }
                case .failure(let error): library.error = error.localizedDescription
                }
            }
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
    @State private var selectedParser: ParserEndpoint?
    @State private var showMediaTracks = false
    @EnvironmentObject var chrome: NavigationChrome
    @State private var navigationID = UUID()
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                PlayerHost(player: playback.player).frame(height: 220).background(.black)
                if playback.resolving { ProgressView("正在解析播放地址") }
                Text("播放进度：\(Int(playback.elapsedSeconds))秒").font(.caption).foregroundStyle(.secondary).accessibilityIdentifier("playback-progress")
                VStack(alignment: .leading, spacing: 8) {
                    Text((video ?? summary).name).font(.title3.bold())
                    let item = video ?? summary
                    Text([item.year, item.area, item.language, item.genre].filter { !$0.isEmpty && $0 != "0" }.joined(separator: " · ")).font(.subheadline).foregroundStyle(.secondary)
                    if let source { Text(source.name).font(.caption).padding(.horizontal, 10).padding(.vertical, 4).background(Color.secondary.opacity(0.15), in: Capsule()) }
                    Text(item.remarks).foregroundStyle(.secondary)
                    if !item.director.isEmpty { Text("导演：" + item.director).font(.subheadline) }
                    if !item.actors.isEmpty { Text("演员：" + item.actors).font(.subheadline) }
                    MediaBadgeRow(text: (video ?? summary).name + " " + (video ?? summary).remarks, player: true)
                    Text(HTMLSummary.plainText((video ?? summary).content)).font(.subheadline).textSelection(.enabled)
                }.padding(16).frame(maxWidth: .infinity, alignment: .leading).background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16))
                if let video {
                    if let source {
                        Button(userLibrary.isFollowing(source: source, video: video) ? "取消追剧" : "追剧") { userLibrary.toggleFollowing(source: source, video: video) }
                    }
                    if let parsers = source?.parsers, !parsers.isEmpty {
                        Menu(selectedParser?.name ?? "自动解析") {
                            Button("自动解析") { selectedParser = nil }
                            ForEach(parsers) { parser in Button(parser.name) { selectedParser = parser } }
                        }
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
                                    playback.start(video: video, source: source, line: line, episode: episode, parser: selectedParser) { item in
                                        userLibrary.resumePosition(source: source, video: video, episode: item)
                                    } progress: { item, position in
                                        userLibrary.record(source: source, video: video, episode: item, position: position)
                                    }
                                }
                            }.buttonStyle(.bordered).accessibilityIdentifier("episode-\(episode.id)")
                        }
                    }
                    HStack {
                        Button("上一集") { playback.advance(-1) }
                        Button("下一集") { playback.advance(1) }
                        Button("停止播放") { playback.stop() }
                    }.buttonStyle(.bordered)
                    Button("音轨与字幕") { showMediaTracks = true }
                    Menu("定时退出播放") {
                        ForEach([15, 30, 60, 90], id: \.self) { minutes in
                            Button("\(minutes) 分钟") { playback.setStopTimer(minutes: minutes) }
                        }
                        Button("取消定时") { playback.setStopTimer(minutes: nil) }
                    }
                }
            }.padding(6)
        }.navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showMediaTracks) { MediaTracksView() }
            .onAppear { chrome.enter(navigationID) }
            .onDisappear { chrome.leave(navigationID) }
            .task {
                do { video = try await library.detail(summary, source: source) }
                catch is CancellationError {} catch { library.error = error.localizedDescription }
            }

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
            Color.clear.frame(height: 100).listRowBackground(Color.clear).listRowSeparator(.hidden)
        }.navigationTitle(following ? "追剧" : "记录").navigationBarTitleDisplayMode(.inline)
    }
}
