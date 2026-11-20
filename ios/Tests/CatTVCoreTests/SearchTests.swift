import XCTest
@testable import CatTVCore

final class SearchTests: XCTestCase {
    func testSearchKeepsSourceIdentityAndReportsUnsupportedSources() async throws {
        let configuration = try SourceConfiguration.decode(Data(#"{"sites":[{"key":"a","name":"A","type":3,"api":"csp_A"},{"key":"disabled","name":"Disabled","type":3,"api":"csp_D","searchable":0},{"key":"b","name":"B","type":3,"api":"csp_B"}]}"#.utf8))
        let result = try await CatalogSearch().search(sites: configuration.sites, keyword: "test", concurrency: 2)
        XCTAssertEqual(result.map(\.id), ["a", "b"])
        XCTAssertTrue(result.allSatisfy { $0.failure != nil })
    }
    func testEmptySearchDoesNotDispatchSourceRequests() async throws {
        let configuration = try SourceConfiguration.decode(Data(#"{"sites":[{"key":"a","name":"A","type":3,"api":"csp_A"}]}"#.utf8))
        let result = try await CatalogSearch().search(sites: configuration.sites, keyword: "   ")
        XCTAssertTrue(result.isEmpty)
    }
}
