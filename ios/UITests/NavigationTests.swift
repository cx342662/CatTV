import XCTest

final class NavigationTests: XCTestCase {
    func testNavigationAndInvalidConfigurationValidation() throws {
        let server = try FixtureServer()
        defer { server.stop() }
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.tabBars.buttons["首页"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.tabBars.buttons["记录"].exists)
        XCTAssertTrue(app.tabBars.buttons["追剧"].exists)
        XCTAssertTrue(app.tabBars.buttons["我的"].exists)
        capture(app, name: "home-empty-state")
        app.tabBars.buttons["我的"].tap()
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
        app.tabBars.buttons["记录"].tap()
        XCTAssertTrue(app.staticTexts["暂无播放记录"].waitForExistence(timeout: 5))
        capture(app, name: "records-empty-state")
        app.tabBars.buttons["追剧"].tap()
        XCTAssertTrue(app.staticTexts["暂无追剧"].waitForExistence(timeout: 5))
        app.tabBars.buttons["我的"].tap()
        let configURL = app.textFields["配置地址"]
        XCTAssertTrue(configURL.waitForExistence(timeout: 5))
        configURL.tap()
        configURL.typeText(server.baseURL + "/config.json")
        app.buttons["导入配置"].tap()
        app.tabBars.buttons["首页"].tap()
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
    }
    private func capture(_ app: XCUIApplication, name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
