import SwiftUI
import CatTVCore

@MainActor
final class GlobalSearchModel: ObservableObject {
    @Published private(set) var results: [SearchSourceResult] = []
    @Published private(set) var busy = false
    @Published private(set) var history: [String] = UserDefaults.standard.stringArray(forKey: "CatTV.searchHistory") ?? []
    @Published private(set) var failure: String?
    @Published private(set) var lastQuery: String?
    private let search = CatalogSearch()
    private var task: Task<Void, Never>?
    private var generation = UUID()
    func start(sites: [SourceSite], query: String) {
        let query = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return }
        task?.cancel()
        generation = UUID()
        let expected = generation
        lastQuery = query
        results = []
        failure = nil
        history.removeAll { $0 == query }
        history.insert(query, at: 0)
        history = Array(history.prefix(30))
        UserDefaults.standard.set(history, forKey: "CatTV.searchHistory")
        busy = true
        task = Task {
            do {
                let response = try await search.search(sites: sites, keyword: query)
                if !Task.isCancelled && generation == expected { results = response }
            } catch is CancellationError {} catch {
                if generation == expected { failure = error.localizedDescription }
            }
            if generation == expected { busy = false }
        }
    }
    func clearHistory() { history = []; UserDefaults.standard.removeObject(forKey: "CatTV.searchHistory") }
    func cancel() { task?.cancel(); generation = UUID(); busy = false }
}
struct GlobalSearchView: View {
    let initialQuery: String
    let sites: [SourceSite]
    @StateObject private var model = GlobalSearchModel()
    @State private var query = ""
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                if model.busy { ProgressView("正在搜索影视源") }
                if let failure = model.failure { Text(failure).foregroundStyle(.secondary) }
                if sites.isEmpty { Text("请先导入影视源配置") }
                if query.isEmpty && !model.history.isEmpty {
                    Section("搜索历史") {
                        ForEach(model.history, id: \.self) { term in Button(term) { query = term; model.start(sites: sites, query: term) } }
                        Button("清空搜索历史", role: .destructive) { model.clearHistory() }
                    }
                }
                ForEach(model.results) { result in
                    Section(result.site.name) {
                        if let failure = result.failure { Text(failure).font(.caption).foregroundStyle(.secondary) }
                        else if result.videos.isEmpty { Text("无搜索结果").foregroundStyle(.secondary) }
                        ForEach(result.videos) { video in
                            NavigationLink { DetailView(summary: video, source: result.site) } label: {
                                HStack(spacing: 12) {
                                    AsyncImage(url: URL(string: video.poster, relativeTo: URL(string: result.site.api))?.absoluteURL) { image in image.resizable().scaledToFill() }
                                        placeholder: { Color.secondary.opacity(0.15) }
                                        .frame(width: 60, height: 90).clipped().clipShape(RoundedRectangle(cornerRadius: 10))
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text(video.name).lineLimit(2)
                                        Text(video.remarks).font(.caption).foregroundStyle(.secondary)
                                        MediaBadgeRow(text: video.name + " " + video.remarks)
                                    }
                                }
                            }
                        }
                    }
                }
            }.navigationTitle("搜索")
                .searchable(text: $query, prompt: "搜索影视")
                .onSubmit(of: .search) { model.start(sites: sites, query: query) }
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("关闭") { model.cancel(); dismiss() } } }
                .task { if model.lastQuery == nil { query = initialQuery; model.start(sites: sites, query: query) } }
                .onDisappear { model.cancel() }
        }
    }
}
