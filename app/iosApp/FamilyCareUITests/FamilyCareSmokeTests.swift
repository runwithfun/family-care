import XCTest

final class FamilyCareSmokeTests: XCTestCase {
    @MainActor
    func testHackathonDemo() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        assertVisible("Care for both of you", in: app)
        tap("Continue", in: app)
        assertVisible("Create a child profile", in: app)

        for _ in 0..<2 {
            tap("Add photo", in: app)
            cancelPhotoPicker(in: app)
        }

        let profileNote = "Likes songs."
        guard let notes = profileInput("Notes about the child (optional)", in: app) else { return }
        notes.tap()
        notes.typeText(profileNote)
        tap("Open day plan", in: app)
        assertVisible("Calendar", in: app)
        assertVisible("8 weeks old", in: app)
        capture("FamilyCare-Home", in: app)

        tap("View profile", in: app)
        assertVisible("Born 2026-09-28", in: app)
        assertVisible(profileNote, in: app)
        tap("Settings", in: app)
        assertVisible("Settings", in: app)
        tap("Edit profile", in: app)
        guard let name = profileInput("Child's name", in: app, currentValue: "Maya") else { return }
        name.tap()
        name.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: "Maya".count) + "Nora")
        tap("Save profile", in: app)
        assertVisible("Nora", in: app)
        tap("Home", in: app)

        tap("Calendar", in: app)
        assertVisible("November 2026", in: app)
        tap("Well-baby visit", in: app, startsWith: true)
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
        assertVisible("Nora", in: app)
        assertVisible("8 weeks old", in: app)
        assertVisible(followUp, in: app, startsWith: true)
        assertSingleAgendaItem(followUp, in: app)
        tap("View profile", in: app)
        assertVisible("Born 2026-09-28", in: app)
        assertVisible(profileNote, in: app)

        assertActivityExport("A4", in: app)
        // Each format reaches a real native sheet. Relaunch without invoking a share action.
        app.terminate()
        app.launch()
        assertVisible("Calendar", in: app)
        assertActivityExport("US Letter", in: app)
    }
}

@MainActor
private extension FamilyCareSmokeTests {
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

    func cancelPhotoPicker(in app: XCUIApplication) {
        let labels = ["Cancel", "Close"]
        let nativeCancel = app.buttons.matching(
            NSPredicate(format: "label IN %@ OR identifier IN %@", labels, labels)
        ).firstMatch
        XCTAssertTrue(nativeCancel.waitForExistence(timeout: 15), "The native picker should offer Cancel or Close.")
        nativeCancel.tap()
        XCTAssertTrue(nativeCancel.waitForNonExistence(timeout: 10), "The native picker should dismiss.")

        let addPhoto = app.descendants(matching: .any)
            .matching(labelPredicate("Add photo"))
            .matching(NSPredicate(format: "enabled == true"))
            .firstMatch
        XCTAssertTrue(addPhoto.waitForExistence(timeout: 5), "Add photo should become available again.")
        XCTAssertFalse(app.staticTexts["Preparing photo…"].exists)
    }

    func profileInput(_ label: String, in app: XCUIApplication, currentValue: String? = nil) -> XCUIElement? {
        let labelMatch = labelPredicate(label, startsWith: true)
        let inputMatch = currentValue.map {
            NSCompoundPredicate(orPredicateWithSubpredicates: [labelMatch, NSPredicate(format: "value == %@", $0)])
        } ?? labelMatch
        let candidates = [
            app.textFields.matching(inputMatch).firstMatch,
            app.textViews.matching(inputMatch).firstMatch,
            app.descendants(matching: .any).matching(labelMatch).firstMatch,
        ]
        for attempt in 0...3 {
            if let input = candidates.first(where: { $0.exists && $0.isHittable }) { return input }
            if attempt < 3 { app.swipeUp() }
        }
        XCTFail("Could not reach the input ‘\(label)’.")
        return nil
    }

    func assertActivityExport(_ format: String, in app: XCUIApplication) {
        tap("Growing up", in: app)
        tap("Early infant", in: app, startsWith: true)
        assertVisible("Observations · 2–4 months", in: app)
        tap("Open activity", in: app)
        assertVisible("Reach and grasp play", in: app)
        tap(format, in: app)
        let selectedFormat = app.descendants(matching: .any)
            .matching(labelPredicate(format))
            .matching(NSPredicate(format: "selected == true"))
            .firstMatch
        XCTAssertTrue(selectedFormat.waitForExistence(timeout: 5), "The \(format) option should be selected.")

        tap("Export PDF", in: app)
        let shareSheetContent = app.descendants(matching: .any).matching(
            NSPredicate(
                format: "label CONTAINS[c] %@ OR label == %@ OR label == %@",
                "family-care-demo-activity-reach-and-grasp", "Save to Files", "Print"
            )
        ).firstMatch
        XCTAssertTrue(
            shareSheetContent.waitForExistence(timeout: 10),
            "The \(format) export should show the activity or a file action in the native share sheet."
        )
    }

    func capture(_ name: String, in app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
