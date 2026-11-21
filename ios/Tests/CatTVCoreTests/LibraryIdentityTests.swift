import XCTest
@testable import CatTVCore

final class LibraryIdentityTests: XCTestCase {
    private func site(api: String = "https://example.invalid/a", key: String = "same", ext: String = "") throws -> SourceSite {
        var object: [String: Any] = ["key": key, "name": "Source", "type": 1, "api": api]
        if !ext.isEmpty { object["ext"] = ext }
        return try JSONDecoder().decode(SourceSite.self, from: JSONSerialization.data(withJSONObject: object))
    }
    func testSameKeyOnDifferentEndpointsDoesNotOverwriteHistory() throws {
        let a = try site()
        let b = try site(api: "https://example.invalid/b")
        XCTAssertNotEqual(LibraryIdentity.video(source: a, videoID: "1"), LibraryIdentity.video(source: b, videoID: "1"))
        XCTAssertNotEqual(LibraryIdentity.source(a), LibraryIdentity.source(try site(ext: "other")))
    }
    func testSnapshotRoundTripPreservesIdentityWithoutRewritingStoredData() throws {
        let a = try site()
        let restored = try JSONDecoder().decode(SourceSite.self, from: JSONEncoder().encode(a))
        XCTAssertEqual(LibraryIdentity.source(a), LibraryIdentity.source(restored))
        XCTAssertNotEqual(LibraryIdentity.video(source: a, videoID: "a:b"), LibraryIdentity.video(source: a, videoID: "b"))
        var renamed = a
        renamed.name = "Renamed"
        XCTAssertEqual(LibraryIdentity.source(a), LibraryIdentity.source(renamed))
    }
}
