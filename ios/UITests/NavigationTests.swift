import XCTest

final class NavigationTests: XCTestCase {
    func testNavigationAndInvalidConfigurationValidation() {
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
        XCTAssertTrue(app.navigationBars["新增配置"].exists)
        XCTAssertTrue(name.exists)
        capture(app, name: "configuration-validation")
        app.navigationBars.buttons["取消"].tap()
        XCTAssertTrue(app.buttons["手动新增"].waitForExistence(timeout: 5))
        app.tabBars.buttons["记录"].tap()
        XCTAssertTrue(app.staticTexts["暂无播放记录"].waitForExistence(timeout: 5))
        capture(app, name: "records-empty-state")
        app.tabBars.buttons["追剧"].tap()
        XCTAssertTrue(app.staticTexts["暂无追剧"].waitForExistence(timeout: 5))
    }
    private func capture(_ app: XCUIApplication, name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
