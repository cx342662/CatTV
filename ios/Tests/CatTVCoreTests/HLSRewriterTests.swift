import XCTest
@testable import CatTVCore

final class HLSRewriterTests: XCTestCase {
    func testRewritesVariantSegmentKeyAndMapAgainstRedirectedBase() throws {
        let playlist = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"../key?k=1\"\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXT-X-STREAM-INF:BANDWIDTH=100\nchild/main.m3u8\nhttps://cdn.example.invalid/segment.ts\n"
        var urls: [URL] = []
        let rewritten = try HLSPlaylistRewriter.rewrite(Data(playlist.utf8), baseURL: XCTUnwrap(URL(string: "https://example.invalid/path/master.m3u8"))) { url in
            urls.append(url)
            return "http://127.0.0.1:9999/\(urls.count)"
        }
        XCTAssertEqual(urls.map(\.absoluteString), ["https://example.invalid/key?k=1", "https://example.invalid/path/init.mp4", "https://example.invalid/path/child/main.m3u8", "https://cdn.example.invalid/segment.ts"])
        let text = String(decoding: rewritten, as: UTF8.self)
        XCTAssertTrue(text.contains(#"URI="http://127.0.0.1:9999/1""#))
        XCTAssertTrue(text.contains("http://127.0.0.1:9999/3"))
        XCTAssertFalse(text.contains("https://cdn.example.invalid"))
    }
    func testPreservesDataAndFairPlayKeyURIs() throws {
        let input = Data("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://asset\"\n#EXT-X-KEY:METHOD=AES-128,URI=\"data:abc\"".utf8)
        let output = try HLSPlaylistRewriter.rewrite(input, baseURL: XCTUnwrap(URL(string: "https://example.invalid/a"))) { _ in XCTFail("Inline and DRM schemes must not be proxied"); return "bad" }
        XCTAssertEqual(output, input)
    }
    func testRejectsHTMLDisguisedAsPlaylist() {
        XCTAssertThrowsError(try HLSPlaylistRewriter.rewrite(Data("<html>error</html>".utf8), baseURL: XCTUnwrap(URL(string: "https://example.invalid/a"))) { $0.absoluteString })
    }
    func testRemovesUTF8BOMBeforeClassifyingPlaylistLines() throws {
        let input = Data("\u{FEFF}#EXTM3U\nsegment.ts".utf8)
        let output = try HLSPlaylistRewriter.rewrite(input, baseURL: XCTUnwrap(URL(string: "https://example.invalid/a/master.m3u8"))) { $0.absoluteString }
        XCTAssertEqual(String(decoding: output, as: UTF8.self), "#EXTM3U\nhttps://example.invalid/a/segment.ts")
    }
}
