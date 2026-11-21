import XCTest
import CoreFoundation
@testable import CatTVCore

final class LiveCatalogTests: XCTestCase {
    private func source() throws -> LiveSource {
        let configuration = try SourceConfiguration.decode(Data(#"{"lives":[{"name":"Test Live","url":"https://example.invalid/list.m3u","header":{"User-Agent":"Source UA"}}]}"#.utf8))
        return try XCTUnwrap(LiveCatalog.sources(configuration.lives ?? []).first)
    }
    func testLiveOnlyConfigurationAndRelativeListURL() throws {
        let data = Data(#"{"lives":[{"name":"Live","url":"../list.m3u"}]}"#.utf8)
        let config = try SourceConfiguration.decode(data)
        XCTAssertTrue(config.sites.isEmpty)
        XCTAssertEqual(LiveCatalog.sources(config.lives ?? [], baseURL: URL(string: "https://example.invalid/config/a.json")).first?.address, "https://example.invalid/list.m3u")
    }
    func testM3UMetadataHeadersAndDuplicateBackups() throws {
        let playlist = "#EXTM3U\n#EXTINF:-1 tvg-id=\"id1\" tvg-logo=\"https://example.invalid/logo.png\" group-title=\"Group\",Channel\n#EXTVLCOPT:http-user-agent=VLC UA\nhttps://example.invalid/a.m3u8\n#EXTINF:-1 tvg-id=\"id1\" group-title=\"Group\",Channel\nhttps://example.invalid/b.m3u8|Referer=https%3A%2F%2Fsite.invalid%2F\n"
        let channels = try LiveCatalog.parse(playlist, source: source())
        XCTAssertEqual(channels.count, 1)
        XCTAssertEqual(channels.first?.streams.count, 2)
        XCTAssertEqual(channels.first?.epgID, "id1")
        XCTAssertEqual(channels.first?.streams.first?.headers["User-Agent"], "VLC UA")
        XCTAssertEqual(channels.first?.streams.last?.headers["Referer"], "https://site.invalid/")
    }
    func testTXTGroupsAndMultipleLines() throws {
        let channels = try LiveCatalog.parse("央视,#genre#\n频道,http://example.invalid/a.m3u8#https://example.invalid/b.m3u8\n", source: source())
        XCTAssertEqual(channels.first?.group, "央视")
        XCTAssertEqual(channels.first?.name, "频道")
        XCTAssertEqual(channels.first?.streams.count, 2)
    }
    func testInlineGroupsBecomePlayableWithoutCatalogAPICall() throws {
        let config = try SourceConfiguration.decode(Data(#"{"lives":[{"group":"Group","channels":[{"name":"Channel","urls":["https://example.invalid/a.m3u8"]}]}]}"#.utf8))
        let source = try XCTUnwrap(LiveCatalog.sources(config.lives ?? []).first)
        let channel = try XCTUnwrap(source.channels.first)
        let (video, site) = try channel.playback(stream: 0)
        XCTAssertEqual(site.type, 1)
        XCTAssertEqual(video.lines.first?.episodes.first?.address, "https://example.invalid/a.m3u8")
    }
    func testRejectsUnrecognizedPlaylistAndUnsupportedNativeProtocol() throws {
        XCTAssertThrowsError(try LiveCatalog.parse("not a playlist", source: source()))
        let channels = try LiveCatalog.parse("Group,#genre#\nRTSP,rtsp://example.invalid/live\n", source: source())
        let channel = try XCTUnwrap(channels.first)
        XCTAssertThrowsError(try channel.playback(stream: 0))
    }
    func testGB18030AndUTF16PlaylistDecoding() throws {
        let text = "央视,#genre#\n频道,http://example.invalid/live.m3u8\n"
        let encoding = String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.GB_18030_2000.rawValue)))
        XCTAssertEqual(try LiveCatalog.decodeText(XCTUnwrap(text.data(using: encoding))), text)
        XCTAssertEqual(try LiveCatalog.decodeText(XCTUnwrap(text.data(using: .utf16))), text)
    }
}
