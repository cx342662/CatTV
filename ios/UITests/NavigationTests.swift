import XCTest

final class NavigationTests: XCTestCase {
    func testNavigationAndInvalidConfigurationValidation() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.tabBars.buttons["首页"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.tabBars.buttons["记录"].exists)
        XCTAssertTrue(app.tabBars.buttons["追剧"].exists)
        XCTAssertTrue(app.tabBars.buttons["我的"].exists)
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
        app.navigationBars.buttons["取消"].tap()
        XCTAssertTrue(app.buttons["手动新增"].waitForExistence(timeout: 5))
        app.tabBars.buttons["记录"].tap()
        XCTAssertTrue(app.staticTexts["暂无播放记录"].waitForExistence(timeout: 5))
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "records-empty-state"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
