import Foundation

public enum JSONValue: Codable, Equatable, Sendable {
    case string(String), number(Double), bool(Bool), object([String: JSONValue]), array([JSONValue]), null
    public init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let v = try? c.decode(Bool.self) { self = .bool(v) }
        else if let v = try? c.decode(String.self) { self = .string(v) }
        else if let v = try? c.decode(Double.self) { self = .number(v) }
        else if let v = try? c.decode([String: JSONValue].self) { self = .object(v) }
        else { self = .array(try c.decode([JSONValue].self)) }
    }
    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .string(let v): try c.encode(v)
        case .number(let v): try c.encode(v)
        case .bool(let v): try c.encode(v)
        case .object(let v): try c.encode(v)
        case .array(let v): try c.encode(v)
        case .null: try c.encodeNil()
        }
    }
    public var text: String? {
        switch self {
        case .string(let s): return s
        case .number(let n): return n == n.rounded() ? String(format: "%.0f", n) : String(n)
        case .bool(let b): return b ? "true" : "false"
        default: return nil
        }
    }
    public var serialized: String {
        if case .string(let s) = self { return s }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        guard let data = try? encoder.encode(self), let text = String(data: data, encoding: .utf8) else { return "" }
        return text
    }
}

public struct SourceConfiguration: Codable, Equatable, Sendable {
    public var sites: [SourceSite]
    public var spider: String?
    public var parses: [ParserEndpoint]?
    public var lives: [JSONValue]?
    enum CodingKeys: String, CodingKey { case sites, spider, parses, lives }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        sites = try c.decodeIfPresent([SourceSite].self, forKey: .sites) ?? []
        spider = try c.decodeIfPresent(JSONValue.self, forKey: .spider)?.serialized
        parses = try c.decodeIfPresent([ParserEndpoint].self, forKey: .parses)
        lives = try c.decodeIfPresent([JSONValue].self, forKey: .lives)
    }
    public static func decode(_ data: Data, baseURL: URL? = nil) throws -> Self {
        var configuration = try JSONDecoder().decode(Self.self, from: data)
        guard !configuration.sites.isEmpty || configuration.lives?.isEmpty == false else { throw SourceError.invalidConfiguration("配置没有影视源或直播源") }
        guard configuration.sites.allSatisfy({ !$0.key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !$0.api.isEmpty }) else { throw SourceError.invalidConfiguration("影视源缺少 key 或 api") }
        guard Set(configuration.sites.map(\.key)).count == configuration.sites.count else { throw SourceError.invalidConfiguration("影视源 key 重复") }
        if let baseURL {
            if var parsers = configuration.parses {
                for index in parsers.indices {
                    if let url = URL(string: parsers[index].url, relativeTo: baseURL)?.absoluteURL { parsers[index].url = url.absoluteString }
                }
                configuration.parses = parsers
            }
            for index in configuration.sites.indices where [0, 1, 4].contains(configuration.sites[index].type) {
                if let resolved = URL(string: configuration.sites[index].api, relativeTo: baseURL)?.absoluteURL {
                    configuration.sites[index].api = resolved.absoluteString
                }
            }
        }
        for index in configuration.sites.indices {
            if let parsers = configuration.parses { configuration.sites[index].parsers = parsers }
        }
        return configuration
    }
}

public struct SourceSite: Codable, Equatable, Identifiable, Sendable {
    public var key: String
    public var name: String
    public var type: Int
    public var api: String
    public var header: [String: JSONValue]?
    public var ext: JSONValue?
    public var playUrl: String?
    public var timeout: Int?
    public var searchable: JSONValue?
    public var parsers: [ParserEndpoint]?
    public var id: String { key }
    enum CodingKeys: String, CodingKey { case key, name, type, api, header, ext, playUrl, timeout, searchable, parsers }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = try c.decode(String.self, forKey: .key)
        name = try c.decode(String.self, forKey: .name)
        api = try c.decode(String.self, forKey: .api)
        let rawType = try c.decode(JSONValue.self, forKey: .type)
        guard let raw = rawType.text, let value = Int(raw) else { throw SourceError.invalidConfiguration("影视源 type 必须为整数") }
        type = value
        header = try c.decodeIfPresent([String: JSONValue].self, forKey: .header)
        ext = try c.decodeIfPresent(JSONValue.self, forKey: .ext)
        playUrl = try c.decodeIfPresent(String.self, forKey: .playUrl)
        timeout = try c.decodeIfPresent(JSONValue.self, forKey: .timeout)?.text.flatMap(Int.init)
        searchable = try c.decodeIfPresent(JSONValue.self, forKey: .searchable)
        parsers = try c.decodeIfPresent([ParserEndpoint].self, forKey: .parsers)
    }
    public var headers: [String: String] {
        (header ?? [:]).reduce(into: [:]) { result, pair in
            if let value = pair.value.text, HeaderPolicy.isSendable(name: pair.key, value: value) { result[pair.key] = value }
        }
    }
}

public enum HeaderPolicy {
    public static func isSendable(name: String, value: String) -> Bool {
        let separators = "()<>@,;:\\\"/[]?={} \t"
        return !name.isEmpty && name.unicodeScalars.allSatisfy { $0.value > 32 && $0.value < 127 && !separators.unicodeScalars.contains($0) }
            && value.unicodeScalars.allSatisfy { $0.value == 9 || ($0.value >= 32 && $0.value <= 126) }
    }
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
        try SourceConfiguration.decode(await loadData(from: url), baseURL: url)
    }
    public func loadData(from url: URL) async throws -> Data {
        guard ["http", "https"].contains(url.scheme?.lowercased() ?? "") else { throw SourceError.invalidURL }
        let (data, response) = try await session.data(from: url)
        guard let response = response as? HTTPURLResponse else { throw SourceError.invalidURL }
        guard (200..<300).contains(response.statusCode) else { throw SourceError.httpStatus(response.statusCode) }
        return data
    }
}
