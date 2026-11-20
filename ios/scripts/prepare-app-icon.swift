import CoreGraphics
import ImageIO
import Foundation
import UniformTypeIdentifiers

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let source = root.appendingPathComponent("app/src/main/res/drawable/cattv_launcher_foreground.png")
let destination = root.appendingPathComponent("ios/App/Assets.xcassets/AppIcon.appiconset/AppIcon.png")
guard let sourceImage = CGImageSourceCreateWithURL(source as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(sourceImage, 0, nil) else {
    fatalError("Cannot read Android artwork: \(source.path)")
}
guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB),
      let context = CGContext(data: nil, width: 1024, height: 1024, bitsPerComponent: 8, bytesPerRow: 0, space: colorSpace, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else {
    fatalError("Cannot create opaque RGB icon context")
}
let rect = CGRect(x: 0, y: 0, width: 1024, height: 1024)
context.setFillColor(CGColor(gray: 1, alpha: 1))
context.fill(rect)
context.interpolationQuality = .high
context.draw(image, in: rect)
guard let output = context.makeImage(),
      let encoder = CGImageDestinationCreateWithURL(destination as CFURL, UTType.png.identifier as CFString, 1, nil) else {
    fatalError("Cannot create PNG output")
}
CGImageDestinationAddImage(encoder, output, nil)
guard CGImageDestinationFinalize(encoder) else { fatalError("Cannot write iOS app icon") }
