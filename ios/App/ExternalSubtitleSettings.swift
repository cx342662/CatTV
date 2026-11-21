import SwiftUI
import UniformTypeIdentifiers
import CatTVCore

struct ExternalSubtitleSettings: View {
    @EnvironmentObject var playback: PlaybackModel
    @State private var importing = false
    @State private var failure: String?
    var body: some View {
        Section("外部字幕") {
            Button("导入 SRT / WebVTT 文件") { importing = true }
            if let name = playback.subtitleName {
                Text(name)
                Text(String(format: "字幕延后 %.1f 秒", playback.subtitleOffset))
                Slider(value: $playback.subtitleOffset, in: -30...30, step: 0.1)
                Button("重置时间偏移") { playback.subtitleOffset = 0 }
                Button("移除外部字幕") { playback.clearSubtitles() }
                Text("外部字幕当前显示在应用播放器内；画中画及 AirPlay 接收端不显示此叠加字幕。").font(.caption).foregroundStyle(.secondary)
            }
        }.fileImporter(isPresented: $importing, allowedContentTypes: [.plainText, .data]) { result in
            Task {
                do {
                    let url = try result.get()
                    let cues = try await Task.detached {
                        let scoped = url.startAccessingSecurityScopedResource()
                        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                        return try SubtitleDocument.decode(Data(contentsOf: url))
                    }.value
                    playback.setSubtitles(cues, name: url.lastPathComponent)
                } catch { failure = error.localizedDescription }
            }
        }.alert("字幕导入失败", isPresented: Binding(get: { failure != nil }, set: { if !$0 { failure = nil } })) {
            Button("确定") { failure = nil }
        } message: { Text(failure ?? "") }
    }
}
