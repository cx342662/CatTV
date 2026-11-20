import Foundation

public struct SourceConfiguration: Codable, Equatable, Sendable {
    public var sites: [SourceSite]
    public var spider: String?
    public static func decode(_ data: Data) throws -> Self {
        let configuration = try JSONDecoder().decode(Self.self, from: data)
        guard !configuration.sites.isEmpty else { throw SourceError.invalidConfiguration("配置没有影视源") }
        guard configuration.sites.allSatisfy({ !$0.key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !$0.api.isEmpty }) else { throw SourceError.invalidConfiguration("影视源缺少 key 或 api") }
        guard Set(configuration.sites.map(\.key)).count == configuration.sites.count else {
            throw SourceError.invalidConfiguration("影视源 key 重复")
        }
        return configuration
    }
}

public struct SourceSite: Codable, Equatable, Identifiable, Sendable {
    public var key: String
    public var name: String
    public var type: Int
    public var api: String
    public var id: String { key }
}

public enum SourceError: LocalizedError {
    case invalidConfiguration(String)
    case unsupportedSource(Int)
    case invalidURL
    case httpStatus(Int)
    public var errorDescription: String? {
        switch self {
        case .invalidConfiguration(let message): return message
        case .unsupportedSource(let type): return "尚未移植此源类型：\(type)，不能直接作为视频地址播放"
        case .invalidURL: return "需要有效的 HTTP 或 HTTPS 地址"
        case .httpStatus(let status): return "服务器返回 HTTP \(status)"
        }
    }
}

public actor ConfigurationRepository {
    private let session: URLSession
    public init(session: URLSession = .shared) { self.session = session }
    public func load(from url: URL) async throws -> SourceConfiguration {
        try SourceConfiguration.decode(await loadData(from: url))
    }
    public func loadData(from url: URL) async throws -> Data {
        guard ["http", "https"].contains(url.scheme?.lowercased() ?? "") else { throw SourceError.invalidURL }
        let (data, response) = try await session.data(from: url)
        guard let response = response as? HTTPURLResponse else { throw SourceError.invalidURL }
        guard (200..<300).contains(response.statusCode) else { throw SourceError.httpStatus(response.statusCode) }
        return data
    }
}
