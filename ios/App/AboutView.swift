import SwiftUI

struct AboutView: View {
    private var version: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "" }
    private var build: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "" }
    var body: some View {
        Form {
            Section("CatTV") {
                LabeledContent("版本", value: version)
                LabeledContent("构建", value: build)
                Text("iOS 移植开发版")
            }
            Section("开源") {
                if let value = Bundle.main.object(forInfoDictionaryKey: "CatTVSourceURL") as? String, let url = URL(string: value) {
                    Link("开源代码", destination: url)
                }
                NavigationLink("AGPL-3.0 许可证") { LicenseView() }
            }
        }.navigationTitle("关于 CatTV").navigationBarTitleDisplayMode(.inline)
    }
}
private struct LicenseView: View {
    @State private var text = ""
    var body: some View {
        ScrollView { Text(text).font(.system(.footnote, design: .monospaced)).textSelection(.enabled).padding(16) }
            .navigationTitle("开源许可证").navigationBarTitleDisplayMode(.inline)
            .task {
                do {
                    guard let url = Bundle.main.url(forResource: "LICENSE", withExtension: "txt") else { text = "许可证文件未找到"; return }
                    text = try String(contentsOf: url, encoding: .utf8)
                } catch { text = error.localizedDescription }
            }
    }
}
