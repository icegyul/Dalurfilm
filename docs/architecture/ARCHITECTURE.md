# DALUR film — architecture

## Modules
* `camera/` — Easy + PRO capture (CameraX standard, Camera2 interop for manual ISO/shutter/focus).
* `film-engine/` — `FilmRepository` (local JSON) + `FilmEngine` (GPUImage Plus rule strings + CPU fallback).
* `pro-controls/` — `CapabilityManager`, `CodecSelector`, `LogLutMonitor`, `HistogramAnalyzer`.
* `recording/` — `StoragePaths` (internal vs USB volume) + `MediaVerifier` + CameraX Recorder.
* `gps/` — `LocationTracker` (Fused, opt-in; absence saved as unavailable, never invented).
* `map/` — MapLibre MapView + polyline + markers (style URL configurable).
* `journey-engine/` — `JourneyTimeline` (route→reveal→overview) + `JourneyMp4Renderer` (MediaCodec/Muxer).
* `media-library/` — `CaptureMetadataStore` (JSON index + sidecars) + MediaStore save.
* `settings/` / `capability-detection` / `diagnostics`.

## Color contract (non-negotiable)
`Sensor → LOG/flat master file` + `Sensor → LOG/flat → monitoring LUT → display`.
Playback toggles LOG/LUT from the same master + sidecar (recipe ID/version/hash, camera settings, GPS).

## Marketplace future (interfaces only, no impl)
`FilmRepository.exportPackage()` produces the portable recipe package.
Future: `MarketplaceApi` (publish/preview/purchase), `EntitlementStore`, versioned IDs.
Phase 2 starts only after physical validation. No accounts/billing/social in v1.
