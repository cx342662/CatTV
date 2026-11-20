import SwiftUI
import CatTVCore

struct ConfigurationEditor: View {
    let document: ConfigurationDocument?
    @EnvironmentObject var library: LibraryModel
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var origin = ""
    @State private var text = ""
    @State private var error: String?
    @State private var showError = false
    @FocusState private var editorFocused: Bool
    var body: some View {
        NavigationStack {
            Form {
                TextField("配置名称", text: $name)
                TextField("配置来源地址（可选）", text: $origin).textInputAutocapitalization(.never).autocorrectionDisabled()
                Section("JSON 配置") {
                    TextEditor(text: $text).focused($editorFocused).font(.system(.caption, design: .monospaced)).frame(minHeight: 320)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                }
                if let error { Text(error).foregroundStyle(.red) }
            }.navigationTitle(document == nil ? "新增配置" : "编辑配置")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("保存") {
                            editorFocused = false
                            do {
                                guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw SourceError.invalidConfiguration("请填写配置名称") }
                                let url: URL?
                                if origin.isEmpty { url = nil }
                                else {
                                    guard let parsed = URL(string: origin), ["http", "https"].contains(parsed.scheme ?? "") else { throw SourceError.invalidURL }
                                    url = parsed
                                }
                                let updated = try ConfigurationDocument(id: document?.id ?? UUID(), name: name, origin: url, data: Data(text.utf8))
                                try library.save(updated)
                                dismiss()
                            } catch { self.error = error.localizedDescription; showError = true }
                        }
                    }
                }.alert("配置错误", isPresented: $showError) {
                    Button("确定", role: .cancel) {}
                } message: { Text(error ?? "配置无效") }
                .onAppear {
                    name = document?.name ?? ""
                    origin = document?.origin?.absoluteString ?? ""
                    text = document.map { String(decoding: $0.data, as: UTF8.self) } ?? ""
                }
        }
    }
}
