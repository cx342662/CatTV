import XCTest
import Foundation
@testable import CatTVCore

private final class ParserFixtureProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "parser-fixture.invalid" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        guard let url = request.url, let response = HTTPURLResponse(url: url, statusCode: url.path == "/bad" ? 500 : 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"]) else { return }
        XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(#"{"data":{"url":"https://media.invalid/video.m3u8"},"referer":"https://page.invalid/"}"#.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
final class ParserTests: XCTestCase {
    private func endpoint(_ json: String) throws -> ParserEndpoint { try JSONDecoder().decode(ParserEndpoint.self, from: Data(json.utf8)) }
    func testCatExtIsRemovedAndHeadersBecomeRequestHeaders() throws {
        let metadata = Data(#"{"header":{"User-Agent":"Parser Agent","Referer":"https://parser.invalid/"}}"#.utf8).base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        let parser = try endpoint("{\"name\":\"Test\",\"type\":1,\"url\":\"https://parser.invalid/?cat_ext=\(metadata)&url=\"}")
        let request = try ParserRepository.makeRequest(endpoint: parser, input: "https://page.invalid/watch")
        XCTAssertEqual(request.url?.absoluteString, "https://parser.invalid/?url=https://page.invalid/watch")
        XCTAssertEqual(request.value(forHTTPHeaderField: "User-Agent"), "Parser Agent")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Referer"), "https://parser.invalid/")
    }
    func testNestedParserOutputAndProtocolRelativeURL() throws {
        let data = Data(#"{"data":{"url":"//media.invalid/a.m3u8"},"user-agent":"Media Agent","referer":"https://page.invalid/"}"#.utf8)
        let result = try ParserRepository.decode(data, input: "https://page.invalid/watch", headers: ["X-Site": "kept"])
        XCTAssertEqual(result.url.absoluteString, "https://media.invalid/a.m3u8")
        XCTAssertEqual(result.headers["User-Agent"], "Media Agent")
        XCTAssertEqual(result.headers["X-Site"], "kept")
    }
    func testRejectsUnchangedWebPageResult() {
        XCTAssertThrowsError(try ParserRepository.decode(Data(#"{"url":"https://page.invalid/watch"}"#.utf8), input: "https://page.invalid/watch", headers: [:]))
    }
    func testRootParsersResolveRelativePrefixesAndTravelWithSourceSnapshot() throws {
        let data = Data(#"{"sites":[{"key":"a","name":"A","type":1,"api":"../api"}],"parses":[{"name":"P","type":"1","url":"../parse?url="}]}"#.utf8)
        let config = try SourceConfiguration.decode(data, baseURL: URL(string: "https://example.invalid/config/list.json"))
        XCTAssertEqual(config.sites.first?.parsers?.first?.url, "https://example.invalid/parse?url=")
        let source = try XCTUnwrap(config.sites.first)
        let restored = try JSONDecoder().decode(SourceSite.self, from: JSONEncoder().encode(source))
        XCTAssertEqual(restored.parsers, source.parsers)
    }
    func testParallelParsersReturnValidResultAndKeepSiteCredentialsOffParserRequests() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [ParserFixtureProtocol.self]
        let repository = ParserRepository(session: URLSession(configuration: configuration))
        let bad = try endpoint(#"{"name":"Bad","type":1,"url":"https://parser-fixture.invalid/bad?url="}"#)
        let good = try endpoint(#"{"name":"Good","type":1,"url":"https://parser-fixture.invalid/good?url="}"#)
        let result = try await repository.resolve(endpoints: [bad, good], input: "https://page.invalid/watch", headers: ["Authorization": "site-only"])
        XCTAssertEqual(result.url.absoluteString, "https://media.invalid/video.m3u8")
        XCTAssertEqual(result.headers["Authorization"], "site-only")
    }
}
