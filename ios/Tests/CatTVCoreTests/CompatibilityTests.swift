import XCTest
@testable import CatTVCore

final class CompatibilityTests: XCTestCase {
    private func site(type: Int = 4, extra: String = "") throws -> SourceSite {
        let data = Data("{\"sites\":[{\"key\":\"test\",\"name\":\"测试\",\"type\":\(type),\"api\":\"https://example.invalid/api?token=kept&pg=old\"\(extra)}]}".utf8)
        return try XCTUnwrap(SourceConfiguration.decode(data).sites.first)
    }
    func testXMLCategoriesCDATAAndLines() throws {
        let xml = #"<rss><class><ty id="2">剧集</ty></class><list page="2" pagecount="7"><video><id>42</id><name>测试&amp;名称</name><pic>https://example.invalid/a.jpg</pic><note>完结</note><des><![CDATA[<p>简介</p>]]></des><dl><dd flag="一线"><![CDATA[第1集$https://example.invalid/a.m3u8#第2集$https://example.invalid/b.m3u8]]></dd><dd flag="二线">备用$https://example.invalid/c.mp4</dd></dl></video></list></rss>"#
        let result = try XMLCatalogDecoder.decode(Data(xml.utf8))
        XCTAssertEqual(result.categories.first?.id, "2")
        XCTAssertEqual(result.page, 2)
        XCTAssertEqual(result.pageCount, 7)
        XCTAssertEqual(result.videos.first?.name, "测试&名称")
        XCTAssertEqual(result.videos.first?.content, "<p>简介</p>")
        XCTAssertEqual(result.videos.first?.lines.count, 2)
        XCTAssertEqual(result.videos.first?.lines.first?.episodes.count, 2)
    }
    func testRejectsMalformedXMLAndHTML() {
        XCTAssertThrowsError(try XMLCatalogDecoder.decode(Data("<rss><list>".utf8)))
        XCTAssertThrowsError(try XMLCatalogDecoder.decode(Data("<html><body>error</body></html>".utf8)))
    }
    func testRelativeAPIAndPrimitiveHeaders() throws {
        let data = Data(#"{"sites":[{"key":"s","name":"S","type":1,"api":"../api","header":{"User-Agent":"CatTV","X-Number":4,"X-Bool":true,"X-Nested":{},"X-Bad":"a\r\nb"}}]}"#.utf8)
        let config = try SourceConfiguration.decode(data, baseURL: URL(string: "https://example.invalid/config/list.json"))
        let source = try XCTUnwrap(config.sites.first)
        XCTAssertEqual(source.api, "https://example.invalid/api")
        XCTAssertEqual(source.headers["X-Number"], "4")
        XCTAssertEqual(source.headers["X-Bool"], "true")
        XCTAssertNil(source.headers["X-Nested"])
        XCTAssertNil(source.headers["X-Bad"])
    }
    func testGETReplacesPaginationAndPreservesQuery() throws {
        let request = try CatalogRepository.makeRequest(site: site(), address: site().api, parameters: ["pg": "2", "wd": "a&b+ c"])
        let components = try XCTUnwrap(URLComponents(url: XCTUnwrap(request.url), resolvingAgainstBaseURL: false))
        XCTAssertEqual(components.queryItems?.filter { $0.name == "pg" }.count, 1)
        XCTAssertEqual(components.queryItems?.first { $0.name == "token" }?.value, "kept")
        XCTAssertEqual(components.queryItems?.first { $0.name == "wd" }?.value, "a&b+ c")
    }
    func testLongExtensionUsesPOSTAndEscapesPlus() throws {
        let source = try site()
        let request = try CatalogRepository.makeRequest(site: source, address: source.api, parameters: ["extend": String(repeating: "x", count: 1001), "ext": "a+b/c="])
        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertTrue(String(decoding: try XCTUnwrap(request.httpBody), as: UTF8.self).contains("ext=a%2Bb"))
        XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Type"), "application/x-www-form-urlencoded; charset=utf-8")
    }
    func testFilterBase64AndXMLAction() throws {
        let query = CatalogQuery(category: "2", page: 3, filters: ["year": "2026"])
        let params = try query.parameters(type: 4)
        let decoded = try XCTUnwrap(Data(base64Encoded: XCTUnwrap(params["ext"])))
        XCTAssertEqual(try JSONDecoder().decode([String: String].self, from: decoded), ["year": "2026"])
        XCTAssertEqual(try query.parameters(type: 0)["ac"], "videolist")
        XCTAssertEqual(params["pg"], "3")
    }
    func testSingleAndArrayFilterDefinitions() throws {
        let data = Data(#"{"class":[],"filters":{"1":{"key":"year","name":"年份","value":[{"n":"全部","v":""}]},"2":[{"key":"area","name":"地区","value":[]}]},"pagecount":"9"}"#.utf8)
        let result = try JSONDecoder().decode(CatalogResponse.self, from: data)
        XCTAssertEqual(result.filters["1"]?.first?.key, "year")
        XCTAssertEqual(result.filters["2"]?.first?.key, "area")
        XCTAssertEqual(result.pageCount, 9)
    }
    func testConfigurationCollectionPreservesUnknownFieldsAndSelection() throws {
        let raw = Data(#"{"unknown":{"secret":"user-only"},"sites":[{"key":"s","name":"S","type":1,"api":"https://example.invalid"}]}"#.utf8)
        let one = try ConfigurationDocument(name: "one", origin: nil, data: raw)
        let two = try ConfigurationDocument(name: "two", origin: nil, data: raw)
        var collection = ConfigurationCollection()
        try collection.upsert(one)
        try collection.selectSource("s")
        try collection.upsert(two)
        try collection.activate(one.id)
        let restored = try JSONDecoder().decode(ConfigurationCollection.self, from: JSONEncoder().encode(collection))
        XCTAssertEqual(restored.active?.data, raw)
        XCTAssertEqual(restored.sourceKeys[one.id.uuidString], "s")
        collection.remove(one.id)
        XCTAssertEqual(collection.activeID, two.id)
        collection.remove(two.id)
        XCTAssertNil(collection.activeID)
    }
    func testInvalidConfigurationCannotReplaceWorkingCollection() throws {
        let raw = Data(#"{"sites":[{"key":"s","name":"S","type":1,"api":"https://example.invalid"}]}"#.utf8)
        var collection = ConfigurationCollection()
        var document = try ConfigurationDocument(name: "good", origin: nil, data: raw)
        try collection.upsert(document)
        let before = collection
        document.data = Data("broken".utf8)
        XCTAssertThrowsError(try collection.upsert(document))
        XCTAssertEqual(collection, before)
    }
    func testStringTypeAndTimeoutCompatibility() throws {
        let data = Data(#"{"sites":[{"key":"s","name":"S","type":"4","timeout":"45","api":"https://example.invalid"}]}"#.utf8)
        let site = try XCTUnwrap(SourceConfiguration.decode(data).sites.first)
        XCTAssertEqual(site.type, 4)
        XCTAssertEqual(site.timeout, 45)
    }
    func testGETEscapesLiteralPlusForFormDecodingServers() throws {
        let source = try site()
        let request = try CatalogRepository.makeRequest(site: source, address: source.api, parameters: ["ext": "a+b="])
        XCTAssertTrue(try XCTUnwrap(request.url?.absoluteString).contains("a%2Bb"))
    }
}
