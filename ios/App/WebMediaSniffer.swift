import SwiftUI
import WebKit
import CatTVCore

@MainActor
final class WebMediaSniffer: NSObject, WKScriptMessageHandler, WKNavigationDelegate {
    let webView: WKWebView
    private var continuation: CheckedContinuation<ResolvedPlayback, Error>?
    private var deadline: Task<Void, Never>?
    private var generation = UUID()
    private var activeNavigation: WKNavigation?
    private var headers: [String: String] = [:]
    override init() {
        let controller = WKUserContentController()
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        configuration.userContentController = controller
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init()
        controller.add(WeakScriptHandler(self), name: "catMedia")
        let script = #"""
        (() => {
          const emit = u => { if (typeof u === 'string' && /^https?:/i.test(u)) window.webkit.messageHandlers.catMedia.postMessage(u); };
          const scan = () => {
            document.querySelectorAll('video,source,audio').forEach(v => emit(v.currentSrc || v.src));
            performance.getEntriesByType('resource').forEach(v => emit(v.name));
          };
          new PerformanceObserver(items => items.getEntries().forEach(v => emit(v.name))).observe({entryTypes:['resource']});
          document.addEventListener('loadedmetadata', scan, true);
          setInterval(scan, 500);
          scan();
        })();
        """#
        controller.addUserScript(WKUserScript(source: script, injectionTime: .atDocumentStart, forMainFrameOnly: false))
        webView.navigationDelegate = self
    }
    func sniff(_ resource: ResolvedPlayback, timeout: Int = 30) async throws -> ResolvedPlayback {
        cancel()
        generation = UUID()
        let expected = generation
        headers = resource.headers
        webView.customUserAgent = resource.headers.first { $0.key.lowercased() == "user-agent" }?.value
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                self.continuation = continuation
                var request = URLRequest(url: resource.url)
                for (key, value) in headers { request.setValue(value, forHTTPHeaderField: key) }
                activeNavigation = webView.load(request)
                deadline = Task { [weak self] in
                    do { try await Task.sleep(nanoseconds: UInt64(max(1, timeout)) * 1_000_000_000) }
                    catch { return }
                    guard let self, self.generation == expected else { return }
                    self.finish(.failure(SourceError.invalidConfiguration("网页解析超时：未发现兼容的媒体请求")))
                }
            }
        } onCancel: {
            Task { @MainActor [weak self] in if self?.generation == expected { self?.cancel() } }
        }
    }
    func cancel() { finish(.failure(CancellationError())) }
    private func finish(_ result: Result<ResolvedPlayback, Error>) {
        guard let continuation else { return }
        self.continuation = nil
        deadline?.cancel(); deadline = nil
        activeNavigation = nil
        webView.stopLoading()
        continuation.resume(with: result)
    }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let text = message.body as? String, let url = URL(string: text), ["http", "https"].contains(url.scheme?.lowercased() ?? ""),
              ["m3u8", "mp4", "m4v", "mov", "mp3", "m4a", "aac"].contains(url.pathExtension.lowercased()) else { return }
        accept(url)
    }
    private func accept(_ url: URL) {
        guard continuation != nil else { return }
        let expected = generation
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { [weak self] cookies in
            Task { @MainActor [weak self] in
                guard let self, self.generation == expected, self.continuation != nil else { return }
                var result = self.headers
                if let referer = self.webView.url?.absoluteString { result["Referer"] = referer }
                let selected = cookies.filter { cookie in
                    let domain = cookie.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")).lowercased()
                    let host = url.host?.lowercased() ?? ""
                    return (host == domain || host.hasSuffix("." + domain)) && url.path.hasPrefix(cookie.path) && (!cookie.isSecure || url.scheme == "https")
                }
                for (key, value) in HTTPCookie.requestHeaderFields(with: selected) { result[key] = value }
                self.finish(.success(ResolvedPlayback(url: url, headers: result)))
            }
        }
    }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation?, withError error: Error) {
        guard navigation === activeNavigation, (error as NSError).code != NSURLErrorCancelled else { return }
        finish(.failure(error))
    }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation?, withError error: Error) {
        guard navigation === activeNavigation, (error as NSError).code != NSURLErrorCancelled else { return }
        finish(.failure(error))
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        if let mime = navigationResponse.response.mimeType?.lowercased(), mime.hasPrefix("video/") || mime.hasPrefix("audio/") || mime.contains("mpegurl"), let url = navigationResponse.response.url {
            accept(url)
            decisionHandler(.cancel)
        } else { decisionHandler(.allow) }
    }
}
@MainActor
private final class WeakScriptHandler: NSObject, WKScriptMessageHandler {
    weak var owner: WebMediaSniffer?
    init(_ owner: WebMediaSniffer) { self.owner = owner }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) { owner?.userContentController(userContentController, didReceive: message) }
}
struct WebSnifferHost: UIViewRepresentable {
    let sniffer: WebMediaSniffer
    func makeUIView(context: Context) -> WKWebView { sniffer.webView }
    func updateUIView(_ view: WKWebView, context: Context) {}
}
