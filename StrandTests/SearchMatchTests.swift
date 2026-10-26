import XCTest
@testable import Strand

/// Pins the More and Settings search filter. Twin of Kotlin `SearchMatchTest`, whose expected block is
/// this implementation's own output, so these cases guard the Swift side against drifting from it.
final class SearchMatchTests: XCTestCase {

    func testFoldIgnoresCaseAndAccents() {
        XCTAssertEqual(SearchMatch.fold("Réglages"), "reglages")
        XCTAssertEqual(SearchMatch.fold("Cafe\u{301}"), "cafe")
        XCTAssertEqual(SearchMatch.fold("İstanbul"), "istanbul")
    }

    /// Kotlin's `String.lowercase` would end this in "ς"; the twin lowercases per code point to match.
    func testFoldHasNoFinalSigmaRule() {
        XCTAssertEqual(SearchMatch.fold("ΣΟΦΟΣ"), "σοφοσ")
    }

    func testTermsSplitOnUnicodeWhitespaceOnly() {
        XCTAssertEqual(SearchMatch.terms("  live   notif "), ["live", "notif"])
        XCTAssertEqual(SearchMatch.terms("a\u{00A0}b"), ["a", "b"])
        XCTAssertEqual(SearchMatch.terms("a\u{200B}b"), ["a\u{200B}b"])
        XCTAssertEqual(SearchMatch.terms("   "), [])
    }

    func testEveryWordMustAppearInSomeField() {
        XCTAssertTrue(SearchMatch.matches("notif live", fields: ["Live notifications"]))
        XCTAssertTrue(SearchMatch.matches("ai coach", fields: ["Appearance", "AI Coach"]))
        XCTAssertTrue(SearchMatch.matches("live notif", fields: ["Live", "notifications"]))
        XCTAssertFalse(SearchMatch.matches("coach xyz", fields: ["AI Coach"]))
        XCTAssertFalse(SearchMatch.matches("hr", fields: ["Live heart rate"]))
    }

    func testBlankQueryHidesNothing() {
        XCTAssertTrue(SearchMatch.matches("", fields: []))
        XCTAssertTrue(SearchMatch.matches(" \n", fields: ["Anything"]))
        XCTAssertFalse(SearchMatch.matches("x", fields: []))
    }
}
