import SwiftUI
import VisionKit
import AVFoundation
import CoreImage.CIFilterBuiltins

struct QRScannerSheet: View {
    let onResult: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var authorized = false
    @State private var message: String?
    var body: some View {
        NavigationStack {
            Group {
                if authorized && DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                    QRScanner { result in onResult(result); dismiss() } onFailure: { message = $0 }
                } else {
                    Text(message ?? "正在检查相机权限").padding()
                }
            }.navigationTitle("扫描配置二维码")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("关闭") { dismiss() } } }
                .task {
                    guard DataScannerViewController.isSupported else { message = "此设备不支持扫码，可手动粘贴配置地址"; return }
                    authorized = await AVCaptureDevice.requestAccess(for: .video)
                    if !authorized { message = "未获得相机权限，可在系统设置中开启或手动粘贴地址" }
                    else if !DataScannerViewController.isAvailable { message = "相机当前不可用" }
                }
        }
    }
}
private struct QRScanner: UIViewControllerRepresentable {
    let onResult: (String) -> Void
    let onFailure: (String) -> Void
    func makeUIViewController(context: Context) -> ScannerHost {
        let controller = ScannerHost()
        controller.onResult = onResult
        controller.onFailure = onFailure
        return controller
    }
    func updateUIViewController(_ controller: ScannerHost, context: Context) {}
}
private final class ScannerHost: UIViewController, DataScannerViewControllerDelegate {
    var onResult: ((String) -> Void)?
    var onFailure: ((String) -> Void)?
    private var consumed = false
    private let scanner = DataScannerViewController(recognizedDataTypes: [.barcode(symbologies: [.qr])], qualityLevel: .balanced, recognizesMultipleItems: false, isHighFrameRateTrackingEnabled: false, isPinchToZoomEnabled: true, isGuidanceEnabled: true, isHighlightingEnabled: true)
    override func viewDidLoad() {
        super.viewDidLoad()
        scanner.delegate = self
        addChild(scanner)
        scanner.view.frame = view.bounds
        scanner.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(scanner.view)
        scanner.didMove(toParent: self)
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        do { try scanner.startScanning() } catch { onFailure?(error.localizedDescription) }
    }
    override func viewWillDisappear(_ animated: Bool) { scanner.stopScanning(); super.viewWillDisappear(animated) }
    func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
        guard !consumed else { return }
        for item in addedItems {
            if case .barcode(let barcode) = item, let payload = barcode.payloadStringValue {
                guard let url = URL(string: payload), ["http", "https"].contains(url.scheme?.lowercased() ?? ""), url.host != nil else {
                    onFailure?("二维码不是有效的配置地址")
                    return
                }
                consumed = true
                scanner.stopScanning()
                onResult?(url.absoluteString)
                return
            }
        }
    }
}
struct ConfigurationQRCode: View {
    let text: String
    @Environment(\.dismiss) private var dismiss
    private var image: UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 8, y: 8)), let cgImage = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                if let image { Image(uiImage: image).interpolation(.none).resizable().scaledToFit().frame(maxWidth: 280).padding(16).background(.white) }
                else { Text("无法生成二维码") }
                Text(text).font(.caption).textSelection(.enabled)
                ShareLink(item: text) { Label("分享配置地址", systemImage: "square.and.arrow.up") }
            }.padding(24).navigationTitle("配置二维码")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }
    }
}
