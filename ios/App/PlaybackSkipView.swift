import SwiftUI

struct PlaybackSkipView: View {
    @EnvironmentObject var playback: PlaybackModel
    var body: some View {
        Section("跳过片头片尾") {
            Text("仅对当前影片保存，同源其他影片互不影响。直播和无法确定时长的媒体不自动跳过。").font(.caption).foregroundStyle(.secondary)
            Stepper("片头 \(Int(playback.skipSettings.intro)) 秒", value: Binding(get: { Int(playback.skipSettings.intro) }, set: { playback.setSkip(intro: Double($0), outro: playback.skipSettings.outro) }), in: 0...600, step: 5)
            Stepper("片尾 \(Int(playback.skipSettings.outro)) 秒", value: Binding(get: { Int(playback.skipSettings.outro) }, set: { playback.setSkip(intro: playback.skipSettings.intro, outro: Double($0)) }), in: 0...600, step: 5)
            Button("将当前进度设为片头") { playback.setSkip(intro: playback.elapsedSeconds, outro: playback.skipSettings.outro) }
            Button("重置跳过设置") { playback.setSkip(intro: 0, outro: 0) }
        }.disabled(playback.isLive)
    }
}
