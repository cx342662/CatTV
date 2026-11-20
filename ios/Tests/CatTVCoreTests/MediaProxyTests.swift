import XCTest
import Foundation
@testable import CatTVCore

private final class ProxyFixtureProtocol: URLProtocol, @unchecked Sendable {
    typealias Handler = (URLRequest) throws -> (Int, [String: String], Data)
    private static let lock = NSLock()
    private static var handler: Handler?
    static func install(_ value: @escaping Handler) { lock.lock(); handler = value; lock.unlock() }
    private static func current() -> Handler? { lock.lock(); defer { lock.unlock() }; return handler }
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "proxy-fixture.invalid" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        do {
            guard let handler = Self.current(), let url = request.url else { throw SourceError.invalidURL }
            let (status, headers, data) = try handler(request)
            guard let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers) else { throw SourceError.invalidURL }
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch { client?.urlProtocol(self, didFailWithError: error) }
    }
    override func stopLoading() {}
}
final class MediaProxyTests: XCTestCase {
    private func upstream() -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ProxyFixtureProtocol.self]
        return URLSession(configuration: configuration)
    }
    func testRangeAndHeadersSurviveActualLoopbackTransport() async throws {
        ProxyFixtureProtocol.install { request in
            XCTAssertEqual(request.value(forHTTPHeaderField: "Referer"), "https://site.invalid/")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Range"), "bytes=2-5")
            return (206, ["Content-Type": "video/mp4", "Content-Length": "4", "Content-Range": "bytes 2-5/10"], Data("2345".utf8))
        }
        let proxy = MediaHTTPProxy(origin: try XCTUnwrap(URL(string: "https://proxy-fixture.invalid/media.mp4")), headers: ["Referer": "https://site.invalid/"], session: upstream())
        do {
            let local = try await proxy.start()
            XCTAssertEqual(local.host, "127.0.0.1")
            var request = URLRequest(url: local)
            request.setValue("bytes=2-5", forHTTPHeaderField: "Range")
            request.timeoutInterval = 10
            let (data, response) = try await URLSession.shared.data(for: request)
            XCTAssertEqual(data, Data("2345".utf8))
            XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 206)
            XCTAssertEqual((response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Range"), "bytes 2-5/10")
            await proxy.stop()
        } catch { await proxy.stop(); throw error }
    }
    func testHLSPlaylistAndSegmentUseOpaqueLocalRoutes() async throws {
        ProxyFixtureProtocol.install { request in
            XCTAssertEqual(request.value(forHTTPHeaderField: "X-Test"), "kept")
            if request.url?.path == "/master.m3u8" {
                let playlist = Data("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n#EXTINF:5,\nsegment.ts\n".utf8)
                return (200, ["Content-Type": "application/vnd.apple.mpegurl"], playlist)
            }
            return (200, ["Content-Type": "video/mp2t", "Content-Length": "3"], Data([1, 2, 3]))
        }
        let proxy = MediaHTTPProxy(origin: try XCTUnwrap(URL(string: "https://proxy-fixture.invalid/master.m3u8")), headers: ["X-Test": "kept"], session: upstream())
        do {
            let local = try await proxy.start()
            let (data, _) = try await URLSession.shared.data(from: local)
            let playlist = String(decoding: data, as: UTF8.self)
            XCTAssertTrue(playlist.contains("URI=\"http://127.0.0.1:"))
            XCTAssertFalse(playlist.contains("proxy-fixture.invalid"))
            let address = try XCTUnwrap(playlist.components(separatedBy: "\n").first { $0.hasPrefix("http://") })
            let (segment, response) = try await URLSession.shared.data(from: XCTUnwrap(URL(string: address)))
            XCTAssertEqual(segment, Data([1, 2, 3]))
            XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 200)
            let unknown = local.deletingLastPathComponent().appendingPathComponent("unknown")
            let (_, missing) = try await URLSession.shared.data(from: unknown)
            XCTAssertEqual((missing as? HTTPURLResponse)?.statusCode, 404)
            await proxy.stop()
        } catch { await proxy.stop(); throw error }
    }
}
