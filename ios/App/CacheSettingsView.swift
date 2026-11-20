import SwiftUI

struct CacheSettingsView: View {
    @State private var disk = 0
    @State private var memory = 0
    var body: some View {
        Form {
            LabeledContent("网络磁盘缓存", value: ByteCountFormatter.string(fromByteCount: Int64(disk), countStyle: .file))
            LabeledContent("网络内存缓存", value: ByteCountFormatter.string(fromByteCount: Int64(memory), countStyle: .memory))
            Button("清理网络缓存") {
                URLCache.shared.removeAllCachedResponses()
                refresh()
            }
        }.navigationTitle("缓存管理").onAppear { refresh() }
    }
    private func refresh() {
        disk = URLCache.shared.currentDiskUsage
        memory = URLCache.shared.currentMemoryUsage
    }
}
