import Foundation

public actor PosterRepository {
    public static let shared = PosterRepository()
    private let session: URLSession
    public init(session: URLSession = .shared) { self.session = session }
    public static func makeRequest(address: String, site: SourceSite?) throws -> URLRequest {
        let trimmed = address.trimmingCharacters(in: .whitespacesAndNewlines)
        let base = site.flatMap { URL(string: $0.api) }
        guard !trimmed.isEmpty, let url = URL(string: trimmed, relativeTo: base)?.absoluteURL,
              ["http", "https"].contains(url.scheme?.lowercased() ?? ""), url.host != nil else { throw SourceError.invalidConfiguration("海报地址无效") }
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        for (name, value) in site?.headers ?? [:] {
            let lower = name.lowercased()
            if lower == "host" { continue }
            if ["authorization", "cookie"].contains(lower) && !sameOrigin(url, base) { continue }
            request.setValue(value, forHTTPHeaderField: name)
        }
        return request
    }
    private static func sameOrigin(_ left: URL, _ right: URL?) -> Bool {
        guard let right else { return false }
        func port(_ url: URL) -> Int { url.port ?? (url.scheme?.lowercased() == "https" ? 443 : 80) }
        return left.scheme?.lowercased() == right.scheme?.lowercased() && left.host?.lowercased() == right.host?.lowercased() && port(left) == port(right)
    }
    static func redirectRequest(_ request: URLRequest, origin: URL?) -> URLRequest {
        var result = request
        if let url = request.url, !sameOrigin(url, origin) {
            result.setValue(nil, forHTTPHeaderField: "Authorization")
            result.setValue(nil, forHTTPHeaderField: "Cookie")
        }
        return result
    }
    public func data(address: String, site: SourceSite?) async throws -> Data {
        let request = try Self.makeRequest(address: address, site: site)
        let (data, response) = try await session.data(for: request, delegate: PosterRedirectDelegate(origin: request.url))
        try Task.checkCancellation()
        guard let response = response as? HTTPURLResponse else { throw SourceError.invalidConfiguration("海报响应无效") }
        guard (200..<300).contains(response.statusCode) else { throw SourceError.httpStatus(response.statusCode) }
        guard !data.isEmpty, data.count <= 8 * 1024 * 1024 else { throw SourceError.invalidConfiguration("海报为空或超过 8 MB") }
        return data
    }
}
private final class PosterRedirectDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    let origin: URL?
    init(origin: URL?) { self.origin = origin }
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(PosterRepository.redirectRequest(request, origin: origin))
    }
}
