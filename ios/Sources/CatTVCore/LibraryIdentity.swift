import Foundation
import CryptoKit

public enum LibraryIdentity {
    public static func source(_ site: SourceSite) -> String {
        let identity = JSONValue.array([.string(site.key), .number(Double(site.type)), .string(site.api), site.ext ?? .null])
        return SHA256.hash(data: Data(identity.serialized.utf8)).map { String(format: "%02x", $0) }.joined()
    }
    public static func video(source: SourceSite, videoID: String) -> String {
        JSONValue.array([.string(self.source(source)), .string(videoID)]).serialized
    }
}
