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
public actor PlaybackResolver {
    private let repository: CatalogRepository
    public init(repository: CatalogRepository = CatalogRepository()) { self.repository = repository }
    public func resolve(site: SourceSite, episode: Episode, line: String) async throws -> URL {
        var address = episode.address
        if site.type == 4 {
            let data = try await repository.requestData(site: site, parameters: ["play": address, "flag": line])
            let descriptor = try JSONDecoder().decode(PlaybackDescriptor.self, from: data)
            guard !descriptor.requiresParser else { throw SourceError.invalidConfiguration("此播放结果要求网页或二次解析，当前尚未完成兼容") }
            let extraHeaders = (descriptor.header ?? [:]).merging(descriptor.headers ?? [:]) { _, new in new }
            guard extraHeaders.isEmpty && site.headers.isEmpty else { throw SourceError.invalidConfiguration("此媒体要求自定义请求头，当前原生播放尚未完成对应代理适配") }
            address = descriptor.url
        } else if [0, 1].contains(site.type) {
            guard site.playUrl?.isEmpty != false else { throw SourceError.invalidConfiguration("此源配置了 playUrl 网页解析，需要进一步适配") }
            guard site.headers.isEmpty else { throw SourceError.invalidConfiguration("此媒体要求自定义请求头，当前原生播放尚未完成对应代理适配") }
        } else { throw SourceError.unsupportedSource(site.type) }
        guard let url = URL(string: address), ["http", "https"].contains(url.scheme?.lowercased() ?? ""), url.host != nil else { throw SourceError.invalidURL }
        return url
    }
}
