import XCTest
@testable import CatTVCore
final class ConfigurationTests: XCTestCase {
    func testRejectsDuplicateSourceKeys() throws {
        let data = Data(#"{"sites":[{"key":"a","name":"A","type":3,"api":"csp_Test"},{"key":"a","name":"B","type":1,"api":"https://example.invalid"}]}"#.utf8)
        XCTAssertThrowsError(try SourceConfiguration.decode(data))
    }
    func testRetainsSpiderContract() throws {
        let data = Data(#"{"spider":"./spider.jar","sites":[{"key":"a","name":"A","type":3,"api":"csp_Test"}]}"#.utf8)
        let configuration = try SourceConfiguration.decode(data)
        XCTAssertEqual(configuration.spider, "./spider.jar")
        XCTAssertEqual(configuration.sites.first?.type, 3)
    }
}
