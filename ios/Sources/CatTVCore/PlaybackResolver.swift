import Foundation

public struct PlaybackDescriptor: Decodable, Sendable {
    public let url: String
    public let parse: JSONValue?
    public let jx: JSONValue?
    public let header: [String: JSONValue]?
    public let headers: [String: JSONValue]?
    public var requiresParser: Bool {
        [parse, jx].contains { value in
            guard let text = value?.text else { return false }
            return text == "1" || text == "true"
        }
    }
}
public struct ResolvedPlayback: Sendable {
    public let url: URL
    public let headers: [String: String]
    public let requiresWebParsing: Bool
    public init(url: URL, headers: [String: String], requiresWebParsing: Bool = false) {
        self.url = url; self.headers = headers; self.requiresWebParsing = requiresWebParsing
    }
}
public actor PlaybackResolver {
    private let repository: CatalogRepository
    private let parserRepository = ParserRepository()
    public init(repository: CatalogRepository = CatalogRepository()) { self.repository = repository }
    public func resolve(site: SourceSite, episode: Episode, line: String) async throws -> URL {
        try await resolveResource(site: site, episode: episode, line: line).url
    }
    public func resolveResource(site: SourceSite, episode: Episode, line: String, parser: ParserEndpoint? = nil) async throws -> ResolvedPlayback {
        var requiresWeb = false
        var headers = site.headers
        var address = episode.address
        if site.type == 4 {
            let data = try await repository.requestData(site: site, parameters: ["play": address, "flag": line])
            let descriptor = try JSONDecoder().decode(PlaybackDescriptor.self, from: data)
            requiresWeb = descriptor.requiresParser
            let extraHeaders = (descriptor.header ?? [:]).merging(descriptor.headers ?? [:]) { _, new in new }
            for (key, value) in extraHeaders {
                if let text = value.text, HeaderPolicy.isSendable(name: key, value: text) { headers[key] = text }
            }
            address = descriptor.url
        } else if [0, 1].contains(site.type) {
            if let prefix = site.playUrl, !prefix.isEmpty, parser == nil { address = prefix + address; requiresWeb = true }

        } else { throw SourceError.unsupportedSource(site.type) }
        if let parser { return try await parserRepository.resolve(endpoint: parser, input: address, headers: headers) }
        if requiresWeb, site.playUrl?.isEmpty != false, let parsers = site.parsers, !parsers.isEmpty {
            return try await parserRepository.resolve(endpoints: parsers, input: address, headers: headers)
        }
        guard let url = URL(string: address), ["http", "https"].contains(url.scheme?.lowercased() ?? ""), url.host != nil else { throw SourceError.invalidURL }
        return ResolvedPlayback(url: url, headers: headers, requiresWebParsing: requiresWeb)
    }
}
