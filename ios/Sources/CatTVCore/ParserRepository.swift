import Foundation

public struct ParserEndpoint: Codable, Equatable, Identifiable, Sendable {
    public let name: String
    public var url: String
    public let type: Int
    public let ext: JSONValue?
    public var id: String { name + ":" + url }
    enum CodingKeys: String, CodingKey { case name, url, type, ext }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decode(String.self, forKey: .name)
        url = try c.decode(String.self, forKey: .url)
        let raw = try c.decodeIfPresent(JSONValue.self, forKey: .type)
        type = raw?.text.flatMap(Int.init) ?? 0
        ext = try c.decodeIfPresent(JSONValue.self, forKey: .ext)
    }
}
public actor ParserRepository {
    private let session: URLSession
    public init(session: URLSession = .shared) { self.session = session }
    public static func makeRequest(endpoint: ParserEndpoint, input: String) throws -> URLRequest {
        guard var components = URLComponents(string: endpoint.url) else { throw SourceError.invalidURL }
        var metadata = endpoint.ext
        if let encoded = components.queryItems?.first(where: { $0.name == "cat_ext" })?.value {
            var normalized = encoded.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
            normalized += String(repeating: "=", count: (4 - normalized.count % 4) % 4)
            guard let data = Data(base64Encoded: normalized) else { throw SourceError.invalidConfiguration("解析器 cat_ext 无效") }
            metadata = try JSONDecoder().decode(JSONValue.self, from: data)
            components.queryItems = components.queryItems?.filter { $0.name != "cat_ext" }
        }
        guard let prefix = components.string, let url = URL(string: prefix + input), ["http", "https"].contains(url.scheme?.lowercased() ?? ""), url.host != nil else { throw SourceError.invalidURL }
        var request = URLRequest(url: url)
        request.timeoutInterval = 30
        if let current = metadata, case .string(let text) = current { metadata = try JSONDecoder().decode(JSONValue.self, from: Data(text.utf8)) }
        if let current = metadata, case .object(let object) = current, let rawHeaders = object["header"], case .object(let headers) = rawHeaders {
            for (name, value) in headers {
                if let text = value.text, HeaderPolicy.isSendable(name: name, value: text) { request.setValue(text, forHTTPHeaderField: name) }
            }
        }
        return request
    }
    public static func decode(_ data: Data, input: String, headers: [String: String]) throws -> ResolvedPlayback {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw SourceError.invalidConfiguration("解析结果不是 JSON 对象") }
        let payload = (root["data"] as? [String: Any]) ?? root
        guard var address = payload["url"] as? String else { throw SourceError.invalidConfiguration("解析结果缺少 url") }
        if address.hasPrefix("//") { address = "https:" + address }
        guard let url = URL(string: address), ["http", "https"].contains(url.scheme?.lowercased() ?? ""), url.host != nil else { throw SourceError.invalidURL }
        if address == input && !["m3u8", "mp4", "m4a", "mp3", "mov", "mkv", "flv"].contains(url.pathExtension.lowercased()) { throw SourceError.invalidConfiguration("解析器原样返回了非媒体页面") }
        var merged = headers
        for key in ["header", "headers"] {
            if let values = root[key] as? [String: Any] {
                for (name, value) in values {
                    if let text = value as? String, HeaderPolicy.isSendable(name: name, value: text) { merged[name] = text }
                }
            }
        }
        for (key, canonical) in [("user-agent", "User-Agent"), ("referer", "Referer")] {
            if let value = root[key] as? String, HeaderPolicy.isSendable(name: canonical, value: value) { merged[canonical] = value }
        }
        let needsWeb = [root["parse"], root["jx"]].contains { value in
            if let value = value as? NSNumber { return value.intValue == 1 }
            if let value = value as? String { return value == "1" || value == "true" }
            return false
        }
        return ResolvedPlayback(url: url, headers: merged, requiresWebParsing: needsWeb)
    }
    public func resolve(endpoint: ParserEndpoint, input: String, headers: [String: String]) async throws -> ResolvedPlayback {
        let request = try Self.makeRequest(endpoint: endpoint, input: input)
        if endpoint.type == 0 {
            guard let url = request.url else { throw SourceError.invalidURL }
            let merged = headers.merging(request.allHTTPHeaderFields ?? [:]) { _, new in new }
            return ResolvedPlayback(url: url, headers: merged, requiresWebParsing: true)
        }
        guard endpoint.type == 1 else { throw SourceError.invalidConfiguration("尚未移植解析器类型：\(endpoint.type)") }
        let (data, response) = try await session.data(for: request)
        guard let response = response as? HTTPURLResponse else { throw SourceError.invalidURL }
        guard (200..<300).contains(response.statusCode) else { throw SourceError.httpStatus(response.statusCode) }
        return try Self.decode(data, input: input, headers: headers)
    }
    public func resolve(endpoints: [ParserEndpoint], input: String, headers: [String: String]) async throws -> ResolvedPlayback {
        let json = endpoints.filter { $0.type == 1 }
        let selected = try await withThrowingTaskGroup(of: ResolvedPlayback?.self, returning: ResolvedPlayback?.self) { group in
            var next = 0
            for endpoint in json.prefix(5) {
                next += 1
                group.addTask { do { return try await self.resolve(endpoint: endpoint, input: input, headers: headers) } catch { return nil } }
            }
            for try await result in group {
                try Task.checkCancellation()
                if let result { group.cancelAll(); return result }
                if next < json.count {
                    let endpoint = json[next]; next += 1
                    group.addTask { do { return try await self.resolve(endpoint: endpoint, input: input, headers: headers) } catch { return nil } }
                }
            }
            return nil
        }
        if let selected { return selected }
        if let web = endpoints.first(where: { $0.type == 0 }) { return try await resolve(endpoint: web, input: input, headers: headers) }
        throw SourceError.invalidConfiguration("所有兼容 JSON 解析器均未返回有效媒体地址")
    }
}
