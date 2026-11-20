import Foundation

public enum XMLCatalogDecoder {
    public static func decode(_ data: Data) throws -> CatalogResponse {
        let delegate = XMLCatalogDelegate()
        let parser = XMLParser(data: data)
        parser.shouldResolveExternalEntities = false
        parser.externalEntityResolvingPolicy = .never
        parser.delegate = delegate
        guard parser.parse(), !delegate.invalid else {
            throw SourceError.invalidConfiguration("XML 影视源响应无效：\(parser.parserError?.localizedDescription ?? "无效结构")")
        }
        let json = try JSONSerialization.data(withJSONObject: ["class": delegate.categories, "list": delegate.videos, "page": delegate.page, "pagecount": delegate.pageCount])
        return try JSONDecoder().decode(CatalogResponse.self, from: json)
    }
}
private final class XMLCatalogDelegate: NSObject, XMLParserDelegate {
    var categories: [[String: String]] = []
    var videos: [[String: String]] = []
    var page = "1"
    var pageCount = "1"
    var invalid = false
    private var video: [String: String]?
    private var categoryID: String?
    private var text = ""
    private var lines: [(String, String)] = []
    private var flag = ""
    private var stack: [String] = []
    func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String]) {
        if stack.isEmpty && name != "rss" { invalid = true }
        stack.append(name)
        text = ""
        if name == "list" { page = attributes["page"] ?? "1"; pageCount = attributes["pagecount"] ?? "1" }
        if name == "ty" { categoryID = attributes["id"] }
        if name == "video" { video = [:]; lines = [] }
        if name == "dd" { flag = attributes["flag"] ?? "默认" }
    }
    func parser(_ parser: XMLParser, foundCharacters string: String) { text += string }
    func parser(_ parser: XMLParser, foundCDATA CDATABlock: Data) { text += String(decoding: CDATABlock, as: UTF8.self) }
    func parser(_ parser: XMLParser, didEndElement name: String, namespaceURI: String?, qualifiedName: String?) {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if name == "ty", let id = categoryID { categories.append(["type_id": id, "type_name": value]); categoryID = nil }
        if video != nil {
            let mapping = ["id": "vod_id", "name": "vod_name", "pic": "vod_pic", "note": "vod_remarks", "des": "vod_content", "year": "vod_year", "area": "vod_area", "lang": "vod_lang", "type": "type_name", "actor": "vod_actor", "director": "vod_director"]
            if let field = mapping[name] { video?[field] = value }
            if name == "dd" { lines.append((flag, value)) }
            if name == "video" {
                video?["vod_play_from"] = lines.map(\.0).joined(separator: "$$$")
                video?["vod_play_url"] = lines.map(\.1).joined(separator: "$$$")
                if let video { videos.append(video) }
                video = nil
            }
        }
        if stack.last != name { invalid = true }
        if !stack.isEmpty { stack.removeLast() }
        text = ""
    }
    func parser(_ parser: XMLParser, resolveExternalEntityName name: String, systemID: String?) -> Data? { nil }
}
