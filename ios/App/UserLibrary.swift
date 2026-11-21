import SwiftUI
import CatTVCore

struct LibraryEntry: Codable, Identifiable {
    let source: SourceSite
    let video: Video
    var episodeAddress: String?
    var position: Double
    var updatedAt: Date
    var id: String { LibraryIdentity.video(source: source, videoID: video.id) }
}
@MainActor
final class UserLibrary: ObservableObject {
    @Published private(set) var history: [LibraryEntry] = []
    @Published private(set) var following: [LibraryEntry] = []
    @Published var failure: String?
    private let file = URL.applicationSupportDirectory.appendingPathComponent("CatTV/library.json")
    private struct Snapshot: Codable { var history: [LibraryEntry]; var following: [LibraryEntry] }
    init() {
        do {
            if FileManager.default.fileExists(atPath: file.path) {
                let snapshot = try JSONDecoder().decode(Snapshot.self, from: Data(contentsOf: file))
                history = snapshot.history
                following = snapshot.following
            }
        } catch { failure = "记录读取失败：\(error.localizedDescription)" }
    }
    func isFollowing(source: SourceSite, video: Video) -> Bool {
        let id = LibraryIdentity.video(source: source, videoID: video.id)
        return following.contains { $0.id == id }
    }
    func toggleFollowing(source: SourceSite, video: Video) {
        let entry = LibraryEntry(source: source, video: video, position: 0, updatedAt: Date())
        if following.contains(where: { $0.id == entry.id }) { following.removeAll { $0.id == entry.id } }
        else { following.insert(entry, at: 0) }
        save()
    }
    func record(source: SourceSite, video: Video, episode: Episode, position: Double) {
        guard position.isFinite, position >= 0 else { return }
        let entry = LibraryEntry(source: source, video: video, episodeAddress: episode.address, position: position, updatedAt: Date())
        history.removeAll { $0.id == entry.id }
        history.insert(entry, at: 0)
        save()
    }
    func resumePosition(source: SourceSite, video: Video, episode: Episode) -> Double {
        let id = LibraryIdentity.video(source: source, videoID: video.id)
        return history.first { $0.id == id && $0.episodeAddress == episode.address }?.position ?? 0
    }
    func removeHistory(at offsets: IndexSet) { history.remove(atOffsets: offsets); save() }
    private func save() {
        do {
            try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
            let data = try JSONEncoder().encode(Snapshot(history: history, following: following))
            try data.write(to: file, options: .atomic)
        } catch { failure = "记录保存失败：\(error.localizedDescription)" }
    }
}
