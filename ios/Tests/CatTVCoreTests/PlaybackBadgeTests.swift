import XCTest
@testable import CatTVCore

final class PlaybackBadgeTests: XCTestCase {
    func testBuiltinBadgeRulesPreserveHDRPrecedence() throws {
        let matcher = try MediaBadgeMatcher(rules: MediaBadgeMatcher.builtInRules)
        let labels = matcher.match("2160p WEB-DL Dolby Vision HDR10+ HEVC Atmos 10bit").map(\.label)
        XCTAssertTrue(labels.contains("4K"))
        XCTAssertTrue(labels.contains("HDR10+"))
        XCTAssertFalse(labels.contains("HDR10"))
        XCTAssertFalse(labels.contains("HDR"))
        XCTAssertLessThanOrEqual(labels.count, 7)
    }
    func testBadgePackIgnoresDisabledAndInvalidRules() throws {
        let data = Data(#"{"filters":[{"name":"off","pattern":"x","isEnabled":false},{"name":"broken","pattern":"["},{"name":"valid","pattern":"uhd","groupId":"resolution","imageURL":"http://example.invalid/a.svg"}]}"#.utf8)
        let rules = try MediaBadgeMatcher.parsePack(data)
        XCTAssertEqual(rules.count, 1)
        XCTAssertNil(rules.first?.image)
        XCTAssertEqual(rules.first?.group, "resolution")
        XCTAssertEqual(try MediaBadgeMatcher(rules: rules).match("UHD").first?.label, "valid")
    }
    func testRejectsEmptyBadgePack() {
        XCTAssertThrowsError(try MediaBadgeMatcher.parsePack(Data(#"{"filters":[]}"#.utf8)))
    }
    func testPlaybackDescriptorRequiresSecondParserForStringOrNumberFlags() throws {
        for json in [#"{"url":"https://example.invalid/page","parse":1}"#, #"{"url":"https://example.invalid/page","jx":"1"}"#] {
            XCTAssertTrue(try JSONDecoder().decode(PlaybackDescriptor.self, from: Data(json.utf8)).requiresParser)
        }
        XCTAssertFalse(try JSONDecoder().decode(PlaybackDescriptor.self, from: Data(#"{"url":"https://example.invalid/video","parse":0}"#.utf8)).requiresParser)
    }
    func testExtensionlessDirectPlaybackURL() async throws {
        let config = try SourceConfiguration.decode(Data(#"{"sites":[{"key":"s","name":"S","type":1,"api":"https://example.invalid/api"}]}"#.utf8))
        let response = try JSONDecoder().decode(CatalogResponse.self, from: Data(#"{"list":[{"vod_id":1,"vod_name":"T","vod_play_url":"播放$https://example.invalid/stream?id=1"}]}"#.utf8))
        let site = try XCTUnwrap(config.sites.first)
        let episode = try XCTUnwrap(response.videos.first?.lines.first?.episodes.first)
        let resolved = try await PlaybackResolver().resolve(site: site, episode: episode, line: "test")
        XCTAssertEqual(resolved.absoluteString, "https://example.invalid/stream?id=1")
    }
    func testConfiguredPlayURLRoutesThroughWebParser() async throws {
        let config = try SourceConfiguration.decode(Data(#"{"sites":[{"key":"s","name":"S","type":1,"api":"https://example.invalid/api","playUrl":"https://parser.invalid/?url="}]}"#.utf8))
        let response = try JSONDecoder().decode(CatalogResponse.self, from: Data(#"{"list":[{"vod_id":1,"vod_name":"T","vod_play_url":"播放$https://example.invalid/page"}]}"#.utf8))
        let site = try XCTUnwrap(config.sites.first)
        let episode = try XCTUnwrap(response.videos.first?.lines.first?.episodes.first)
        let resolved = try await PlaybackResolver().resolveResource(site: site, episode: episode, line: "test")
        XCTAssertTrue(resolved.requiresWebParsing)
        XCTAssertEqual(resolved.url.absoluteString, "https://parser.invalid/?url=https://example.invalid/page")
    }
}
