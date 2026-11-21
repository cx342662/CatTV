import Foundation
import FoundationXML

public struct EPGProgramme: Identifiable, Equatable, Sendable {
    public let title: String
    public let start: Date
    public let end: Date
    public var id: String { String(start.timeIntervalSince1970) + "|" + title }
    public func isCurrent(at date: Date) -> Bool { start <= date && date < end }
}
public enum EPGDecoder {
    public static let timeZone = TimeZone(secondsFromGMT: 8 * 3600) ?? .gmt
    static func formatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = format
        formatter.isLenient = false
        return formatter
    }
    public static func normalizedName(_ name: String) -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let compact = trimmed.replacingOccurrences(of: "-", with: "").replacingOccurrences(of: " ", with: "").uppercased()
        guard compact.hasPrefix("CCTV") else { return trimmed }
        let expression = try? NSRegularExpression(pattern: #"^(CCTV\d+(?:\+|K)?)(?:[\u4e00-\u9fa5].*|$)"#)
        if let match = expression?.firstMatch(in: compact, range: NSRange(compact.startIndex..., in: compact)), let range = Range(match.range(at: 1), in: compact) { return String(compact[range]) }
        return compact
    }
    public static func requestURL(address: String, channel: String, date: Date) throws -> URL {
        let day = formatter("yyyy-MM-dd").string(from: date)
        let encoded = channel.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ""
        var result = address
        if result.contains("{name}") || result.contains("{date}") {
            result = result.replacingOccurrences(of: "{name}", with: encoded).replacingOccurrences(of: "{date}", with: day)
        } else if URL(string: result)?.path.lowercased().hasSuffix(".xml") != true {
            result += (result.contains("?") ? "&" : "?") + "ch=" + encoded + "&date=" + day
        }
        guard let url = URL(string: result), ["http", "https"].contains(url.scheme?.lowercased() ?? "") else { throw SourceError.invalidConfiguration("节目单地址必须为 HTTP(S)") }
        return url
    }
    public static func decode(_ data: Data, channelNames: [String], date: Date) throws -> [EPGProgramme] {
        guard data.count <= 32 * 1024 * 1024 else { throw SourceError.invalidConfiguration("节目单超过 32 MB") }
        let text = try LiveCatalog.decodeText(data).trimmingCharacters(in: .whitespacesAndNewlines)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let day = calendar.startOfDay(for: date)
        guard let next = calendar.date(byAdding: .day, value: 1, to: day) else { throw SourceError.invalidConfiguration("节目单日期错误") }
        let programmes: [EPGProgramme]
        if text.hasPrefix("<") {
            let delegate = XMLGuideDelegate()
            let parser = XMLParser(data: Data(text.utf8))
            parser.shouldResolveExternalEntities = false
            parser.externalEntityResolvingPolicy = .never
            parser.delegate = delegate
            guard parser.parse(), delegate.root == "tv" else { throw SourceError.invalidConfiguration("XMLTV 节目单无效") }
            let names = Set(channelNames.map(normalizedName))
            let ids = Set(delegate.channelNames.filter { !$0.value.isDisjoint(with: names) || names.contains(normalizedName($0.key)) }.map(\.key))
            programmes = delegate.programmes.filter { ids.contains($0.channel) || names.contains(normalizedName($0.channel)) }.map(\.programme)
        } else {
            guard let root = try JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any] else { throw SourceError.invalidConfiguration("JSON 节目单格式无效") }
            let nested = root["data"] as? [String: Any] ?? [:]
            let arrays = [root["epg_data"], root["data"], root["list"], nested["epg_data"], nested["list"]]
            let entries = arrays.compactMap { $0 as? [[String: Any]] }.first ?? []
            programmes = entries.compactMap { item in
                let title = cleanTitle(item["title"] as? String ?? item["name"] as? String ?? "")
                let startText = item["start"] as? String ?? item["start_time"] as? String ?? item["starttime"] as? String ?? ""
                let endText = item["end"] as? String ?? item["end_time"] as? String ?? item["endtime"] as? String ?? ""
                guard !title.isEmpty, let start = jsonDate(startText, day: date), var end = jsonDate(endText, day: date) else { return nil }
                if end <= start { end = calendar.date(byAdding: .day, value: 1, to: end) ?? end }
                guard end > start else { return nil }
                return EPGProgramme(title: title, start: start, end: end)
            }
        }
        return programmes.filter { $0.start < next && $0.end > day }.sorted { $0.start < $1.start }
    }
    static func cleanTitle(_ text: String) -> String {
        let title = text.replacingOccurrences(of: " --免费使用", with: "").replacingOccurrences(of: "--免费使用", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
        return title.contains("暂无") || title.contains("未提供") ? "" : title
    }
    static func jsonDate(_ text: String, day: Date) -> Date? {
        for format in ["yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"] {
            if let date = formatter(format).date(from: text) { return date }
        }
        let prefix = formatter("yyyy-MM-dd").string(from: day) + " "
        for format in ["yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"] {
            if let date = formatter(format).date(from: prefix + text) { return date }
        }
        return nil
    }
    static func xmlDate(_ text: String) -> Date? {
        for format in ["yyyyMMddHHmmss Z", "yyyyMMddHHmm Z", "yyyyMMddHHmmss", "yyyyMMddHHmm"] {
            if let date = formatter(format).date(from: text) { return date }
        }
        return nil
    }
}
private final class XMLGuideDelegate: NSObject, XMLParserDelegate {
    var root: String?
    var channelNames: [String: Set<String>] = [:]
    var programmes: [(channel: String, programme: EPGProgramme)] = []
    private var channelID: String?
    private var programmeAttributes: [String: String]?
    private var title = ""
    private var field: String?
    private var text = ""
    private var depth = 0
    func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String]) {
        depth += 1
        if root == nil { root = name }
        if depth > 32 || programmes.count > 100_000 { parser.abortParsing(); return }
        if name == "channel" { channelID = attributes["id"] }
        if name == "programme" { programmeAttributes = attributes; title = "" }
        if name == "display-name" || name == "title" { field = name; text = "" }
    }
    func parser(_ parser: XMLParser, foundCharacters string: String) { if field != nil { text += string }; if text.count > 16_384 { parser.abortParsing() } }
    func parser(_ parser: XMLParser, foundCDATA block: Data) { if let text = String(data: block, encoding: .utf8) { self.parser(parser, foundCharacters: text) } }
    func parser(_ parser: XMLParser, didEndElement name: String, namespaceURI: String?, qualifiedName: String?) {
        if name == "display-name", let channelID { channelNames[channelID, default: []].insert(EPGDecoder.normalizedName(text)) }
        if name == "title", title.isEmpty { title = EPGDecoder.cleanTitle(text) }
        if name == field { field = nil; text = "" }
        if name == "channel" { if let channelID { channelNames[channelID, default: []].insert(EPGDecoder.normalizedName(channelID)) }; channelID = nil }
        if name == "programme", let attributes = programmeAttributes {
            if let channel = attributes["channel"], let start = EPGDecoder.xmlDate(attributes["start"] ?? ""), let end = EPGDecoder.xmlDate(attributes["stop"] ?? ""), end > start, !title.isEmpty { programmes.append((channel, EPGProgramme(title: title, start: start, end: end))) }
            programmeAttributes = nil
        }
        depth -= 1
    }
}
public actor EPGRepository {
    private let session: URLSession
    private var cache: [URL: (date: Date, data: Data)] = [:]
    public init(session: URLSession = .shared) { self.session = session }
    public func programmes(address: String, channelNames: [String], date: Date) async throws -> [EPGProgramme] {
        let url = try EPGDecoder.requestURL(address: address, channel: channelNames.first ?? "", date: date)
        let data: Data
        if let cached = cache[url], Date().timeIntervalSince(cached.date) < 900 { data = cached.data }
        else {
            var request = URLRequest(url: url)
            request.timeoutInterval = 20
            let (body, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw SourceError.invalidConfiguration("节目单请求失败") }
            guard body.count <= 32 * 1024 * 1024 else { throw SourceError.invalidConfiguration("节目单超过 32 MB") }
            data = body
            if cache.count >= 8 { cache.removeAll() }
            cache[url] = (Date(), body)
        }
        try Task.checkCancellation()
        return try EPGDecoder.decode(data, channelNames: channelNames, date: date)
    }
}
