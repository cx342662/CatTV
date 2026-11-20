import Foundation

public enum HTMLSummary {
    public static func plainText(_ html: String) -> String {
        var text = String(html.prefix(100_000))
            .replacingOccurrences(of: #"(?is)<(script|style)\b[^>]*>.*?</\1>"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"(?i)<br\s*/?>|</p\s*>|</div\s*>"#, with: "\n", options: .regularExpression)
            .replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)
        let named = [("&nbsp;", " "), ("&lt;", "<"), ("&gt;", ">"), ("&quot;", "\""), ("&#39;", "'"), ("&apos;", "'"), ("&amp;", "&")]
        for (entity, value) in named { text = text.replacingOccurrences(of: entity, with: value) }
        var searchStart = text.startIndex
        while searchStart < text.endIndex, let range = text.range(of: #"&#(?:[0-9]+|[xX][0-9a-fA-F]+);"#, options: .regularExpression, range: searchStart..<text.endIndex) {
            let digits = String(text[range].dropFirst(2).dropLast())
            let isHex = digits.lowercased().hasPrefix("x")
            let number = UInt32(isHex ? String(digits.dropFirst()) : digits, radix: isHex ? 16 : 10)
            if let number, let scalar = UnicodeScalar(number), number > 0 {
                let replacement = String(scalar)
                let offset = text.distance(from: text.startIndex, to: range.lowerBound)
                text.replaceSubrange(range, with: replacement)
                searchStart = text.index(text.startIndex, offsetBy: offset + replacement.count)
            } else { searchStart = range.upperBound }
        }
        return text.replacingOccurrences(of: #"\n{3,}"#, with: "\n\n", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
