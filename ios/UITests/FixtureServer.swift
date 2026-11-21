import Foundation
import Network

final class FixtureServer: @unchecked Sendable {
    private let queue = DispatchQueue(label: "com.cattv.uitests.fixture")
    private let ready = DispatchSemaphore(value: 0)
    private let lock = NSLock()
    private var listener: NWListener?
    private var initializationError: Error?
    private var port: UInt16 = 0
    private let video: Data
    private let poster: Data
    var baseURL: String { "http://127.0.0.1:\(port)" }
    init() throws {
        guard let root = Bundle(for: FixtureServer.self).resourceURL?.appendingPathComponent("fixtures") else { throw FixtureFailure.missingMedia }
        video = try Data(contentsOf: root.appendingPathComponent("test.mp4"))
        poster = try Data(contentsOf: root.appendingPathComponent("poster.png"))
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
        let server = try NWListener(using: parameters, on: .any)
        listener = server
        server.stateUpdateHandler = { [weak self] state in
            guard let self else { return }
            switch state {
            case .ready:
                self.lock.lock(); self.port = server.port?.rawValue ?? 0; self.lock.unlock()
                self.ready.signal()
            case .failed(let error):
                self.lock.lock(); self.initializationError = error; self.lock.unlock()
                self.ready.signal()
            default: break
            }
        }
        server.newConnectionHandler = { [weak self] connection in
            guard let self else { connection.cancel(); return }
            connection.start(queue: self.queue)
            self.receive(connection, accumulated: Data())
        }
        server.start(queue: queue)
        guard ready.wait(timeout: .now() + 10) == .success else { stop(); throw FixtureFailure.startupTimeout }
        lock.lock(); let error = initializationError; let assigned = port; lock.unlock()
        if let error { stop(); throw error }
        guard assigned != 0 else { stop(); throw FixtureFailure.startupTimeout }
    }
    func stop() {
        listener?.stateUpdateHandler = nil
        listener?.newConnectionHandler = nil
        listener?.cancel()
        listener = nil
    }
    private func receive(_ connection: NWConnection, accumulated: Data) {
        guard accumulated.count < 32_768 else { connection.cancel(); return }
        connection.receive(minimumIncompleteLength: 1, maximumLength: 4096) { [weak self] data, _, complete, error in
            guard let self, error == nil, let data, !data.isEmpty else { connection.cancel(); return }
            let request = accumulated + data
            if request.range(of: Data("\r\n\r\n".utf8)) != nil { self.respond(connection, request: String(decoding: request, as: UTF8.self)) }
            else if !complete { self.receive(connection, accumulated: request) }
            else { connection.cancel() }
        }
    }
    private func respond(_ connection: NWConnection, request: String) {
        let lines = request.components(separatedBy: "\r\n")
        let parts = (lines.first ?? "").split(separator: " ")
        guard parts.count >= 2 else { connection.cancel(); return }
        let method = String(parts[0])
        let path = String(parts[1]).components(separatedBy: "?").first ?? ""
        var headers: [String: String] = [:]
        for line in lines.dropFirst() {
            let pair = line.split(separator: ":", maxSplits: 1)
            if pair.count == 2 { headers[String(pair[0]).lowercased()] = pair[1].trimmingCharacters(in: .whitespaces) }
        }
        do {
            switch path {
            case "/config.json":
                let config: [String: Any] = ["sites": [["key": "fixture", "name": "CI Fixture Source", "type": 1, "api": baseURL + "/api", "playUrl": baseURL + "/parser?url="]]]
                send(connection, data: try JSONSerialization.data(withJSONObject: config), mime: "application/json", headOnly: method == "HEAD")
            case "/api":
                let item: [String: Any] = ["vod_id": "fixture-1", "vod_name": "CI Fixture Video", "vod_pic": baseURL + "/poster.png", "vod_remarks": "CI generated test media", "vod_year": 2026, "vod_content": "<p>CI-only generated media. Not bundled in the app.</p>", "vod_play_from": "Fixture Line", "vod_play_url": "Episode 1$fixture-video"]
                let response: [String: Any] = ["class": [["type_id": 1, "type_name": "Fixture"]], "list": [item], "page": 1, "pagecount": 1]
                send(connection, data: try JSONSerialization.data(withJSONObject: response), mime: "application/json", headOnly: method == "HEAD")
            case "/parser":
                let html = "<html><body><video muted autoplay playsinline src=\"\(baseURL)/test.mp4\"></video></body></html>"
                send(connection, data: Data(html.utf8), mime: "text/html", headOnly: method == "HEAD")
            case "/poster.png": send(connection, data: poster, mime: "image/png", headOnly: method == "HEAD")
            case "/test.mp4":
                if let range = headers["range"], range.hasPrefix("bytes=") {
                    let bounds = range.dropFirst(6).split(separator: "-", omittingEmptySubsequences: false)
                    let start = Int(bounds.first ?? "") ?? 0
                    let end = min(bounds.count > 1 ? (Int(bounds[1]) ?? video.count - 1) : video.count - 1, video.count - 1)
                    guard start >= 0 && start <= end else { send(connection, data: Data(), mime: "text/plain", status: 416, headOnly: method == "HEAD"); return }
                    send(connection, data: video.subdata(in: start..<end + 1), mime: "video/mp4", status: 206, extra: ["Content-Range": "bytes \(start)-\(end)/\(video.count)", "Accept-Ranges": "bytes"], headOnly: method == "HEAD")
                } else { send(connection, data: video, mime: "video/mp4", extra: ["Accept-Ranges": "bytes"], headOnly: method == "HEAD") }
            default: send(connection, data: Data(), mime: "text/plain", status: 404, headOnly: method == "HEAD")
            }
        } catch { send(connection, data: Data(), mime: "text/plain", status: 500, headOnly: false) }
    }
    private func send(_ connection: NWConnection, data: Data, mime: String, status: Int = 200, extra: [String: String] = [:], headOnly: Bool) {
        var header = "HTTP/1.1 \(status) Status\r\nContent-Type: \(mime)\r\nContent-Length: \(data.count)\r\nConnection: close\r\n"
        for (name, value) in extra { header += "\(name): \(value)\r\n" }
        let payload = Data((header + "\r\n").utf8) + (headOnly ? Data() : data)
        connection.send(content: payload, completion: .contentProcessed { _ in connection.cancel() })
    }
    private enum FixtureFailure: Error { case missingMedia, startupTimeout }
}
