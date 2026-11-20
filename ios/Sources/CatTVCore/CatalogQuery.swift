import Foundation

public struct FilterOption: Codable, Identifiable, Sendable {
    public let n: String
    public let v: String
    public var id: String { v }
}
public struct CatalogFilter: Codable, Identifiable, Sendable {
    public let key: String
    public let name: String
    public let value: [FilterOption]
    public var id: String { key }
}

public struct CatalogQuery: Equatable, Sendable {
    public var category: String?
    public var search: String?
    public var page: Int
    public var filters: [String: String]
    public var ids: String?
    public init(category: String? = nil, search: String? = nil, page: Int = 1, filters: [String: String] = [:], ids: String? = nil) {
        self.category = category; self.search = search; self.page = page; self.filters = filters; self.ids = ids
    }
    public func parameters(type: Int) throws -> [String: String] {
        var result: [String: String] = [:]
        if let category { result["t"] = category }
        if let search, !search.isEmpty { result["wd"] = search }
        if let ids { result["ids"] = ids }
        if category != nil || search != nil || ids != nil {
            result["ac"] = type == 0 ? "videolist" : "detail"
            result["pg"] = String(max(1, page))
        }
        if type == 4 {
            result["filter"] = "true"
            let data = try JSONSerialization.data(withJSONObject: filters, options: [.sortedKeys])
            if category != nil { result["ext"] = data.base64EncodedString() }
        }
        return result
    }
}
