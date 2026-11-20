import Foundation
import AVFoundation
import CoreGraphics
import CoreVideo
import ImageIO
import UniformTypeIdentifiers

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath).appendingPathComponent("ios/build/fixtures")
try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
let destination = root.appendingPathComponent("test.mp4")
if FileManager.default.fileExists(atPath: destination.path) { try FileManager.default.removeItem(at: destination) }
let writer = try AVAssetWriter(outputURL: destination, fileType: .mp4)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: 320, AVVideoHeightKey: 180])
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB, kCVPixelBufferWidthKey as String: 320, kCVPixelBufferHeightKey as String: 180])
guard writer.canAdd(input) else { fatalError("Cannot configure test video writer") }
writer.add(input)
guard writer.startWriting() else { fatalError("Cannot start writing test video: \(String(describing: writer.error))") }
writer.startSession(atSourceTime: .zero)
let deadline = Date().addingTimeInterval(30)
for frame in 0..<144 {
    while !input.isReadyForMoreMediaData {
        guard Date() < deadline, writer.status == .writing else { fatalError("Test video writer stalled") }
        try await Task.sleep(nanoseconds: 5_000_000)
    }
    guard let pool = adaptor.pixelBufferPool else { fatalError("No pixel buffer pool") }
    var created: CVPixelBuffer?
    guard CVPixelBufferPoolCreatePixelBuffer(nil, pool, &created) == kCVReturnSuccess, let buffer = created else { fatalError("Cannot allocate test frame") }
    CVPixelBufferLockBaseAddress(buffer, [])
    guard let address = CVPixelBufferGetBaseAddress(buffer) else { fatalError("No test frame memory") }
    let bytes = address.assumingMemoryBound(to: UInt8.self)
    let stride = CVPixelBufferGetBytesPerRow(buffer)
    for y in 0..<180 {
        for x in 0..<320 {
            let index = y * stride + x * 4
            bytes[index] = 255
            bytes[index + 1] = UInt8((x + frame) % 256)
            bytes[index + 2] = UInt8((y + frame * 2) % 256)
            bytes[index + 3] = 120
        }
    }
    CVPixelBufferUnlockBaseAddress(buffer, [])
    guard adaptor.append(buffer, withPresentationTime: CMTime(value: Int64(frame), timescale: 24)) else { fatalError("Cannot append test frame") }
}
input.markAsFinished()
await writer.finishWriting()
guard writer.status == .completed else { fatalError("Cannot complete test video: \(String(describing: writer.error))") }
let generator = AVAssetImageGenerator(asset: AVURLAsset(url: destination))
let image = try await generator.image(at: .zero).image
guard let encoder = CGImageDestinationCreateWithURL(root.appendingPathComponent("poster.png") as CFURL, UTType.png.identifier as CFString, 1, nil) else { fatalError("Cannot create test poster") }
CGImageDestinationAddImage(encoder, image, nil)
guard CGImageDestinationFinalize(encoder) else { fatalError("Cannot complete test poster") }
