import Foundation

public struct Video: Codable, Identifiable, Equatable, Sendable {
    public let id: String
    public let name: String
    public let poster: String
    public let remarks: String
    public let content: String
    public let playFrom: String
    public let playURL: String
    enum CodingKeys: String, CodingKey {
        case id = "vod_id", name = "vod_name", poster = "vod_pic", remarks = "vod_remarks"
        case content = "vod_content", playFrom = "vod_play_from", playURL = "vod_play_url"
    }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let text = try? c.decode(String.self, forKey: .id) { id = text }
        else { id = String(try c.decode(Int.self, forKey: .id)) }
        name = try c.decode(String.self, forKey: .name)
        poster = try c.decodeIfPresent(String.self, forKey: .poster) ?? ""
        remarks = try c.decodeIfPresent(String.self, forKey: .remarks) ?? ""
        content = try c.decodeIfPresent(String.self, forKey: .content) ?? ""
        playFrom = try c.decodeIfPresent(String.self, forKey: .playFrom) ?? ""
        playURL = try c.decodeIfPresent(String.self, forKey: .playURL) ?? ""
    }
    public var lines: [PlaybackLine] {
        let names = playFrom.components(separatedBy: "$$$")
        return playURL.components(separatedBy: "$$$").enumerated().compactMap { index, group in
            guard !group.isEmpty else { return nil }
            let episodes = group.components(separatedBy: "#").enumerated().compactMap { offset, value -> Episode? in
                guard !value.isEmpty else { return nil }
                let parts = value.split(separator: "$", maxSplits: 1, omittingEmptySubsequences: false)
                return Episode(id: offset, name: parts.count == 2 ? String(parts[0]) : "第\(offset + 1)集", address: String(parts.last ?? ""))
            }
            return PlaybackLine(id: index, name: index < names.count ? names[index] : "线路\(index + 1)", episodes: episodes)
        }
    }
}
public struct PlaybackLine: Identifiable, Sendable {
    public let id: Int
    public let name: String
    public let episodes: [Episode]
}
public struct Episode: Identifiable, Sendable {
    public let id: Int
    public let name: String
    public let address: String
}
public struct VideoCategory: Decodable, Identifiable, Sendable {
    public let id: String
    public let name: String
    enum CodingKeys: String, CodingKey { case id = "type_id", name = "type_name" }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let text = try? c.decode(String.self, forKey: .id) { id = text }
        else { id = String(try c.decode(Int.self, forKey: .id)) }
        name = try c.decode(String.self, forKey: .name)
    }
}
public struct CatalogResponse: Decodable, Sendable {
    public let videos: [Video]
    public let categories: [VideoCategory]
    enum CodingKeys: String, CodingKey { case videos = "list", categories = "class" }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        videos = try c.decodeIfPresent([Video].self, forKey: .videos) ?? []
        categories = try c.decodeIfPresent([VideoCategory].self, forKey: .categories) ?? []
    }
}
public actor CatalogRepository {
    private let session: URLSession
    public init(session: URLSession = .shared) { self.session = session }
    public func request(site: SourceSite, parameters: [String: String] = [:]) async throws -> CatalogResponse {
        guard site.type == 1 else { throw SourceError.unsupportedSource(site.type) }
        guard var components = URLComponents(string: site.api), ["https", "http"].contains(components.scheme?.lowercased() ?? "") else { throw SourceError.invalidURL }
        var query = (components.queryItems ?? []).filter { parameters[$0.name] == nil }
        query += parameters.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        components.queryItems = query
        guard let url = components.url else { throw SourceError.invalidURL }
        var request = URLRequest(url: url)
        request.timeoutInterval = 30
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw SourceError.invalidURL }
        guard (200..<300).contains(http.statusCode) else { throw SourceError.httpStatus(http.statusCode) }
        return try JSONDecoder().decode(CatalogResponse.self, from: data)
    }
}
