import XCTest

final class FamilyCareSmokeTests: XCTestCase {
    @MainActor
    func testHackathonDemo() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        // Other UI tests may already have created the local demo profile in this Simulator.
        if app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Continue"))
            .firstMatch
            .waitForExistence(timeout: 3)
        {
            assertVisible("Care for both of you", in: app)
            tap("Continue", in: app)
            assertVisible("Create a child profile", in: app)
            tap("Open day plan", in: app)
        }
        assertVisible("Calendar", in: app)
        capture("FamilyCare-Home", in: app)

        tap("Calendar", in: app)
        assertVisible("November 2026", in: app)
        openVisitFromCalendar(in: app)
        assertVisible("Visit details", in: app)

        tap("Records", in: app)
        tap("Import synthetic clinic PDF", in: app)
        assertVisible("Synthetic clinic PDF is stored on this device.", in: app)
        tap("demo-immunizations.pdf", in: app, startsWith: true)
        tap("Open stored original", in: app)
        assertVisible("Stored clinic PDF preview", in: app)
        capture("FamilyCare-Records", in: app)

        tap("Assistant", in: app)
        assertVisible("Add to Today", in: app)
        capture("FamilyCare-Assistant", in: app)
        tap("Open original note", in: app)
        assertVisible("Original visit note", in: app)
        assertVisible("demo-recovery-note.txt", in: app)
        tap("Close", in: app)
        tap("Add to Today", in: app)

        let followUp = "Ask about recovery at today's visit"
        assertVisible(followUp, in: app, startsWith: true)
        app.terminate()
        app.launch()
        assertVisible("Calendar", in: app)
        assertVisible(followUp, in: app, startsWith: true)
        assertSingleAgendaItem(followUp, in: app)

        tap("Growing up", in: app)
        tap("Early infant", in: app, startsWith: true)
        assertVisible("Observations · 2–4 months", in: app)
        tap("Open activity", in: app)
        assertVisible("Reach and grasp play", in: app)

        tap("A4", in: app)
        let selectedA4 = app.descendants(matching: .any)
            .matching(labelPredicate("A4"))
            .matching(NSPredicate(format: "selected == true"))
            .firstMatch
        XCTAssertTrue(selectedA4.waitForExistence(timeout: 5), "The A4 paper option should be selected.")

        tap("Export PDF", in: app)
        let shareSheetContent = app.descendants(matching: .any).matching(
            NSPredicate(
                format: "label CONTAINS[c] %@ OR label == %@ OR label == %@",
                "family-care-demo-activity-reach-and-grasp", "Save to Files", "Print"
            )
        ).firstMatch
        XCTAssertTrue(
            shareSheetContent.waitForExistence(timeout: 10),
            "The native share sheet should show the generated activity or a file action."
        )
    }
}

@MainActor
private extension FamilyCareSmokeTests {
    func openVisitFromCalendar(in app: XCUIApplication) {
        let wellBaby = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label BEGINSWITH %@", "Well-baby visit"))
            .firstMatch
        if wellBaby.waitForExistence(timeout: 3) {
            wellBaby.tap()
            return
        }
        // A prior clarity test may have moved the well-baby visit off today.
        let movedDay = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@", "November 24"))
            .firstMatch
        if movedDay.waitForExistence(timeout: 3) {
            movedDay.tap()
        }
        if wellBaby.waitForExistence(timeout: 5) {
            wellBaby.tap()
            return
        }
        tap("Recovery follow-up", in: app, startsWith: true)
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

        for attempt in 0...4 {
            for candidate in [button, element] where candidate.exists && candidate.isHittable {
                candidate.tap()
                return
            }
            if attempt < 4 { app.swipeUp() }
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

    func assertSingleAgendaItem(_ title: String, in app: XCUIApplication) {
        let predicate = labelPredicate(title, startsWith: true)
        let buttons = app.buttons.matching(predicate).count
        let textLabels = app.staticTexts.matching(labelPredicate(title)).count
        let items = app.descendants(matching: .any).matching(predicate).count

        // Prefer a single accessibility representation of each agenda row.
        let count = buttons > 0 ? buttons : (textLabels > 0 ? textLabels : items)
        XCTAssertEqual(count, 1, "The follow-up should appear once after reopening the app.")
    }

    func capture(_ name: String, in app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
