import Foundation

public struct MediaBadgeRule: Codable, Equatable, Identifiable, Sendable {
    public let label: String
    public let pattern: String
    public let image: String?
    public let group: String
    public var id: String { group + ":" + label }
}
public struct MediaBadgeMatcher: @unchecked Sendable {
    private let compiled: [(MediaBadgeRule, NSRegularExpression)]
    public init(rules: [MediaBadgeRule]) throws {
        compiled = try rules.map { ($0, try NSRegularExpression(pattern: $0.pattern, options: .caseInsensitive)) }
    }
    public func match(_ input: String) -> [MediaBadgeRule] {
        let text = String(input.prefix(240))
        let range = NSRange(text.startIndex..., in: text)
        let deadline = Date().addingTimeInterval(0.025)
        var result: [MediaBadgeRule] = []
        var groups = Set<String>()
        for (rule, regex) in compiled {
            guard Date() < deadline, result.count < 7 else { break }
            if groups.contains(rule.group) { continue }
            var matched = false
            regex.enumerateMatches(in: text, options: .reportProgress, range: range) { match, _, stop in
                if match != nil { matched = true; stop.pointee = true }
                if Date() > deadline { stop.pointee = true }
            }
            if matched { groups.insert(rule.group); result.append(rule) }
        }
        return result
    }
    public static func parsePack(_ data: Data) throws -> [MediaBadgeRule] {
        guard data.count <= 2_000_000,
              let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let filters = object["filters"] as? [[String: Any]] else { throw SourceError.invalidConfiguration("徽章包格式错误或超过 2 MB") }
        let rules: [MediaBadgeRule] = filters.prefix(3000).compactMap { item in
            guard (item["isEnabled"] as? Bool) != false,
                  let label = item["name"] as? String, !label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  let pattern = item["pattern"] as? String, (1...2000).contains(pattern.count),
                  (try? NSRegularExpression(pattern: pattern, options: .caseInsensitive)) != nil else { return nil }
            let image = (item["imageURL"] as? String).flatMap { URL(string: $0) }.flatMap { $0.scheme == "https" ? $0.absoluteString : nil }
            let group = (item["groupId"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? label
            return MediaBadgeRule(label: String(label.prefix(40)), pattern: pattern, image: image, group: group)
        }
        guard !rules.isEmpty else { throw SourceError.invalidConfiguration("徽章包没有可用规则") }
        return rules
    }
    public static var builtInRules: [MediaBadgeRule] {
        let definitions = [
            ("8K", #"\b(?:8k|4320p?)\b"#), ("4K", #"\b(?:4k|uhd|2160p?)\b"#),
            ("1080p", #"\b(?:1080[pi]?|fhd)\b"#), ("720p", #"\b720p?\b"#),
            ("WEB-DL", #"\bweb[ ._-]?dl\b"#), ("BluRay", #"\bblu[ ._-]?ray\b"#),
            ("REMUX", #"\bremux\b"#), ("Dolby Vision", #"\b(?:dolby[ ._-]?vision|dovi|dv)\b"#),
            ("HDR10+", #"\bhdr10\+"#), ("HDR10", #"\bhdr10\b"#), ("HDR", #"\bhdr\b"#),
            ("H.265", #"\b(?:hevc|[hx][ ._-]?265)\b"#), ("H.264", #"\b(?:avc|[hx][ ._-]?264)\b"#),
            ("AAC", #"\baac\b"#), ("AV1", #"\bav1\b"#), ("Atmos", #"\batmos\b"#),
            ("Dolby Digital Plus", #"\b(?:eac3|ddp)\b"#), ("DTS", #"\bdts\b"#), ("10-bit", #"\b10[ ._-]?bit\b"#)
        ]
        return definitions.map { MediaBadgeRule(label: $0.0, pattern: $0.1, image: nil, group: $0.0.hasPrefix("HDR") ? "HDR" : $0.0) }
    }
}
