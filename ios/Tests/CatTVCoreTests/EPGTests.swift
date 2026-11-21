import XCTest
@testable import CatTVCore

final class EPGTests: XCTestCase {
    private let date = Date(timeIntervalSince1970: 1795190400)
    func testTemplateEncodingAndXMLURLPreservation() throws {
        let url = try EPGDecoder.requestURL(address: "https://example.invalid/guide?name={name}&date={date}", channel: "CCTV 5+&", date: date)
        let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        XCTAssertEqual(components.queryItems?.first?.value, "CCTV 5+&")
        XCTAssertEqual(try EPGDecoder.requestURL(address: "https://example.invalid/tv.xml?token=a", channel: "A", date: date).absoluteString, "https://example.invalid/tv.xml?token=a")
        XCTAssertThrowsError(try EPGDecoder.requestURL(address: "file:///guide", channel: "A", date: date))
    }
    func testJSONAliasesMidnightAndUnavailableTitles() throws {
        let data = Data(#"{"data":{"list":[{"name":"Night","start_time":"23:30","end_time":"00:30"},{"title":"暂无节目","start":"10:00","end":"11:00"},{"title":"Bad","start":"wrong","end":"11:00"}]}}"#.utf8)
        let result = try EPGDecoder.decode(data, channelNames: ["A"], date: date)
        XCTAssertEqual(result.count, 1)
        let programme = try XCTUnwrap(result.first)
        XCTAssertEqual(programme.end.timeIntervalSince(programme.start), 3600)
        XCTAssertTrue(programme.isCurrent(at: programme.start.addingTimeInterval(60)))
        XCTAssertFalse(programme.isCurrent(at: programme.end))
    }
    func testXMLTVDisplayNameAndTimezoneAndDateOverlap() throws {
        let day = EPGDecoder.formatter("yyyyMMdd").string(from: date)
        let xml = "<tv><channel id=\"one\"><display-name>CCTV-1 综合</display-name></channel><programme channel=\"one\" start=\"\(day)120000 +0800\" stop=\"\(day)130000 +0800\"><title><![CDATA[新闻 & 资讯]]></title></programme><programme channel=\"two\" start=\"\(day)120000 +0800\" stop=\"\(day)130000 +0800\"><title>Other</title></programme></tv>"
        let result = try EPGDecoder.decode(Data(xml.utf8), channelNames: ["CCTV1"], date: date)
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result.first?.title, "新闻 & 资讯")
        let programme = try XCTUnwrap(result.first)
        XCTAssertEqual(programme.end.timeIntervalSince(programme.start), 3600)
        XCTAssertEqual(EPGDecoder.normalizedName("CCTV-5+ 体育赛事"), "CCTV5+")
    }
    func testMalformedXMLAndUnrelatedChannels() throws {
        XCTAssertThrowsError(try EPGDecoder.decode(Data("<tv><broken>".utf8), channelNames: ["A"], date: date))
        XCTAssertThrowsError(try EPGDecoder.decode(Data("<rss/>".utf8), channelNames: ["A"], date: date))
        XCTAssertTrue(try EPGDecoder.decode(Data("<tv/>".utf8), channelNames: ["A"], date: date).isEmpty)
    }
}
