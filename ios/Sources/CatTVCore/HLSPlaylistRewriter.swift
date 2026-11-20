import Foundation

public enum HLSPlaylistRewriter {
    public static func rewrite(_ data: Data, baseURL: URL, map: (URL) throws -> String) throws -> Data {
        guard data.count <= 2_000_000, let text = String(data: data, encoding: .utf8), text.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("#EXTM3U") else {
            throw SourceError.invalidConfiguration("HLS 播放列表无效或超过 2 MB")
        }
        let regex = try NSRegularExpression(pattern: #"URI="([^"]+)""#)
        let result = try text.components(separatedBy: .newlines).map { line -> String in
            if line.hasPrefix("#") {
                var rewritten = line
                let matches = regex.matches(in: line, range: NSRange(line.startIndex..., in: line))
                for match in matches.reversed() {
                    guard let valueRange = Range(match.range(at: 1), in: rewritten), let url = URL(string: String(rewritten[valueRange]), relativeTo: baseURL)?.absoluteURL else { throw SourceError.invalidURL }
                    if url.scheme == "data" || url.scheme == "skd" { continue }
                    rewritten.replaceSubrange(valueRange, with: try map(url))
                }
                return rewritten
            }
            let value = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty else { return line }
            guard let url = URL(string: value, relativeTo: baseURL)?.absoluteURL else { throw SourceError.invalidURL }
            return try map(url)
        }.joined(separator: "\n")
        return Data(result.utf8)
    }
}
