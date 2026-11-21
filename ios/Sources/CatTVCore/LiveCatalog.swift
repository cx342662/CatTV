import Foundation
import CoreFoundation

public struct LiveStream: Equatable, Sendable {
    public let address: String
    public let headers: [String: String]
}
public struct LiveChannel: Identifiable, Sendable {
    public let name: String
    public let group: String
    public let logo: String?
    public let epgID: String?
    public var streams: [LiveStream]
    public var id: String { group + "|" + name + "|" + (epgID ?? "") }
    public func playback(stream index: Int) throws -> (Video, SourceSite) {
        guard streams.indices.contains(index) else { throw SourceError.invalidConfiguration("直播线路不存在") }
        let selected = streams[index]
        guard let url = URL(string: selected.address), ["http", "https"].contains(url.scheme?.lowercased() ?? "") else { throw SourceError.invalidConfiguration("当前原生内核只支持 HTTP(S) 直播地址") }
        let source: [String: Any] = ["key": "live:" + id, "name": name, "type": 1, "api": selected.address, "header": selected.headers]
        let movie: [String: Any] = ["vod_id": id, "vod_name": name, "vod_pic": logo ?? "", "type_name": "直播", "vod_play_from": "直播", "vod_play_url": "直播$" + selected.address]
        return (try JSONDecoder().decode(Video.self, from: JSONSerialization.data(withJSONObject: movie)), try JSONDecoder().decode(SourceSite.self, from: JSONSerialization.data(withJSONObject: source)))
    }
}
public struct LiveSource: Identifiable, Sendable {
    public let name: String
    public let address: String?
    public let channels: [LiveChannel]
    public let headers: [String: String]
    public let epg: String?
    public var id: String { name + "|" + (address ?? "inline") }
}
public enum LiveCatalog {
    public static func sources(_ values: [JSONValue], baseURL: URL? = nil) -> [LiveSource] {
        values.enumerated().compactMap { index, value in
            guard case .object(let object) = value else { return nil }
            let name = object["name"]?.text ?? object["group"]?.text ?? "线路\(index + 1)"
            let headers = readHeaders(object)
            let channels: [LiveChannel]
            if let rawChannels = object["channels"], case .array(let entries) = rawChannels { channels = inlineChannels(entries, group: name, headers: headers) }
            else { channels = [] }
            let address = object["url"]?.text.flatMap { URL(string: $0, relativeTo: baseURL)?.absoluteURL.absoluteString }
            return LiveSource(name: name, address: address, channels: channels, headers: headers, epg: object["epg"]?.text)
        }
    }
    private static func readHeaders(_ object: [String: JSONValue]) -> [String: String] {
        var result: [String: String] = [:]
        if let value = object["header"], case .object(let headers) = value {
            for (name, value) in headers {
                if let text = value.text, HeaderPolicy.isSendable(name: name, value: text) { result[name] = text }
            }
        }
        if let ua = object["ua"]?.text, HeaderPolicy.isSendable(name: "User-Agent", value: ua) { result["User-Agent"] = ua }
        return result
    }
    private static func inlineChannels(_ values: [JSONValue], group: String, headers: [String: String]) -> [LiveChannel] {
        values.compactMap { value in
            guard case .object(let object) = value, let name = object["name"]?.text else { return nil }
            var addresses: [String] = []
            if let urls = object["urls"], case .array(let entries) = urls { addresses = entries.compactMap(\.text) }
            if let url = object["url"]?.text { addresses += url.components(separatedBy: "#") }
            let merged = headers.merging(readHeaders(object)) { _, new in new }
            let streams = addresses.compactMap { stream($0, headers: merged) }
            guard !streams.isEmpty else { return nil }
            return LiveChannel(name: name, group: object["group"]?.text ?? group, logo: object["logo"]?.text, epgID: object["epg"]?.text, streams: streams)
        }
    }
    private static func stream(_ input: String, headers: [String: String]) -> LiveStream? {
        let parts = input.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: "|", maxSplits: 1)
        guard let address = parts.first.map(String.init), let url = URL(string: address), url.scheme != nil else { return nil }
        var result = headers
        if parts.count > 1 {
            for entry in parts[1].split(separator: "&") {
                let pair = entry.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
                if pair.count == 2 {
                    let key = String(pair[0]).removingPercentEncoding ?? String(pair[0])
                    let value = String(pair[1]).removingPercentEncoding ?? String(pair[1])
                    if HeaderPolicy.isSendable(name: key, value: value) { result[key] = value }
                }
            }
        }
        return LiveStream(address: address, headers: result)
    }
    public static func decodeText(_ data: Data) throws -> String {
        if data.starts(with: [0xFF, 0xFE]) || data.starts(with: [0xFE, 0xFF]), let decoded = String(data: data, encoding: .utf16) { return decoded }
        if let decoded = String(data: data, encoding: .utf8) { return decoded }
        let legacy = String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.GB_18030_2000.rawValue)))
        guard let decoded = String(data: data, encoding: legacy) else { throw SourceError.invalidConfiguration("直播列表编码无法识别") }
        return decoded
    }
    public static func parse(_ text: String, source: LiveSource) throws -> [LiveChannel] {
        let text = text.replacingOccurrences(of: "\u{FEFF}", with: "")
        let m3u = text.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("#EXTM3U")
        let attributes = try NSRegularExpression(pattern: #"([\w-]+)="([^"]*)""#)
        var group = "默认"
        var name: String?
        var metadata: [String: String] = [:]
        var headers = source.headers
        var channels: [LiveChannel] = []
        for raw in text.components(separatedBy: .newlines) {
            let line = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.isEmpty { continue }
            if m3u {
                if line.hasPrefix("#EXTINF:") {
                    metadata = [:]; headers = source.headers
                    for match in attributes.matches(in: line, range: NSRange(line.startIndex..., in: line)) {
                        if let key = Range(match.range(at: 1), in: line), let value = Range(match.range(at: 2), in: line) { metadata[String(line[key])] = String(line[value]) }
                    }
                    name = line.lastIndex(of: ",").map { String(line[line.index(after: $0)...]) } ?? metadata["tvg-name"]
                    group = metadata["group-title"] ?? "默认"
                } else if line.hasPrefix("#EXTGRP:") { group = String(line.dropFirst(8)) }
                else if line.hasPrefix("#EXTVLCOPT:") {
                    let pair = line.dropFirst(11).split(separator: "=", maxSplits: 1)
                    if pair.count == 2 {
                        let key = pair[0] == "http-user-agent" ? "User-Agent" : pair[0] == "http-referrer" ? "Referer" : ""
                        if HeaderPolicy.isSendable(name: key, value: String(pair[1])) { headers[key] = String(pair[1]) }
                    }
                } else if !line.hasPrefix("#"), let media = stream(line, headers: headers) {
                    let title = name.flatMap { $0.isEmpty ? nil : $0 } ?? metadata["tvg-name"] ?? media.address
                    channels.append(LiveChannel(name: title, group: group, logo: metadata["tvg-logo"], epgID: metadata["tvg-id"], streams: [media]))
                    name = nil
                }
            } else if let comma = line.firstIndex(of: ",") {
                let title = String(line[..<comma]).trimmingCharacters(in: .whitespaces)
                let rest = String(line[line.index(after: comma)...]).trimmingCharacters(in: .whitespaces)
                if rest == "#genre#" { group = title }
                else {
                    let streams = rest.components(separatedBy: "#").compactMap { stream($0, headers: source.headers) }
                    if !streams.isEmpty { channels.append(LiveChannel(name: title, group: group, logo: nil, epgID: nil, streams: streams)) }
                }
            }
        }
        var merged: [LiveChannel] = []
        for channel in channels {
            if let index = merged.firstIndex(where: { $0.id == channel.id }) {
                for stream in channel.streams where !merged[index].streams.contains(stream) { merged[index].streams.append(stream) }
            } else { merged.append(channel) }
        }
        guard !merged.isEmpty else { throw SourceError.invalidConfiguration("直播列表没有可识别频道；爬虫、加密列表或其他格式尚未兼容") }
        return merged
    }
}
public actor LiveRepository {
    private let session: URLSession
    public init(session: URLSession = .shared) { self.session = session }
    public func channels(source: LiveSource) async throws -> [LiveChannel] {
        if !source.channels.isEmpty { return source.channels }
        guard let address = source.address, let url = URL(string: address), ["http", "https"].contains(url.scheme?.lowercased() ?? "") else { throw SourceError.invalidConfiguration("直播源需要兼容的 HTTP(S) 列表或内置频道组") }
        var request = URLRequest(url: url)
        request.timeoutInterval = 30
        for (name, value) in source.headers { request.setValue(value, forHTTPHeaderField: name) }
        let (data, response) = try await session.data(for: request)
        guard let response = response as? HTTPURLResponse else { throw SourceError.invalidURL }
        guard (200..<300).contains(response.statusCode) else { throw SourceError.httpStatus(response.statusCode) }
        let text = try LiveCatalog.decodeText(data)
        return try LiveCatalog.parse(text, source: source)
    }
}
