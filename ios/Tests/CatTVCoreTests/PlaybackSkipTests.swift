import XCTest
@testable import CatTVCore

final class PlaybackSkipTests: XCTestCase {
    func testResumeBeyondIntroIsPreserved() {
        let settings = PlaybackSkipSettings(intro: 30, outro: 20)
        XCTAssertEqual(settings.introTarget(position: 0, duration: 300), 30)
        XCTAssertNil(settings.introTarget(position: 60, duration: 300))
        XCTAssertFalse(settings.shouldAdvance(position: 279, duration: 300))
        XCTAssertTrue(settings.shouldAdvance(position: 280, duration: 300))
    }
    func testLiveUnknownAndShortMediaDoNotSkip() {
        let settings = PlaybackSkipSettings(intro: 30, outro: 20)
        for duration in [Double.nan, .infinity, 0, 40, 50] {
            XCTAssertNil(settings.introTarget(position: 0, duration: duration))
            XCTAssertFalse(settings.shouldAdvance(position: 35, duration: duration))
        }
        XCTAssertFalse(settings.shouldAdvance(position: .nan, duration: 300))
        XCTAssertFalse(PlaybackSkipSettings().shouldAdvance(position: 300, duration: 300))
    }
    func testClampingAndPersistence() throws {
        let settings = PlaybackSkipSettings(intro: -1, outro: .infinity)
        XCTAssertEqual(settings, PlaybackSkipSettings())
        let valid = PlaybackSkipSettings(intro: 900, outro: 10)
        XCTAssertEqual(valid.intro, 600)
        XCTAssertEqual(try JSONDecoder().decode(PlaybackSkipSettings.self, from: JSONEncoder().encode(valid)), valid)
    }
}
