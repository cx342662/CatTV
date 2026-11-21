import XCTest
@testable import CatTVCore
final class SubtitleTests: XCTestCase {
    func testSRTAndOffsetAndOverlappingCues() throws {
        let cues = try SubtitleDocument.decode(Data("1\r\n00:00:01,000 --> 00:00:03,000\r\n第一行\r\n\r\n2\r\n00:00:02,000 --> 00:00:04,000\r\n第二行\r\n".utf8))
        XCTAssertEqual(SubtitleDocument.text(at: 2, offset: 0, cues: cues), "第一行\n第二行")
        XCTAssertEqual(SubtitleDocument.text(at: 2, offset: 1, cues: cues), "第一行")
        XCTAssertEqual(SubtitleDocument.text(at: 4, offset: 0, cues: cues), "")
    }
    func testWebVTTSettingsAndMarkup() throws {
        let cues = try SubtitleDocument.decode(Data("WEBVTT\n\ncue\n00:01.500 --> 00:03.000 align:start\n<b>Hello</b> &amp; world\n".utf8))
        XCTAssertEqual(cues.first?.start, 1.5)
        XCTAssertEqual(cues.first?.text, "Hello & world")
    }
    func testRejectsInvalidTimingsAndUnsupportedFiles() {
        XCTAssertThrowsError(try SubtitleDocument.decode(Data("[Script Info]\nASS".utf8)))
        XCTAssertThrowsError(try SubtitleDocument.decode(Data("1\n00:70:00 --> 00:71:00\nbad".utf8)))
    }
}
