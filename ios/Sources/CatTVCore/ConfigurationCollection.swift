import Foundation

public struct ConfigurationDocument: Codable, Identifiable, Equatable, Sendable {
    public let id: UUID
    public var name: String
    public var origin: URL?
    public var data: Data
    public init(id: UUID = UUID(), name: String, origin: URL?, data: Data) throws {
        _ = try SourceConfiguration.decode(data, baseURL: origin)
        self.id = id; self.name = name; self.origin = origin; self.data = data
    }
    public func configuration() throws -> SourceConfiguration { try SourceConfiguration.decode(data, baseURL: origin) }
}
public struct ConfigurationCollection: Codable, Equatable, Sendable {
    public private(set) var documents: [ConfigurationDocument] = []
    public private(set) var activeID: UUID?
    public private(set) var sourceKeys: [String: String] = [:]
    public init() {}
    public var active: ConfigurationDocument? { documents.first { $0.id == activeID } }
    public mutating func upsert(_ document: ConfigurationDocument) throws {
        _ = try document.configuration()
        if let index = documents.firstIndex(where: { $0.id == document.id }) { documents[index] = document }
        else { documents.append(document) }
        activeID = document.id
    }
    public mutating func activate(_ id: UUID) throws {
        guard let document = documents.first(where: { $0.id == id }) else { throw SourceError.invalidConfiguration("配置不存在") }
        _ = try document.configuration()
        activeID = id
    }
    public mutating func remove(_ id: UUID) {
        documents.removeAll { $0.id == id }
        sourceKeys.removeValue(forKey: id.uuidString)
        if activeID == id { activeID = documents.first?.id }
    }
    public mutating func selectSource(_ key: String) throws {
        guard let active, try active.configuration().sites.contains(where: { $0.key == key }) else { throw SourceError.invalidConfiguration("影视源不存在") }
        sourceKeys[active.id.uuidString] = key
    }
}
