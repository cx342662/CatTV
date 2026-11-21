import SwiftUI
import ImageIO
import CatTVCore

@MainActor
final class PosterModel: ObservableObject {
    @Published private(set) var image: UIImage?
    @Published private(set) var failure: String?
    private var generation = UUID()
    func load(address: String, source: SourceSite?) async {
        generation = UUID()
        let expected = generation
        image = nil; failure = nil
        do {
            let data = try await PosterRepository.shared.data(address: address, site: source)
            let decoded = try await Task.detached(priority: .utility) {
                try Task.checkCancellation()
                let options: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceThumbnailMaxPixelSize: 1024, kCGImageSourceCreateThumbnailWithTransform: true, kCGImageSourceShouldCacheImmediately: true]
                guard let source = CGImageSourceCreateWithData(data as CFData, nil), let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { throw SourceError.invalidConfiguration("海报图片无法解码") }
                return image
            }.value
            try Task.checkCancellation()
            if generation == expected { image = UIImage(cgImage: decoded) }
        } catch is CancellationError {} catch {
            if generation == expected { failure = error.localizedDescription }
        }
    }
}
struct CatalogTile: View {
    let video: Video
    let source: SourceSite?
    @StateObject private var poster = PosterModel()
    private var requestID: URLRequest? { try? PosterRepository.makeRequest(address: video.poster, site: source) }
    var body: some View {
        NavigationLink { DetailView(summary: video, source: source) } label: {
            VStack(alignment: .leading, spacing: 5) {
                Rectangle().fill(Color.secondary.opacity(0.15))
                    .aspectRatio(2.0 / 3.0, contentMode: .fit)
                    .overlay {
                        GeometryReader { geometry in
                            Group {
                                if let image = poster.image { Image(uiImage: image).resizable().scaledToFill() }
                                else { Image(systemName: poster.failure == nil ? "film" : "exclamationmark.triangle") }
                            }.frame(width: geometry.size.width, height: geometry.size.height).clipped()
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 16)).contentShape(Rectangle())
                Text(video.name).font(.system(size: 14, weight: .medium)).lineLimit(1).frame(maxWidth: .infinity).multilineTextAlignment(.center)
                MediaBadgeRow(text: video.name + " " + video.remarks)
                Text(video.remarks).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }.frame(maxWidth: .infinity).contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityIdentifier("video-\(video.id)")
            .accessibilityValue(poster.image != nil ? "海报已加载" : (poster.failure.map { "海报加载失败：" + $0 } ?? "海报加载中"))
            .task(id: requestID) { await poster.load(address: video.poster, source: source) }
    }
}
