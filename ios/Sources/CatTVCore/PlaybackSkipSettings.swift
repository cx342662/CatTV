import Foundation

public struct PlaybackSkipSettings: Codable, Equatable, Sendable {
    public let intro: Double
    public let outro: Double
    public init(intro: Double = 0, outro: Double = 0) {
        self.intro = intro.isFinite ? min(600, max(0, intro)) : 0
        self.outro = outro.isFinite ? min(600, max(0, outro)) : 0
    }
    public func introTarget(position: Double, duration: Double) -> Double? {
        guard position.isFinite, position >= 0, duration.isFinite, duration > intro + outro,
              intro > 0, position < intro else { return nil }
        return intro
    }
    public func shouldAdvance(position: Double, duration: Double) -> Bool {
        position.isFinite && position >= 0 && duration.isFinite && duration > intro + outro && outro > 0 && position >= duration - outro
    }
}
