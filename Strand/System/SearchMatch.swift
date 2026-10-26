import Foundation

/// The free-text filter behind the More index and Settings search bars. A query matches when every
/// whitespace-separated word in it appears somewhere in the candidate's fields, ignoring case and
/// accents, so "notif live" finds "Live notifications" and "reglages" finds "Réglages".
///
/// Twin of Android `SearchMatch` (`com.noop.ui.SearchMatch`). Both fold the same way (canonical
/// decomposition, nonspacing marks dropped, then lowercased) so one query filters the same screens on
/// both platforms; `SearchMatchTests` and the Kotlin `SearchMatchTest` pin the same cases.
enum SearchMatch {
    /// Case- and accent-insensitive form of `text`.
    static func fold(_ text: String) -> String {
        var scalars = String.UnicodeScalarView()
        for scalar in text.decomposedStringWithCanonicalMapping.unicodeScalars
        where scalar.properties.generalCategory != .nonspacingMark {
            scalars.append(scalar)
        }
        return String(scalars).lowercased()
    }

    /// The query's folded words. Empty for a blank query.
    static func terms(_ query: String) -> [String] {
        fold(query).split(whereSeparator: \.isWhitespace).map(String.init)
    }

    /// True when every word of `query` occurs in at least one of `fields`. A blank query matches
    /// everything, so an empty search bar hides nothing.
    static func matches(_ query: String, fields: [String]) -> Bool {
        let words = terms(query)
        guard !words.isEmpty else { return true }
        let folded = fields.map(fold)
        return words.allSatisfy { word in folded.contains { $0.contains(word) } }
    }
}
