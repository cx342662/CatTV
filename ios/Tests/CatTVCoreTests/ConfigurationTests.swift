import XCTest
@testable import CatTVCore
final class ConfigurationTests: XCTestCase {
    func testNonObjectHeadersAreIgnoredAndAPIWhitespaceIsTrimmed() throws {
        for value in [JSONValue.string("ignored"), .array([]), .number(7), .bool(false), .null] {
            let source: JSONValue = .object(["sites": .array([.object(["key": .string("a"), "name": .string("A"), "type": .number(1), "api": .string(" \nhttps://example.invalid/api\t"), "header": value])])])
            let configuration = try SourceConfiguration.decode(Data(source.serialized.utf8))
            let site = try XCTUnwrap(configuration.sites.first)
            XCTAssertEqual(site.api, "https://example.invalid/api")
            XCTAssertTrue(site.headers.isEmpty)
        }
    }
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
