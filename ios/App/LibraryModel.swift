import SwiftUI
import CatTVCore

@MainActor
final class LibraryModel: ObservableObject {
    @Published private(set) var configuration: SourceConfiguration?
    @Published private(set) var site: SourceSite?
    @Published private(set) var videos: [Video] = []
    @Published private(set) var categories: [VideoCategory] = []
    @Published private(set) var busy = false
    @Published var error: String?
    @Published var configurationURL = ""
    private let configurations = ConfigurationRepository()
    private let catalog = CatalogRepository()
    private var requestTask: Task<Void, Never>?
    private var requestID = UUID()
    private let storage: URL
    init() {
        storage = URL.applicationSupportDirectory.appendingPathComponent("CatTV/configuration.json")
        do {
            if FileManager.default.fileExists(atPath: storage.path) {
                configuration = try SourceConfiguration.decode(Data(contentsOf: storage))
                site = configuration?.sites.first
            }
        } catch { self.error = "本地配置读取失败：\(error.localizedDescription)" }
    }
    func importConfiguration() async {
        guard let url = URL(string: configurationURL) else { error = SourceError.invalidURL.localizedDescription; return }
        busy = true
        defer { busy = false }
        do {
            let data = try await configurations.loadData(from: url)
            let config = try SourceConfiguration.decode(data)
            try FileManager.default.createDirectory(at: storage.deletingLastPathComponent(), withIntermediateDirectories: true)
            try data.write(to: storage, options: .atomic)
            configuration = config
            select(config.sites.first)
        } catch is CancellationError {} catch { self.error = error.localizedDescription }
    }
    func select(_ selected: SourceSite?) {
        site = selected
        categories = []
        load()
    }
    func load(category: String? = nil, search: String? = nil) {
        requestTask?.cancel()
        let token = UUID()
        requestID = token
        videos = []
        guard let site else { busy = false; return }
        busy = true
        requestTask = Task {
            do {
                var parameters: [String: String] = [:]
                if let category { parameters = ["ac": "detail", "t": category, "pg": "1"] }
                if let search, !search.isEmpty { parameters = ["ac": "detail", "wd": search] }
                let response = try await catalog.request(site: site, parameters: parameters)
                guard !Task.isCancelled, requestID == token else { return }
                videos = response.videos
                if category == nil && search == nil { categories = response.categories }
            } catch is CancellationError {} catch {
                if !Task.isCancelled && requestID == token { self.error = error.localizedDescription }
            }
            if requestID == token { busy = false }
        }
    }
    func detail(_ video: Video, source: SourceSite?) async throws -> Video {
        guard let site = source ?? site else { throw SourceError.invalidConfiguration("请先选择影视源") }
        let response = try await catalog.request(site: site, parameters: ["ac": "detail", "ids": video.id])
        guard let detail = response.videos.first else { throw SourceError.invalidConfiguration("影视源未返回详情") }
        return detail
    }
}
