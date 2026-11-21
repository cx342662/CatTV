import XCTest
@testable import CatTVCore

final class PosterTests: XCTestCase {
    private func site() throws -> SourceSite {
        try JSONDecoder().decode(SourceSite.self, from: Data(#"{"key":"s","name":"S","type":1,"api":"https://example.invalid/api/index","header":{"User-Agent":"Poster UA","Referer":"https://example.invalid/","Authorization":"Bearer test","Cookie":"session=test","Host":"wrong.invalid"}}"#.utf8))
    }
    func testRelativePosterKeepsExplicitOriginCredentials() throws {
        let request = try PosterRepository.makeRequest(address: " ../poster.png ", site: site())
        XCTAssertEqual(request.url?.absoluteString, "https://example.invalid/poster.png")
        XCTAssertEqual(request.value(forHTTPHeaderField: "User-Agent"), "Poster UA")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer test")
        XCTAssertNil(request.value(forHTTPHeaderField: "Host"))
    }
    func testCDNAndPortChangesDoNotReceivePrivateHeaders() throws {
        for address in ["https://cdn.invalid/poster.png", "https://example.invalid:8443/poster.png", "http://example.invalid/poster.png"] {
            let request = try PosterRepository.makeRequest(address: address, site: site())
            XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
            XCTAssertNil(request.value(forHTTPHeaderField: "Cookie"))
            XCTAssertEqual(request.value(forHTTPHeaderField: "Referer"), "https://example.invalid/")
        }
    }
    func testEmptyAndNonHTTPPosterAddressesAreRejected() throws {
        for address in ["", "  ", "file:///private/image.png", "data:image/png;base64,test"] {
            XCTAssertThrowsError(try PosterRepository.makeRequest(address: address, site: site()))
        }
    }
    func testRedirectDropsOriginCredentialsAndKeepsPublicHeaders() throws {
        var request = try PosterRepository.makeRequest(address: "https://example.invalid/poster.png", site: site())
        request.url = URL(string: "https://cdn.invalid/poster.png")
        let redirected = PosterRepository.redirectRequest(request, origin: URL(string: "https://example.invalid/poster.png"))
        XCTAssertNil(redirected.value(forHTTPHeaderField: "Authorization"))
        XCTAssertNil(redirected.value(forHTTPHeaderField: "Cookie"))
        XCTAssertEqual(redirected.value(forHTTPHeaderField: "User-Agent"), "Poster UA")
    }
}
