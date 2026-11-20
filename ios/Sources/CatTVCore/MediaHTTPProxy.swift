import Foundation
import Network

public actor MediaHTTPProxy {
    private let origin: URL
    public nonisolated let exposure: MediaProxyExposure
    private var advertisedHost = "127.0.0.1"
    private let headers: [String: String]
    private let session: URLSession
    private let queue = DispatchQueue(label: "com.cattv.ios.media-proxy", qos: .userInitiated)
    private var listener: NWListener?
    private var port: UInt16?
    private var routes: [String: URL] = [:]
    private var reverseRoutes: [URL: String] = [:]
    private var connections: [UUID: NWConnection] = [:]
    private var tasks: [UUID: Task<Void, Never>] = [:]
    public init(origin: URL, headers: [String: String], session: URLSession? = nil, exposure: MediaProxyExposure = .loopback) {
        self.origin = origin
        self.exposure = exposure
        self.headers = headers
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 30
        self.session = session ?? URLSession(configuration: configuration)
    }
    public func start() async throws -> URL {
        if exposure == .loopback { advertisedHost = "127.0.0.1" }
        else { advertisedHost = try LocalNetworkAddress.ipv4() }
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: NWEndpoint.Host(exposure == .loopback ? "127.0.0.1" : "0.0.0.0"), port: .any)
        let server = try NWListener(using: parameters, on: .any)
        listener = server
        server.newConnectionHandler = { [weak self] connection in
            Task { await self?.accept(connection) }
        }
        let assigned: UInt16 = try await withCheckedThrowingContinuation { continuation in
            let gate = ListenerGate(continuation)
            server.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    if let port = server.port { gate.finish(.success(port.rawValue)) }
                    else { gate.finish(.failure(SourceError.invalidConfiguration("本地播放代理没有端口"))) }
                case .failed(let error): gate.finish(.failure(error))
                case .cancelled: gate.finish(.failure(CancellationError()))
                default: break
                }
            }
            server.start(queue: queue)
        }
        port = assigned
        return try address(for: origin)
    }
    public func stop() {
        listener?.cancel(); listener = nil
        for task in tasks.values { task.cancel() }
        for connection in connections.values { connection.cancel() }
        tasks.removeAll(); connections.removeAll(); routes.removeAll(); reverseRoutes.removeAll()
        session.invalidateAndCancel()
    }
    private func address(for url: URL) throws -> URL {
        guard ["http", "https"].contains(url.scheme?.lowercased() ?? ""), url.host != nil, let port else { throw SourceError.invalidURL }
        let path: String
        if let existing = reverseRoutes[url] { path = existing }
        else {
            guard routes.count < 50_000 else { throw SourceError.invalidConfiguration("播放代理资源数达到上限，请重新播放") }
            path = UUID().uuidString
            routes[path] = url
            reverseRoutes[url] = path
        }
        guard let local = URL(string: "http://\(advertisedHost):\(port)/\(path)") else { throw SourceError.invalidURL }
        return local
    }
    private func accept(_ connection: NWConnection) {
        guard connections.count < 32 else { connection.cancel(); return }
        let id = UUID()
        connection.start(queue: queue)
        connections[id] = connection
        tasks[id] = Task { [weak self] in
            guard let self else { return }
            await self.serve(connection, id: id)
        }
    }
    private func serve(_ connection: NWConnection, id: UUID) async {
        defer { connection.cancel(); connections.removeValue(forKey: id); tasks.removeValue(forKey: id) }
        var responseStarted = false
        do {
            let raw = try await receiveHeaders(connection)
            let lines = raw.components(separatedBy: "\r\n")
            let first = (lines.first ?? "").split(separator: " ")
            guard first.count == 3, ["GET", "HEAD"].contains(String(first[0])) else { throw SourceError.invalidConfiguration("不支持的代理请求") }
            let path = String(first[1]).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            guard let url = routes[path] else {
                try await send(Data("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".utf8), connection)
                return
            }
            var request = URLRequest(url: url)
            request.httpMethod = String(first[0])
            for (name, value) in headers {
                if ["authorization", "cookie"].contains(name.lowercased()), url.host != origin.host { continue }
                if HeaderPolicy.isSendable(name: name, value: value) { request.setValue(value, forHTTPHeaderField: name) }
            }
            request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
            if url.pathExtension.lowercased() != "m3u8" {
                for line in lines.dropFirst() {
                    let pair = line.split(separator: ":", maxSplits: 1)
                    if pair.count == 2, ["range", "if-range"].contains(pair[0].lowercased()) {
                        request.setValue(pair[1].trimmingCharacters(in: .whitespaces), forHTTPHeaderField: String(pair[0]))
                    }
                }
            }
            let (bytes, response) = try await session.bytes(for: request)
            guard let response = response as? HTTPURLResponse else { throw SourceError.invalidURL }
            let mime = response.value(forHTTPHeaderField: "Content-Type") ?? "application/octet-stream"
            let manifest = mime.lowercased().contains("mpegurl") || url.pathExtension.lowercased() == "m3u8"
            if manifest && request.httpMethod != "HEAD" && (200..<300).contains(response.statusCode) {
                var data = Data()
                for try await byte in bytes {
                    guard data.count < 2_000_000 else { throw SourceError.invalidConfiguration("播放列表过大") }
                    data.append(byte)
                }
                let rewritten = try HLSPlaylistRewriter.rewrite(data, baseURL: response.url ?? url) { try address(for: $0).absoluteString }
                let head = "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: \(rewritten.count)\r\nConnection: close\r\n\r\n"
                responseStarted = true
                try await send(Data(head.utf8), connection)
                try await send(rewritten, connection)
                return
            }
            var fields = "HTTP/1.1 \(response.statusCode) Status\r\nConnection: close\r\n"
            for name in ["Content-Type", "Content-Length", "Content-Range", "Accept-Ranges", "ETag", "Last-Modified"] {
                if let value = response.value(forHTTPHeaderField: name), HeaderPolicy.isSendable(name: name, value: value) { fields += "\(name): \(value)\r\n" }
            }
            let chunked = response.value(forHTTPHeaderField: "Content-Length") == nil && request.httpMethod != "HEAD"
            if chunked { fields += "Transfer-Encoding: chunked\r\n" }
            responseStarted = true
            try await send(Data((fields + "\r\n").utf8), connection)
            if request.httpMethod == "HEAD" { return }
            var buffer = Data()
            for try await byte in bytes {
                try Task.checkCancellation()
                buffer.append(byte)
                if buffer.count >= 65_536 { try await sendBody(buffer, chunked: chunked, connection); buffer.removeAll(keepingCapacity: true) }
            }
            if !buffer.isEmpty { try await sendBody(buffer, chunked: chunked, connection) }
            if chunked { try await send(Data("0\r\n\r\n".utf8), connection) }
        } catch {
            if !Task.isCancelled && !responseStarted {
                let body = Data("媒体代理请求失败".utf8)
                let head = "HTTP/1.1 502 Bad Gateway\r\nContent-Length: \(body.count)\r\nConnection: close\r\n\r\n"
                do { try await send(Data(head.utf8) + body, connection) } catch { connection.cancel() }
            }
        }
    }
    private func sendBody(_ data: Data, chunked: Bool, _ connection: NWConnection) async throws {
        if chunked { try await send(Data((String(data.count, radix: 16) + "\r\n").utf8) + data + Data("\r\n".utf8), connection) }
        else { try await send(data, connection) }
    }
    private func send(_ data: Data, _ connection: NWConnection) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            connection.send(content: data, completion: .contentProcessed { error in
                if let error { continuation.resume(throwing: error) } else { continuation.resume() }
            })
        }
    }
    private func receiveHeaders(_ connection: NWConnection) async throws -> String {
        var result = Data()
        while result.count < 32_768 {
            let part: Data = try await withCheckedThrowingContinuation { continuation in
                connection.receive(minimumIncompleteLength: 1, maximumLength: 4096) { data, _, complete, error in
                    if let error { continuation.resume(throwing: error) }
                    else if let data, !data.isEmpty { continuation.resume(returning: data) }
                    else { continuation.resume(throwing: SourceError.invalidConfiguration(complete ? "请求已关闭" : "空请求")) }
                }
            }
            result += part
            if result.range(of: Data("\r\n\r\n".utf8)) != nil { return String(decoding: result, as: UTF8.self) }
        }
        throw SourceError.invalidConfiguration("代理请求头过大")
    }
}
private final class ListenerGate: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<UInt16, Error>?
    init(_ continuation: CheckedContinuation<UInt16, Error>) { self.continuation = continuation }
    func finish(_ result: Result<UInt16, Error>) {
        lock.lock()
        let value = continuation
        continuation = nil
        lock.unlock()
        value?.resume(with: result)
    }
}
