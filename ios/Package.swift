// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "DalurFilm",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "DalurFilm", targets: ["DalurFilm"])
    ],
    dependencies: [
        // Camera foundation reference: NextLevel/NextLevel @ 2fa4250 (MIT).
        // Pinned in THIRD_PARTY_LOCK.json; integrated via SPM when building on macOS.
        // .package(url: "https://github.com/NextLevel/NextLevel.git", exact: "0.19.1"),
        // Map: MapLibre Native @ 9ee6f1c (BSD-2-Clause) via MapLibre SPM/Gradle artifact.
    ],
    targets: [
        .target(name: "DalurFilm", path: "Sources/DalurFilm")
    ]
)
