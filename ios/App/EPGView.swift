import SwiftUI
import CatTVCore

@MainActor
final class EPGViewModel: ObservableObject {
    @Published private(set) var programmes: [EPGProgramme] = []
    @Published private(set) var loading = false
    @Published private(set) var failure: String?
    private let repository = EPGRepository()
    private var generation = UUID()
    func load(address: String, channel: LiveChannel, date: Date) async {
        generation = UUID()
        let expected = generation
        programmes = []; failure = nil; loading = true
        defer { if generation == expected { loading = false } }
        do {
            let names = [channel.epgID, channel.name].compactMap { $0 }.filter { !$0.isEmpty }
            let result = try await repository.programmes(address: address, channelNames: names, date: date)
            try Task.checkCancellation()
            if generation == expected { programmes = result }
        } catch is CancellationError {} catch { if generation == expected { failure = error.localizedDescription } }
    }
}
struct EPGView: View {
    let channel: LiveChannel
    let address: String
    @StateObject private var model = EPGViewModel()
    @State private var date = Date()
    @State private var reload = UUID()
    @Environment(\.dismiss) private var dismiss
    private var requestID: String { String(date.timeIntervalSince1970) + reload.uuidString }
    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = EPGDecoder.timeZone
        formatter.dateFormat = "HH:mm"
        return formatter
    }()
    var body: some View {
        NavigationStack {
            List {
                DatePicker("日期", selection: $date, displayedComponents: .date).environment(\.timeZone, EPGDecoder.timeZone)
                Text("节目单时间：UTC+8").font(.caption).foregroundStyle(.secondary)
                if address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text("请在设置中填写节目单地址，或在直播源配置 epg 字段。").foregroundStyle(.secondary)
                } else if model.loading { ProgressView("正在加载节目单") }
                else if let failure = model.failure {
                    Text(failure).foregroundStyle(.secondary)
                    Button("重试") { reload = UUID() }
                } else if model.programmes.isEmpty { Text("当天暂无节目单").foregroundStyle(.secondary) }
                ForEach(model.programmes) { programme in
                    HStack(alignment: .top, spacing: 12) {
                        VStack(alignment: .leading) {
                            Text(Self.timeFormatter.string(from: programme.start)).accessibilityIdentifier("epg-start-time")
                            Text(Self.timeFormatter.string(from: programme.end)).foregroundStyle(.secondary).accessibilityIdentifier("epg-end-time")
                        }.font(.caption).monospacedDigit()
                        Text(programme.title)
                        Spacer()
                        if programme.isCurrent(at: Date()) { Text("正在播出").font(.caption).foregroundStyle(.tint) }
                    }.padding(.vertical, 4)
                }
            }.navigationTitle(channel.name + " · 节目单").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("关闭节目单") { dismiss() } } }
                .task(id: requestID) { if !address.isEmpty { await model.load(address: address, channel: channel, date: date) } }
        }
    }
}
