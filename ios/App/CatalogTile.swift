import SwiftUI
import CatTVCore

struct CatalogTile: View {
    let video: Video
    let source: SourceSite?
    private var posterURL: URL? {
        URL(string: video.poster, relativeTo: source.flatMap { URL(string: $0.api) })?.absoluteURL
    }
    var body: some View {
        AsyncImage(url: posterURL) { phase in
            NavigationLink { DetailView(summary: video, source: source) } label: {
                VStack(alignment: .leading, spacing: 5) {
                    Group {
                        if let image = phase.image { image.resizable().scaledToFill() }
                        else {
                            Rectangle().fill(Color.secondary.opacity(0.15))
                                .overlay(Image(systemName: phase.error == nil ? "film" : "exclamationmark.triangle"))
                        }
                    }.aspectRatio(2 / 3, contentMode: .fit).clipped().clipShape(RoundedRectangle(cornerRadius: 16))
                    Text(video.name).font(.system(size: 14, weight: .medium)).lineLimit(1).frame(maxWidth: .infinity).multilineTextAlignment(.center)
                    MediaBadgeRow(text: video.name + " " + video.remarks)
                    Text(video.remarks).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                }
            }.buttonStyle(.plain).accessibilityIdentifier("video-\(video.id)")
                .accessibilityValue(phase.image != nil ? "海报已加载" : (phase.error != nil ? "海报加载失败" : "海报加载中"))
        }
    }
}
