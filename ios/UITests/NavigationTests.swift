import XCTest

final class NavigationTests: XCTestCase {
    func testNavigationAndInvalidConfigurationValidation() throws {
        let server = try FixtureServer()
        defer { server.stop() }
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["nav-home"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons["nav-records"].exists)
        XCTAssertTrue(app.buttons["nav-following"].exists)
        XCTAssertTrue(app.buttons["nav-settings"].exists)
        capture(app, name: "home-empty-state")
        app.buttons["nav-settings"].tap()
        XCTAssertTrue(app.buttons["手动新增"].waitForExistence(timeout: 5))
        app.buttons["手动新增"].tap()
        let name = app.textFields["配置名称"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap()
        name.typeText("Invalid Test")
        let editor = app.textViews.firstMatch
        editor.tap()
        editor.typeText("broken-json")
        app.navigationBars.buttons["保存"].tap()
        XCTAssertTrue(app.alerts["配置错误"].waitForExistence(timeout: 5))
        capture(app, name: "configuration-validation")
        app.alerts["配置错误"].buttons["确定"].tap()
        XCTAssertTrue(app.navigationBars["新增配置"].exists)
        XCTAssertTrue(name.exists)
        app.navigationBars.buttons["取消"].tap()
        XCTAssertTrue(app.buttons["手动新增"].waitForExistence(timeout: 5))
        app.buttons["nav-records"].tap()
        XCTAssertTrue(app.staticTexts["暂无播放记录"].waitForExistence(timeout: 5))
        capture(app, name: "records-empty-state")
        app.buttons["nav-following"].tap()
        XCTAssertTrue(app.staticTexts["暂无追剧"].waitForExistence(timeout: 5))
        app.buttons["nav-settings"].tap()
        let configURL = app.textFields["配置地址"]
        XCTAssertTrue(configURL.waitForExistence(timeout: 5))
        configURL.tap()
        configURL.typeText(server.baseURL + "/config.json")
        app.buttons["导入配置"].tap()
        app.buttons["nav-home"].tap()
        let video = app.buttons["video-fixture-1"]
        XCTAssertTrue(video.waitForExistence(timeout: 15))
        capture(app, name: "catalog-from-ci-fixture-server")
        video.tap()
        let episode = app.buttons["episode-0"]
        XCTAssertTrue(episode.waitForExistence(timeout: 15))
        episode.tap()
        let progress = app.staticTexts["playback-progress"]
        let advances = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label MATCHES %@", "播放进度：[1-9][0-9]*秒"), object: progress)
        XCTAssertEqual(XCTWaiter.wait(for: [advances], timeout: 30), .completed)
        capture(app, name: "web-parser-proxy-native-playback-ci-fixture")
        app.buttons["停止播放"].tap()
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.buttons["nav-records"].waitForExistence(timeout: 5))
        app.buttons["nav-records"].tap()
        app.buttons["nav-live"].tap()
        let channel = app.buttons["CI Live Channel"]
        XCTAssertTrue(channel.waitForExistence(timeout: 15))
        channel.tap()
        let liveProgress = app.staticTexts["live-playback-progress"]
        let liveAdvances = XCTNSPredicateExpectation(predicate: NSPredicate(format: "label MATCHES %@", "播放进度：[1-9][0-9]*秒"), object: liveProgress)
        XCTAssertEqual(XCTWaiter.wait(for: [liveAdvances], timeout: 30), .completed)
        capture(app, name: "live-channel-native-playback-ci-fixture")
        app.buttons["节目单"].tap()
        XCTAssertTrue(app.staticTexts["CI Programme"].waitForExistence(timeout: 15))
        capture(app, name: "live-programme-guide-ci-fixture")
        app.buttons["关闭节目单"].tap()
        app.buttons["停止播放"].tap()
        app.buttons["返回频道"].tap()
        app.buttons["关闭"].tap()
    }
    private func capture(_ app: XCUIApplication, name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
