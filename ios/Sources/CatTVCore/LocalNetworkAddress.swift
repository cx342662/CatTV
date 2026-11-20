import Foundation
import Darwin

public enum MediaProxyExposure: String, Equatable, Sendable {
    case loopback
    case localNetwork
}
public enum LocalNetworkAddress {
    public static func ipv4() throws -> String {
        var interfaces: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&interfaces) == 0 else { throw SourceError.invalidConfiguration("无法读取网络接口") }
        defer { if let interfaces { freeifaddrs(interfaces) } }
        var current = interfaces
        while let item = current {
            defer { current = item.pointee.ifa_next }
            let name = String(cString: item.pointee.ifa_name)
            guard name.hasPrefix("en"), let address = item.pointee.ifa_addr, Int32(address.pointee.sa_family) == AF_INET,
                  item.pointee.ifa_flags & UInt32(IFF_UP) != 0,
                  item.pointee.ifa_flags & UInt32(IFF_LOOPBACK) == 0 else { continue }
            var ipv4 = address.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr }
            var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
            let valid = buffer.withUnsafeMutableBufferPointer { pointer in
                inet_ntop(AF_INET, &ipv4, pointer.baseAddress, socklen_t(pointer.count)) != nil
            }
            if valid { return String(cString: buffer) }
        }
        throw SourceError.invalidConfiguration("未找到可用的 Wi-Fi 或有线 IPv4 地址，请连接同一局域网或关闭局域网代理")
    }
}
