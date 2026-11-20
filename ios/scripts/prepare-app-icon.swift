import AppKit
import Foundation

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let source = root.appendingPathComponent("app/src/main/res/drawable/cattv_launcher_foreground.png")
let destination = root.appendingPathComponent("ios/App/Assets.xcassets/AppIcon.appiconset/AppIcon.png")
guard let image = NSImage(contentsOf: source),
      let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 1024, pixelsHigh: 1024, bitsPerSample: 8, samplesPerPixel: 3, hasAlpha: false, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0),
      let context = NSGraphicsContext(bitmapImageRep: bitmap) else {
    fatalError("Cannot load Android artwork or create iOS app icon")
}
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = context
NSColor.white.setFill()
let rect = NSRect(x: 0, y: 0, width: 1024, height: 1024)
rect.fill()
image.draw(in: rect, from: .zero, operation: .sourceOver, fraction: 1)
NSGraphicsContext.restoreGraphicsState()
guard let png = bitmap.representation(using: .png, properties: [:]) else {
    fatalError("Cannot encode iOS app icon")
}
try png.write(to: destination, options: .atomic)
