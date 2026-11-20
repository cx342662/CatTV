import SwiftUI
import CatTVCore

@MainActor
final class LibraryModel: ObservableObject {
    @Published private(set) var configuration: SourceConfiguration?
    @Published private(set) var site: SourceSite?
    @Published private(set) var videos: [Video] = []
    @Published private(set) var categories: [VideoCategory] = []
    @Published private(set) var filters: [String: [CatalogFilter]] = [:]
    @Published private(set) var query = CatalogQuery()
    @Published private(set) var canLoadMore = false
    @Published private(set) var busy = false
    @Published private(set) var importing = false
    @Published private(set) var collection = ConfigurationCollection()
    @Published var error: String?
    @Published var configurationURL = ""
    private let configurations = ConfigurationRepository()
    private let catalog = CatalogRepository()
    private var requestTask: Task<Void, Never>?
    private var requestID = UUID()
    private let storage = URL.applicationSupportDirectory.appendingPathComponent("CatTV/configurations.json")
    init() {
        do {
            if FileManager.default.fileExists(atPath: storage.path) {
                collection = try JSONDecoder().decode(ConfigurationCollection.self, from: Data(contentsOf: storage))
                try restoreActive()
            } else {
                let legacy = storage.deletingLastPathComponent().appendingPathComponent("configuration.json")
                if FileManager.default.fileExists(atPath: legacy.path) {
                    let document = try ConfigurationDocument(name: "已有配置", origin: nil, data: Data(contentsOf: legacy))
                    var migrated = collection
                    try migrated.upsert(document)
                    try persist(migrated)
                }
            }
        } catch { self.error = "本地配置读取失败：\(error.localizedDescription)" }
    }
    func importConfiguration() async {
        guard !importing else { return }
        guard let url = URL(string: configurationURL) else { error = SourceError.invalidURL.localizedDescription; return }
        importing = true
        defer { importing = false }
        do {
            let data = try await configurations.loadData(from: url)
            let existing = collection.documents.first { $0.origin == url }
            let document = try ConfigurationDocument(id: existing?.id ?? UUID(), name: existing?.name ?? (url.host ?? "配置"), origin: url, data: data)
            try save(document)
        } catch is CancellationError {} catch { self.error = error.localizedDescription }
    }
    func save(_ document: ConfigurationDocument) throws {
        var next = collection
        try next.upsert(document)
        try persist(next)
        resetAndLoad()
    }
    func activate(_ id: UUID) {
        do { var next = collection; try next.activate(id); try persist(next); resetAndLoad() }
        catch { self.error = error.localizedDescription }
    }
    func removeConfiguration(_ id: UUID) {
        do { var next = collection; next.remove(id); try persist(next); resetAndLoad() }
        catch { self.error = error.localizedDescription }
    }
    private func persist(_ next: ConfigurationCollection) throws {
        let parsed = try next.active?.configuration()
        let data = try JSONEncoder().encode(next)
        try FileManager.default.createDirectory(at: storage.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: storage, options: .atomic)
        collection = next
        configuration = parsed
        restoreSource()
    }
    private func restoreActive() throws {
        configuration = try collection.active?.configuration()
        restoreSource()
    }
    private func restoreSource() {
        let key = collection.active.flatMap { collection.sourceKeys[$0.id.uuidString] }
        site = configuration?.sites.first(where: { $0.key == key }) ?? configuration?.sites.first
    }
    private func resetAndLoad() { categories = []; filters = [:]; query = CatalogQuery(); load() }
    func select(_ selected: SourceSite?) {
        guard let selected else { return }
        do {
            var next = collection
            try next.selectSource(selected.key)
            try persist(next)
            resetAndLoad()
        } catch { self.error = error.localizedDescription }
    }
    func load(category: String? = nil, search: String? = nil) {
        query = CatalogQuery(category: category, search: search)
        fetch(append: false)
    }
    func selectFilter(key: String, value: String) {
        query.filters[key] = value
        query.page = 1
        fetch(append: false)
    }
    func loadMore() {
        guard canLoadMore, !busy else { return }
        query.page += 1
        fetch(append: true)
    }
    private func fetch(append: Bool) {
        requestTask?.cancel()
        let token = UUID()
        requestID = token
        if !append { videos = []; canLoadMore = false }
        guard let site else { busy = false; return }
        let requested = query
        busy = true
        requestTask = Task {
            do {
                let response = try await catalog.request(site: site, query: requested)
                guard !Task.isCancelled, requestID == token else { return }
                var seen = Set(append ? videos.map(\.id) : [])
                let unique = response.videos.filter { seen.insert($0.id).inserted }
                if append { videos += unique } else { videos = unique }
                canLoadMore = response.pageCount.map { requested.page < $0 } ?? (!unique.isEmpty && requested.category != nil)
                if requested.category == nil && requested.search == nil {
                    categories = response.categories
                    filters = response.filters
                }
            } catch is CancellationError {} catch {
                if !Task.isCancelled && requestID == token {
                    self.error = error.localizedDescription
                    if append { query.page = max(1, requested.page - 1) }
                }
            }
            if requestID == token { busy = false }
        }
    }
    func detail(_ video: Video, source: SourceSite?) async throws -> Video {
        guard let site = source ?? site else { throw SourceError.invalidConfiguration("请先选择影视源") }
        let response = try await catalog.request(site: site, query: CatalogQuery(ids: video.id))
        guard let detail = response.videos.first else { throw SourceError.invalidConfiguration("影视源未返回详情") }
        return detail
    }
}
