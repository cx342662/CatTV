import SwiftUI
import CatTVCore

@MainActor
final class BadgeStore: ObservableObject {
    struct Snapshot: Codable {
        var enabled = true
        var cards = true
        var player = true
        var followControls = true
        var selected = ""
        var packs: [String: Data] = [:]
    }
    @Published private(set) var state = Snapshot()
    @Published private(set) var loading = false
    @Published var failure: String?
    private var matcher: MediaBadgeMatcher?
    private let file = URL.applicationSupportDirectory.appendingPathComponent("CatTV/badges.json")
    init() {
        do {
            if FileManager.default.fileExists(atPath: file.path) { state = try JSONDecoder().decode(Snapshot.self, from: Data(contentsOf: file)) }
            matcher = try Self.matcher(for: state)
        } catch { failure = "徽章读取失败：\(error.localizedDescription)" }
    }
    private static func matcher(for state: Snapshot) throws -> MediaBadgeMatcher {
        let rules: [MediaBadgeRule]
        if state.selected.isEmpty { rules = MediaBadgeMatcher.builtInRules }
        else {
            guard let data = state.packs[state.selected] else { throw SourceError.invalidConfiguration("徽章包不存在") }
            rules = try MediaBadgeMatcher.parsePack(data)
        }
        return try MediaBadgeMatcher(rules: rules)
    }
    func matches(_ text: String, player: Bool = false) -> [MediaBadgeRule] {
        guard state.enabled, player ? state.player : state.cards else { return [] }
        return matcher?.match(text) ?? []
    }
    func update(_ change: (inout Snapshot) -> Void) {
        do {
            var next = state
            change(&next)
            let compiled = try Self.matcher(for: next)
            let data = try JSONEncoder().encode(next)
            try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
            try data.write(to: file, options: .atomic)
            matcher = compiled
            state = next
        } catch { failure = error.localizedDescription }
    }
    func importPack(_ address: String) async {
        guard !loading else { return }
        guard let url = URL(string: address.trimmingCharacters(in: .whitespacesAndNewlines)), url.scheme == "https" else { failure = "徽章包需要 HTTPS 地址"; return }
        loading = true
        defer { loading = false }
        do {
            let (bytes, response) = try await URLSession.shared.bytes(from: url)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw SourceError.invalidConfiguration("徽章包下载失败") }
            var data = Data()
            for try await byte in bytes {
                guard data.count < 2_000_000 else { throw SourceError.invalidConfiguration("徽章包超过 2 MB") }
                data.append(byte)
            }
            _ = try MediaBadgeMatcher.parsePack(data)
            update { $0.packs[url.absoluteString] = data; $0.selected = url.absoluteString }
        } catch is CancellationError {} catch { failure = error.localizedDescription }
    }
}
struct MediaBadgeRow: View {
    let text: String
    var player = false
    @EnvironmentObject var badges: BadgeStore
    var body: some View {
        let matched = badges.matches(text, player: player)
        if !matched.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 5) {
                    ForEach(matched) { badge in
                        if let image = badge.image {
                            AsyncImage(url: URL(string: image)) { phase in
                                if let image = phase.image { image.resizable().scaledToFit().frame(maxWidth: 84, maxHeight: 22) }
                                else { label(badge.label) }
                            }
                        } else { label(badge.label) }
                    }
                }
            }
        }
    }
    private func label(_ text: String) -> some View {
        Text(text).font(.system(size: 10, weight: .semibold)).padding(.horizontal, 5).padding(.vertical, 3)
            .background(Color.secondary.opacity(0.15), in: RoundedRectangle(cornerRadius: 3))
    }
}
struct BadgeSettingsView: View {
    @EnvironmentObject var badges: BadgeStore
    @State private var address = ""
    var body: some View {
        Form {
            Toggle("启用媒体徽章", isOn: Binding(get: { badges.state.enabled }, set: { value in badges.update { $0.enabled = value } }))
            Toggle("海报卡片显示", isOn: Binding(get: { badges.state.cards }, set: { value in badges.update { $0.cards = value } }))
            Toggle("播放器显示", isOn: Binding(get: { badges.state.player }, set: { value in badges.update { $0.player = value } }))
            Section("徽章包") {
                TextField("HTTPS 徽章包地址", text: $address).textInputAutocapitalization(.never).autocorrectionDisabled()
                Button("导入") { Task { await badges.importPack(address) } }.disabled(badges.loading)
                Button("内置徽章") { badges.update { $0.selected = "" } }
                ForEach(badges.state.packs.keys.sorted(), id: \.self) { url in
                    Button { badges.update { $0.selected = url } } label: {
                        HStack { Text(url).lineLimit(2); if badges.state.selected == url { Image(systemName: "checkmark") } }
                    }.swipeActions { Button("删除", role: .destructive) { badges.update { $0.packs.removeValue(forKey: url); if $0.selected == url { $0.selected = "" } } } }
                }
            }
            Section("预览") { MediaBadgeRow(text: "2160p WEB-DL Dolby Vision HDR10+ HEVC Atmos 10bit") }
        }.navigationTitle("媒体徽章")
    }
}
