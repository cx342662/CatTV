import Foundation

public struct SubtitleCue: Equatable, Sendable {
    public let start: Double
    public let end: Double
    public let text: String
}
public enum SubtitleDocument {
    public static func decode(_ data: Data) throws -> [SubtitleCue] {
        guard data.count <= 4 * 1024 * 1024 else { throw SourceError.invalidConfiguration("字幕超过 4 MB") }
        let text = try LiveCatalog.decodeText(data).replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n").replacingOccurrences(of: "\u{FEFF}", with: "")
        var cues: [SubtitleCue] = []
        for block in text.components(separatedBy: "\n\n") {
            let lines = block.components(separatedBy: "\n")
            guard let index = lines.firstIndex(where: { $0.contains("-->") }) else { continue }
            if lines.first?.hasPrefix("NOTE") == true { continue }
            let times = lines[index].components(separatedBy: "-->")
            guard times.count == 2, let start = time(times[0]), let end = time(times[1]), end > start else { continue }
            let content = HTMLSummary.plainText(lines.dropFirst(index + 1).joined(separator: "\n")).trimmingCharacters(in: .whitespacesAndNewlines)
            if !content.isEmpty { cues.append(SubtitleCue(start: start, end: end, text: content)) }
            guard cues.count <= 20_000 else { throw SourceError.invalidConfiguration("字幕条目过多") }
        }
        guard !cues.isEmpty else { throw SourceError.invalidConfiguration("没有有效的 SRT 或 WebVTT 字幕") }
        return cues.sorted { $0.start < $1.start }
    }
    private static func time(_ value: String) -> Double? {
        guard let first = value.split(whereSeparator: { $0.isWhitespace }).first else { return nil }
        let parts = first.replacingOccurrences(of: ",", with: ".").split(separator: ":", omittingEmptySubsequences: false)
        guard parts.count == 2 || parts.count == 3, let seconds = Double(parts.last ?? ""), seconds >= 0, seconds < 60,
              let minutes = Double(parts[parts.count - 2]), minutes >= 0, minutes < 60 else { return nil }
        let hours = parts.count == 3 ? Double(parts[0]) : 0
        guard let hours, hours >= 0, hours.isFinite else { return nil }
        return hours * 3600 + minutes * 60 + seconds
    }
    public static func text(at time: Double, offset: Double, cues: [SubtitleCue]) -> String {
        guard time.isFinite, offset.isFinite else { return "" }
        let position = time - offset
        return cues.filter { $0.start <= position && position < $0.end }.map(\.text).joined(separator: "\n")
    }
}
