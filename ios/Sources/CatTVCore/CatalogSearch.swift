import Foundation

public struct SearchSourceResult: Identifiable, Sendable {
    public let site: SourceSite
    public let videos: [Video]
    public let failure: String?
    public var id: String { site.key }
}
public actor CatalogSearch {
    private let repository: CatalogRepository
    public init(repository: CatalogRepository = CatalogRepository()) { self.repository = repository }
    public func search(sites: [SourceSite], keyword: String, concurrency: Int = 3) async throws -> [SearchSourceResult] {
        let keyword = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !keyword.isEmpty else { return [] }
        let sources = sites.filter { $0.searchable?.text != "0" && $0.searchable?.text != "false" }
        let repository = repository
        let limit = min(max(1, concurrency), sources.count)
        let results = await withTaskGroup(of: SearchSourceResult.self, returning: [SearchSourceResult].self) { group in
            for site in sources.prefix(limit) {
                group.addTask { await Self.fetch(site: site, keyword: keyword, repository: repository) }
            }
            var next = limit
            var results: [SearchSourceResult] = []
            for await result in group {
                if Task.isCancelled { group.cancelAll(); break }
                results.append(result)
                if next < sources.count {
                    let site = sources[next]; next += 1
                    group.addTask { await Self.fetch(site: site, keyword: keyword, repository: repository) }
                }
            }
            return results
        }
        try Task.checkCancellation()
        return sources.compactMap { site in results.first { $0.id == site.key } }
    }
    private static func fetch(site: SourceSite, keyword: String, repository: CatalogRepository) async -> SearchSourceResult {
        do {
            let response = try await repository.request(site: site, query: CatalogQuery(search: keyword))
            return SearchSourceResult(site: site, videos: response.videos, failure: nil)
        } catch { return SearchSourceResult(site: site, videos: [], failure: error.localizedDescription) }
    }
}
