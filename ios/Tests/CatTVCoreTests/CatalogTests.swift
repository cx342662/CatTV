import XCTest
@testable import CatTVCore

final class CatalogTests: XCTestCase {
    func testNumericIDsAndSeparatedEpisodes() throws {
        let data = Data(#"{"class":[{"type_id":2,"type_name":"剧集"}],"list":[{"vod_id":123,"vod_name":"测试","vod_play_from":"一线$$$二线","vod_play_url":"第一集$https://example.invalid/1.m3u8#第二集$https://example.invalid/2.m3u8$$$https://example.invalid/3.mp4"}]}"#.utf8)
        let response = try JSONDecoder().decode(CatalogResponse.self, from: data)
        XCTAssertEqual(response.categories.first?.id, "2")
        let video = try XCTUnwrap(response.videos.first)
        XCTAssertEqual(video.id, "123")
        XCTAssertEqual(video.lines.count, 2)
        XCTAssertEqual(video.lines[0].episodes.map(\.name), ["第一集", "第二集"])
        XCTAssertEqual(video.lines[1].episodes.first?.address, "https://example.invalid/3.mp4")
    }
    func testMalformedListIsNotReportedAsEmptySuccess() {
        let data = Data(#"{"list":"error"}"#.utf8)
        XCTAssertThrowsError(try JSONDecoder().decode(CatalogResponse.self, from: data))
    }
    func testSpiderIsNotSentToHTTPAPI() async {
        let data = Data(#"{"sites":[{"key":"s","name":"Spider","type":3,"api":"csp_Test"}]}"#.utf8)
        do {
            let configuration = try SourceConfiguration.decode(data)
            let site = try XCTUnwrap(configuration.sites.first)
            _ = try await CatalogRepository().request(site: site)
            XCTFail("Spider must require an adapter")
        } catch SourceError.unsupportedSource(let type) {
            XCTAssertEqual(type, 3)
        } catch { XCTFail("Unexpected error: \(error)") }
    }
}
