# iOS — App Store-ready source (builds on macOS + Xcode 26 only)

This directory contains the native iOS project source for DALUR film
(Swift 6, iOS 16+, SwiftUI + AVFoundation + Metal/Core Image).

Status on Windows: **source-complete, build BLOCKED_EXTERNAL_DEPENDENCY**
(Xcode 26 + iOS 26 SDK + signing identity required; see audit).

## Structure

* `Package.swift` — SwiftPM package `DalurFilm`
* `Sources/DalurFilm/Models.swift` — FilmRecipe / CaptureMetadata / Journey /
  MonitorMode / CapabilityReport (mirrors `shared/` JSON schemas)
* `Sources/DalurFilm/DalurCameraManager.swift` — AVFoundation foundation
  (reference: NextLevel @ 2fa4250, MIT; DALUR pipeline is original)
* `Sources/DalurFilm/DalurFilmPipeline.swift` — Metal/Core Image film pipeline
* `DalurFilmApp/` (to be generated on macOS) — SwiftUI app target wiring
  Camera/Map/Journey screens to this package

## Build on macOS (exact commands)

```bash
xcodebuild -version  # must be Xcode 26+
cd ios
swift build -c release
# App target (once created in Xcode):
xcodebuild -scheme DalurFilmApp -sdk iphoneos -configuration Release archive \
  -archivePath ../builds/ios/DALUR-film.xcarchive
xcodebuild -exportArchive -archivePath ../builds/ios/DALUR-film.xcarchive \
  -exportPath ../builds/ios -exportOptionsPlist ExportOptions.plist
```

Signing: requires Apple Developer account + provisioning. Never claimed without it.

## Capability honesty (iOS)

* Log/Apple Log: query actual `AVCaptureDevice.Format`s; disable when absent.
* ProRes: enable only on hardware with a licensed ProRes encoder.
* HEVC/10-bit: gate on `AVAssetWriter` + device support.
