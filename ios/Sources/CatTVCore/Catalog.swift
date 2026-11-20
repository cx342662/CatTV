import Foundation

public struct Video: Codable, Identifiable, Equatable, Sendable {
    public let id: String
    public let name: String
    public let poster: String
    public let remarks: String
    public let content: String
    public let playFrom: String
    public let playURL: String
    public let year: String
    public let area: String
    public let language: String
    public let genre: String
    public let actors: String
    public let director: String
    enum CodingKeys: String, CodingKey {
        case id = "vod_id", name = "vod_name", poster = "vod_pic", remarks = "vod_remarks"
        case content = "vod_content", playFrom = "vod_play_from", playURL = "vod_play_url"
        case year = "vod_year", area = "vod_area", language = "vod_lang", genre = "type_name", actors = "vod_actor", director = "vod_director"
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
        func scalar(_ key: CodingKeys) -> String { (try? c.decode(JSONValue.self, forKey: key))?.text ?? "" }
        year = scalar(.year); area = scalar(.area); language = scalar(.language)
        genre = scalar(.genre); actors = scalar(.actors); director = scalar(.director)
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
    public let filters: [String: [CatalogFilter]]
    public let page: Int
    public let pageCount: Int?
    enum CodingKeys: String, CodingKey { case videos = "list", categories = "class", filters, page, pageCount = "pagecount" }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        videos = try c.decodeIfPresent([Video].self, forKey: .videos) ?? []
        categories = try c.decodeIfPresent([VideoCategory].self, forKey: .categories) ?? []
        if let values = try? c.decode([String: JSONValue].self, forKey: .filters) {
            filters = try values.mapValues { value in
                let data = try JSONEncoder().encode(value)
                if case .array = value { return try JSONDecoder().decode([CatalogFilter].self, from: data) }
                return [try JSONDecoder().decode(CatalogFilter.self, from: data)]
            }
        } else { filters = [:] }
        func number(_ key: CodingKeys) -> Int? {
            if let n = try? c.decode(Int.self, forKey: key) { return n }
            if let s = try? c.decode(String.self, forKey: key) { return Int(s) }
            return nil
        }
        page = number(.page) ?? 1
        pageCount = number(.pageCount)
    }
}
public actor CatalogRepository {
    private let session: URLSession
    private var extensionCache: [String: String] = [:]
    public init(session: URLSession = .shared) { self.session = session }
    public func request(site: SourceSite, parameters: [String: String] = [:]) async throws -> CatalogResponse {
        let data = try await requestData(site: site, parameters: parameters)
        return try decode(data, type: site.type)
    }
    public func request(site: SourceSite, query: CatalogQuery) async throws -> CatalogResponse {
        try await request(site: site, parameters: query.parameters(type: site.type))
    }
    public func decode(_ data: Data, type: Int) throws -> CatalogResponse {
        if type == 0 { return try XMLCatalogDecoder.decode(data) }
        return try JSONDecoder().decode(CatalogResponse.self, from: data)
    }
    public func requestData(site: SourceSite, parameters: [String: String]) async throws -> Data {
        guard [0, 1, 4].contains(site.type) else { throw SourceError.unsupportedSource(site.type) }
        var parameters = parameters
        if site.type == 4, let ext = site.ext {
            var text = ext.serialized
            if let url = URL(string: text), ["http", "https"].contains(url.scheme?.lowercased() ?? "") {
                if let cached = extensionCache[text] { text = cached }
                else {
                    var extensionSite = site
                    extensionSite.header = nil
                    let fetched = try await transfer(Self.makeRequest(site: extensionSite, address: url.absoluteString, parameters: [:]))
                    guard let value = String(data: fetched, encoding: .utf8) else { throw SourceError.invalidConfiguration("扩展配置不是 UTF-8") }
                    if value.count <= 2500 {
                        if let json = try? JSONSerialization.jsonObject(with: fetched), JSONSerialization.isValidJSONObject(json) {
                            text = String(decoding: try JSONSerialization.data(withJSONObject: json, options: [.sortedKeys]), as: UTF8.self)
                        } else { text = value }
                    }
                    extensionCache[ext.serialized] = text
                }
            }
            if !text.isEmpty { parameters["extend"] = text }
        }
        return try await transfer(Self.makeRequest(site: site, address: site.api, parameters: parameters))
    }
    public static func makeRequest(site: SourceSite, address: String, parameters: [String: String]) throws -> URLRequest {
        guard var components = URLComponents(string: address), ["https", "http"].contains(components.scheme?.lowercased() ?? ""), components.host != nil else { throw SourceError.invalidURL }
        let usePost = site.type == 4 && (parameters["extend"]?.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)?.count ?? 0) >= 1000
        let parameters = parameters.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        if !usePost {
            let names = Set(parameters.map(\.name))
            components.queryItems = (components.queryItems ?? []).filter { !names.contains($0.name) } + parameters
            components.percentEncodedQuery = components.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
        }
        guard let url = components.url else { throw SourceError.invalidURL }
        var request = URLRequest(url: url)
        request.timeoutInterval = TimeInterval(max(5, min(site.timeout ?? 30, 180)))
        for (name, value) in site.headers { request.setValue(value, forHTTPHeaderField: name) }
        if usePost {
            request.httpMethod = "POST"
            var body = URLComponents()
            body.queryItems = parameters
            request.httpBody = body.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B").data(using: .utf8)
            request.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
        }
        return request
    }
    private func transfer(_ request: URLRequest) async throws -> Data {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw SourceError.invalidURL }
        guard (200..<300).contains(http.statusCode) else { throw SourceError.httpStatus(http.statusCode) }
        return data
    }
}
