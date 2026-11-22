import XCTest

/// Simulator verification for #40 — selected day, visit owner, reschedule, restart.
final class CalendarVisitClarityTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
        // Each scenario needs a clean local demo profile.
        XCUIApplication().launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
    }

    @MainActor
    func testCalendarVisitRescheduleAndRestart() {
        let app = launchCleanApp()

        completeOnboarding(in: app)
        assertVisible("Calendar", in: app)
        capture("40-home-day-plan", in: app)

        // Open visit from Home first.
        tap("Well-baby visit", in: app, startsWith: true)
        assertVisible("Visit details", in: app)
        assertVisible("Maya", in: app, startsWith: true)
        assertVisible("Booking status", in: app, startsWith: true)
        assertVisible("Reschedule", in: app)
        capture("40-visit-from-home", in: app)
        tap("Back", in: app)

        tap("Calendar", in: app)
        assertVisible("Selected day", in: app)
        assertVisible("November 2026", in: app)
        capture("40-calendar-selected-day", in: app)

        // Empty day: prefer a cell that reports zero events.
        let emptyDay = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@", ", 0 events"))
            .firstMatch
        XCTAssertTrue(emptyDay.waitForExistence(timeout: 5), "Expected an empty day cell in November.")
        emptyDay.tap()
        assertVisible("No visits or reminders on this day", in: app, startsWith: true)
        capture("40-calendar-empty-day", in: app)

        // Return to today and open the child visit.
        let todayCell = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@", "November 23"))
            .firstMatch
        XCTAssertTrue(todayCell.waitForExistence(timeout: 5), "Today (November 23) should be on the month grid.")
        todayCell.tap()
        assertVisible("Maya", in: app)
        assertVisible("Visit · Booked", in: app, startsWith: true)
        tap("Well-baby visit", in: app, startsWith: true)
        assertVisible("Visit details", in: app)
        assertVisible("Date ·", in: app, startsWith: true)
        assertVisible("Time ·", in: app, startsWith: true)
        assertVisible("Booking status · Booked", in: app)

        tap("Move 1 day later", in: app)
        tap("Save new date & time", in: app)
        assertVisible("Rescheduled", in: app, startsWith: true)
        capture("40-visit-rescheduled", in: app)
        tap("Back", in: app)

        assertVisible("Selected day", in: app)
        assertVisible("Well-baby visit", in: app, startsWith: true)
        // Focused row is announced as one accessibility node that includes "Open visit".
        let focused = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@ AND label CONTAINS[c] %@", "Well-baby visit", "Open visit"))
            .firstMatch
        XCTAssertTrue(focused.waitForExistence(timeout: 8), "The open visit should stay highlighted on Calendar.")
        capture("40-calendar-after-reschedule", in: app)

        app.terminate()
        app.launch()
        assertVisible("Calendar", in: app)
        tap("Calendar", in: app)
        let movedDay = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@", "November 24"))
            .firstMatch
        XCTAssertTrue(movedDay.waitForExistence(timeout: 8), "November 24 should remain on the month grid.")
        movedDay.tap()
        assertVisible("Well-baby visit", in: app, startsWith: true)
        capture("40-calendar-after-restart", in: app)

        // Put the visit back on the demo day so later suite tests still find it on today.
        tap("Well-baby visit", in: app, startsWith: true)
        tap("Use today", in: app)
        let save = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@ OR label == %@", "Save new date & time", "Save schedule"))
            .firstMatch
        XCTAssertTrue(save.waitForExistence(timeout: 5), "Expected a schedule save action.")
        save.tap()
        tap("Back", in: app)
    }

    @MainActor
    func testLargerDynamicTypeStillShowsSelectedDayAndReschedule() {
        let app = launchCleanApp(extraArguments: [
            "-UIPreferredContentSizeCategoryName", "UIContentSizeCategoryAccessibilityMedium",
        ])
        completeOnboarding(in: app)
        tap("Calendar", in: app)
        assertVisible("Selected day", in: app)
        // Prefer the mother visit so this still works after another test rescheduled the well-baby day.
        let visit = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH %@ OR label BEGINSWITH %@", "Recovery follow-up", "Well-baby visit"))
            .firstMatch
        XCTAssertTrue(visit.waitForExistence(timeout: 8), "A visit row should remain on the selected day.")
        visit.tap()
        assertVisible("Reschedule", in: app)
        // With large Dynamic Type the primary schedule action may sit below the fold.
        let saveSchedule = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@ OR label == %@", "Save schedule", "Save new date & time"))
            .firstMatch
        for _ in 0..<4 where !saveSchedule.exists || !saveSchedule.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(saveSchedule.waitForExistence(timeout: 8), "Reschedule should expose a save schedule action.")
        capture("40-large-text-visit", in: app)
    }
}

@MainActor
private extension CalendarVisitClarityTests {
    func launchCleanApp(extraArguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"] + extraArguments
        // Wipe the app container so onboarding runs again.
        app.launch()
        app.terminate()
        // Reinstall path isn't available from XCTest; delete via springboard is heavy.
        // Prefer launching after uninstall done outside, but also handle restored profile:
        app.launch()
        return app
    }

    func completeOnboarding(in app: XCUIApplication) {
        if app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Continue"))
            .firstMatch
            .waitForExistence(timeout: 3)
        {
            tap("Continue", in: app)
            assertVisible("Create a child profile", in: app)
            tap("Open day plan", in: app)
        } else {
            // Profile already present from a previous run in this simulator.
            assertVisible("Calendar", in: app)
        }
    }

    func labelPredicate(_ label: String, startsWith: Bool = false) -> NSPredicate {
        NSPredicate(format: startsWith ? "label BEGINSWITH %@" : "label == %@", label)
    }

    func tap(
        _ label: String,
        in app: XCUIApplication,
        startsWith: Bool = false,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let predicate = labelPredicate(label, startsWith: startsWith)
        let button = app.buttons.matching(predicate).firstMatch
        let element = app.descendants(matching: .any).matching(predicate).firstMatch
        _ = element.waitForExistence(timeout: 3)

        for attempt in 0...5 {
            for candidate in [button, element] where candidate.exists && candidate.isHittable {
                candidate.tap()
                return
            }
            if attempt < 5 { app.swipeUp() }
        }
        XCTFail("Could not tap ‘\(label)’.", file: file, line: line)
    }

    func assertVisible(
        _ label: String,
        in app: XCUIApplication,
        startsWith: Bool = false,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let element = app.descendants(matching: .any)
            .matching(labelPredicate(label, startsWith: startsWith))
            .firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 10), "Could not find ‘\(label)’.", file: file, line: line)
    }

    func capture(_ name: String, in app: XCUIApplication) {
        let shot = app.screenshot()
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
