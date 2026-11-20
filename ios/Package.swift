// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "CatTVCore", platforms: [.iOS(.v16), .macOS(.v13)], products: [.library(name: "CatTVCore", targets: ["CatTVCore"])], targets: [.target(name: "CatTVCore"), .testTarget(name: "CatTVCoreTests", dependencies: ["CatTVCore"])])
