# DALUR film — Implementation Audit

Source of truth: `DALUR_FILM_BUILD_PACK_v1_1.zip` (`DALUR_FILM_ONE_SHOT_MASTER_v1.1.md`, MASTER_SPEC_PATCH v1.1, GITHUB_SOURCE_PACK v1.0).
Date: 2026-09-14
Scope: analysis only. No code written. Statuses: `EXISTING` / `PARTIAL` / `MISSING` / `BROKEN` / `CONFLICT`.

---

## 1. Project Overview

DALUR film is specified as a premium paid (USD 7.99) camera app: Easy camera + PRO cinema mode +
DALUR-original Film Recipes + GPS photo map + Journey video engine. No marketplace/community/website
in v1. Android APK/AAB is the P0 deliverable; iOS is an App Store-ready project (P2).

What the workspace actually contains: a **single Gradle project** (`:app` Android app + `:shared`
JVM/Kotlin model + asset module) plus an **iOS SwiftPM skeleton**, already built (APK/AAB exist in
`release/`). The build pack ZIP is preserved at the repo root; the six spec/prompt documents inside
it were consumed to generate the code and are **not** extracted anywhere in the workspace.

| Dimension | Assessment |
|---|---|
| Android build | `EXISTING` — debug/release APK + AAB produced (release/), signed with the debug keystore |
| Automated tests | `PARTIAL` — 16 Gradle unit tests + Python contract tests green; no instrumented/device/integration tests |
| iOS | `PARTIAL` — SPM library skeleton; no SwiftUI app target, build `BLOCKED_EXTERNAL_DEPENDENCY` (Windows) |
| Release evidence | `PARTIAL` — evidence pack incomplete (see §11) |
| Source control | `CONFLICT` — repo `main` has **zero commits**; everything untracked |

---

## 2. Existing Architecture

- **Modules (Gradle):** `:app` (Android, Compose) and `:shared` (versioned JSON schemas, business
  rules, DALUR recipes/LUT assets). Layout: `app/`, `shared/`, `ios/`, `scripts/`, `store/`,
  `docs/`, `tests/`, `third_party/`, `release/`, `builds/`.
- **Android packages (`com.dalur.film`):** `camera` (EasyCameraScreen/CameraViewModel/ProCameraScreen),
  `film` (FilmRepository/FilmEngine/FilmsScreen), `pro` (CapabilityManager/CodecSelector/HistogramAnalyzer),
  `recording` (Storage), `gps` (LocationTracker), `media` (CaptureMetadataStore), `map` (MapScreen),
  `journey` (Timeline/Mp4Renderer/Store/ExportService/JourneysScreen), `playback`, `settings`,
  `navigation`, `diagnostics`, `ui/{components,theme}`.
- **Key stack:** Kotlin 1.9.24, AGP 8.5.2, JDK 17, compileSdk/targetSdk 36, minSdk 28; Compose BOM
  2024.06 + Material3; CameraX 1.3.4; Media3 1.4.1 (ExoPlayer/Transformer); GPUImage Plus
  `3.2.0-16k` (MIT); MapLibre android-sdk 11.12.2 (BSD-2); Play Services Location; DataStore.
- **Shared module:** `FilmRecipe` (versioned, backward compatible), `CaptureMetadata`, `Journey`,
  `CapabilityReport`, `ExportPresets`, `CubeLut` parser/hash, deterministic filename + journey-hold
  math. Asset sources (`shared/src/main/assets/recipes|luts`) are wired into `:app` via sourceSets.
- **iOS (`ios/`):** SwiftPM `DalurFilm` package — `Models.swift` (Codable mirror of the Kotlin
  schemas), `DalurCameraManager.swift` (AVFoundation stub), `DalurFilmPipeline.swift`
  (Core Image tone/color subset). No app target, no NextLevel/MapLibre dependency wired
  (commented out), no Journey/Map/UI.
- **Legal:** `THIRD_PARTY_LOCK.json` pins all 6 sources; `THIRD_PARTY_NOTICES/` present on disk;
  `third_party/source/` checkouts verified at the pinned refs.

Architecture intent (native-first, hardware-honest capability gating) is documented in
`docs/architecture/ARCHITECTURE.md` and matches the spec. The gap is execution depth, not shape.

---

## 3. Existing Features

State per feature. Ratings reflect *real, wired behavior*, not declared intent.

| Feature | State | Notes / evidence |
|---|---|---|
| Photo capture → gallery → metadata index | `EXISTING` | CameraX `ImageCapture` → MediaStore (`DCIM/DALUR`), `CaptureMetadata` index JSON + attempted sidecar (`EasyCameraScreen.kt:148-208`) |
| Video capture → gallery → metadata index | `EXISTING` | CameraX `Recorder` → MediaStore (`Movies/DALUR`), audio on, HEVC/H264 label from capability (`EasyCameraScreen.kt:210-290`) |
| Front/back switch, Photo/Video toggle, flash (photo) | `EXISTING` | `switchLens`, segmented control, `FLASH_MODE_ON` |
| Film recipe store (bundled seed, hash-verified LUTs, save/duplicate/reset) | `EXISTING` | `FilmRepository.kt`; 8 bundled recipes validated against `.cube` sha256 |
| 8 DALUR-original recipes + 17pt `.cube` LUTs | `EXISTING` | `shared/src/main/assets/recipes|luts`; no trademarked names (contract test) |
| Runtime capability detection | `EXISTING` | Cameras/lenses, HEVC/10-bit/HDR/RAW, manual ISO/shutter/focus, USB removable volumes; Log never faked — conservative `HLG-FLAT-10BIT` label only (`CapabilityManager.kt`) |
| PRO panel scaffold (LOG/LUT monitor toggle, codec/ISO/shutter/HEVC chips, USB target chips) | `PARTIAL` | UI + state exist; most do not drive the camera/recorder (see §4/§5) |
| GPS metadata at capture | `PARTIAL` | `LocationTracker.lastFix()` is called, but no runtime location permission flow exists → GPS is effectively always "unavailable" on a fresh install |
| Map screen | `PARTIAL` | MapLibre polyline + markers + detail card work, but the map does not refresh when new captures arrive and the tile style is the network-dependent demo server |
| Journey build + timeline + player + MP4 export | `PARTIAL` | Real timeline math and a real MediaCodec/MediaMuxer MP4 exist, but preview and export are **not** a route-map animation (see §4/§5) |
| Playback (ExoPlayer) | `PARTIAL` | Playback works; LOG/LUT toggle changes a label only, no color transform |
| Settings (intensity, pro toggle, map style, reduced motion) | `PARTIAL` | Persisted via DataStore; most spec settings are absent (see §4) |
| Diagnostics / capability screen | `EXISTING` | Full JSON capability report + re-run |
| Unit tests + contract tests | `EXISTING` | 16 Gradle tests (all 0 failures in `build/test-results`) + `tests/python/test_contracts.py` |
| Release artifacts | `EXISTING` | `release/DALUR-film-android-{debug,release}.apk`, `release/DALUR-film-android-release.aab` |
| App store metadata drafts | `EXISTING` | `store/` has all 15 files from spec §23 |
| Third-party lock + notices + pinned source checkouts | `EXISTING` | Refs verified; PhotonCam correctly excluded from vendoring |

---

## 4. Missing Features

Items required by the spec that have **no implementation**:

1. **Film Creator (Easy + PRO)** (spec §9). Only an intensity slider + duplicate/reset exists
   (`FilmsScreen.kt:87-113`). No mood/warm-cool/grain/glow, no curves/HSL/grading, no split
   preview, no "SAVE FILM" creator flow.
2. **Real .cube 3D LUT application** anywhere. LUTs are parsed, hash-verified and stored as
   metadata, but never applied to a pixel on device. GPUImage Plus needs a 512×512 LUT *PNG*, not a
   `.cube` (`docs/filter-rules.md:169`); no PNG LUT, no LUT shader/Metal stage exists.
3. **Real-time live film preview.** `FilmPreviewOverlay` is a flat color wash
   (`EasyCameraScreen.kt:323-326,455-458`, `filmTint`), not a filter pipeline.
4. **PRO manual control application.** ISO/shutter/WB/tint/focus set ViewModel state only; no
   `Camera2Interop`/Camera2 low-level path — the spec's "Camera2 for low-level PRO controls" is
   entirely absent. ISO/shutter can never actually change an exposure.
5. **Codec/fps wired to recording.** `codecLabel` and `fps` are never passed to `Recorder`;
   HEVC/H264 label in metadata is inferred from capability, not from what was actually encoded.
6. **Monitoring tools:** `HistogramAnalyzer` is never attached to any `ImageAnalysis` use case;
   `HistogramView` is never drawn; zebra/peaking have no rendering. Chip is UI-only.
7. **External USB-C SSD recording write path.** `RecordTarget.Usb` can be selected, but video
   always writes to internal app storage; `StoragePaths.usbCaptures`, `MediaVerifier` are dead code;
   no write/finalize to the volume, no disconnect handler, no DALUR clip file browser, no
   free-space actually used in the capture path.
8. **Runtime location-permission request.** Manifest declares FINE/COARSE but no launcher requests
   them; `LocationTracker.lastFix()` throws → returns null → `locationUnavailable=true`.
9. **Playback LOG/LUT transform.** ExoPlayer view with a mode chip; no re-application of the recipe
   (spec §5.6).
10. **Journey route-map rendering/export.** The exported MP4 draws static text/title cards
    (`JourneyMp4Renderer.kt:51-75`); no map tiles, no path draw, no moving camera, no photo
    reveal thumbnails, no final route overview, no grain overlay, no audio. Preview is a pulsing
    dot + text card.
11. **1080p export.** `DALUR_EXPORT_PRESETS` advertise 1080p, but the renderer hard-codes 720
    (`720×1280 / 720×720 / 1280×720`) (`JourneyMp4Renderer.kt:24-28`).
12. **Journey foreground service use.** `JourneyExportService` is declared (manifest + class) but
    never started; export runs on the UI coroutine (`JourneysScreen.kt:188-199`).
13. **Full Settings screen** (storage location, export quality, haptics, about, licenses, privacy,
    terms, diagnostics, external-ssd status, photo-library/mic permission rows). SettingsScreen
    only lists them in a caption (`SettingsScreen.kt:73-77`).
14. **Per-device lens selector** (multi-camera lenses). Only front/back toggle exists.
15. **Release evidence** (`MASTER_SPEC_PATCH` §8): `CHECKSUMS.txt`, `DEVICE_CAPABILITY_REPORT.json`,
    `TEST_REPORT.md`, `STORE_READINESS.md`, `release/THIRD_PARTY_LOCK.json`,
    `release/THIRD_PARTY_NOTICES/*` (dir is empty) — all `MISSING`.
16. **`docs/qa/FINAL_RELEASE_CANDIDATE_AUDIT.md`** (spec §28) — `docs/qa/` is empty.
17. **Automation scripts** (spec §27): only `build-android.ps1`, `run-tests.ps1`,
    `license-scan.ps1` exist. No clean / lint / capability-report / store-preflight / iOS-build
    scripts.
18. **Integration/instrumented/device tests** (spec §20) — none.
19. **iOS app**: SwiftUI app target, Journey, Map, LUT/Metal stages, NextLevel/MapLibre wiring —
    all absent. iOS model also drifts (`Models.swift` lacks `hsl`, `lightLeak`, `frame`,
    `accuracyMeters` that Kotlin `FilmRecipe`/`GpsPoint` have) → schema CONFLICT.
20. **Git history** (spec §29): no commits, so no milestone commits and no final SHA.

Also `MISSING`/unused: zoom is state-only (never applied to the camera), torch is not
implemented, stabilization is not configured.

---

## 5. Broken Features

1. **Photo film application via GPUImage rule strings — `BROKEN`.** `FilmEngine.ruleString` emits
   `@adjust lut <cube> <k>`, `@adjust grain …`, `@adjust halation …`, `@adjust bloom …`,
   `@adjust vignette …`, `@adjust whitebalance …`. The CGE rule engine supports only:
   brightness/contrast/saturation/monochrome/sharpen/blur/**whitebalance**/shadowhighlight/hsv/
   hsl/level/exposure/colorbalance/**lut (512×512 PNG)** (`docs/filter-rules.md:154-169`), and
   `@vignette low range` (not a single float). `grain`/`halation`/`bloom` are unknown tokens, `lut`
   cannot take a `.cube`, and the whitebalance `tint` semantics differ (tint range is `[0,5]`,
   `1`=none). `applyFilmToJpeg` wraps the whole call in `runCatching`/try-catch and **silently
   returns the original bitmap** on failure (`EasyCameraScreen.kt:478-493`), so photos can ship
   with no film, or with only exposure/contrast/saturation (which the bundled recipes mostly do not
   set). The feature is therefore unreliable and effectively non-functional for the shipped
   recipes' LUT/effects.
2. **GPS capture — `BROKEN` in practice** (no runtime permission) as described in §4.8.
3. **Journey MP4 dimension mapping** — presets named `..._1080P_...` render at 720p; the export
   format text is misleading.
4. **Map staleness** — `AndroidView` factory closure captures `gpsCaptures` only at creation;
   recomposition/new captures never update the MapLibre map (`MapScreen.kt:47-111`).
5. **Sidecars not written for gallery-published media** — after MediaStore save the URI becomes
   `content://…`; `CaptureMetadataStore.insert` only writes `.dalur.json` for `file://` URIs, so
   per-file sidecars are effectively never produced (§10).
6. **README/artifact mismatch — `CONFLICT`** (details in §7).

---

## 6. Camera API

| Area | State | Notes |
|---|---|---|
| CameraX Preview/ImageCapture/Recorder | `EXISTING` | Rebind on mode/lens/flash change via `unbindAll()` |
| CameraX Interop → Camera2 (manual controls) | `MISSING` | No `Camera2Interop.Extender`, no CaptureRequest anywhere |
| Camera2 capability probing | `EXISTING` | `CapabilityManager` uses `CameraCharacteristics` (lenses, ISO/exposure/focus ranges, RAW, 10-bit) |
| Log gating | `EXISTING` | Honest, conservative (HLG-FLAT only when 10-bit capture + HEVC-10 encoder); never fakes vendor Log |
| HEVC selection in recorder | `PARTIAL` | Detected + reported; actual encoder chosen implicitly by CameraX, not by `codecLabel` |
| Manual ISO/shutter/focus/WB/tint | `PARTIAL` | State only; not applied |
| FPS control | `MISSING` | `fps` in state; Recorder never receives it |
| Torch / stabilization / aspect control | `MISSING` | — |
| Photo RAW/DNG | `MISSING` | RAW only probed, never captured |
| Histogram analysis | `BROKEN` | Analyzer unplugged; no `ImageAnalysis` use case bound |

---

## 7. UI Architecture

- **Structure (COMPOSE):** Single `MainActivity` + `DalurNav` `NavHost`; bottom bar with 4 tabs —
  CAMERA / FILMS / MAP / JOURNEYS (matches spec IA). Detail routes: `settings`, `capability`,
  `journey/{id}`, `playback?uri=` (`navigation/DalurNav.kt`).
- **Shared ViewModel:** one `CameraViewModel` is shared by all tabs (films/captures/capabilities/
  settings) — pragmatic, but couples camera state to browse screens.
- **Theme:** cinema-dark Material3 palette + typography (`ui/theme/Theme.kt`), consistent visual
  identity. `EXISTING`.
- **Components:** `DalurHeader`, `DalurSectionLabel`, `FailureState` used consistently.
  `EXISTING`.
- **Accessibility:** icon contentDescriptions present; TalkBack labels for chips/tabs OK;
  reduced-motion toggle exists and shortens-journey holds approach. No full
  localization (all UI strings are hard-coded English). `PARTIAL`.
- **Film strip:** text tabs, not real-time preview thumbnails as the spec requires (§4.2).
  `PARTIAL`.

---

## 8. Storage

| Area | State | Notes |
|---|---|---|
| Recipes (bundled + local edits) | `EXISTING` | `filesDir/films/*.json`; assets seeded with hash verification |
| Capture index | `EXISTING` | `filesDir/captures/*.json` (`CaptureMetadataStore`) |
| Per-media sidecar JSON | `BROKEN` | Only written when URI is `file://`; gallery publishes `content://` → sidecar skipped |
| Journey store | `EXISTING` | `filesDir/journeys/*.json` |
| Preferences | `EXISTING` | DataStore (`dalur_settings`) |
| Media output | `EXISTING` | MediaStore `DCIM/DALUR` (photo), `Movies/DALUR` (video) on Q+; legacy pre-Q path DEPRECATED used only <Q |
| Room/SQLite | `MISSING` (by design) | Spec allows file+JSON; consistent with README ("Room-free by design") |
| USB external volume writes | `MISSING` | See §4.7 |

---

## 9. Permissions

Manifest declares (`app/src/main/AndroidManifest.xml`): CAMERA, RECORD_AUDIO, FINE/COARSE
location, READ_MEDIA_IMAGES/VIDEO, ACCESS_MEDIA_LOCATION, WRITE_EXTERNAL_STORAGE (≤28),
FOREGROUND_SERVICE + DATA_SYNC + MEDIA_PROJECTION, POST_NOTIFICATIONS, INTERNET/NETWORK_STATE.

Runtime requests actually made:
- CAMERA → on first camera open (`EasyCameraScreen.kt:90-96`). `EXISTING`.
- RECORD_AUDIO → when video starts (`:219-223`). `EXISTING`.
- **Location → never requested.** Declared, therefore GPS capture is effectively disabled.
  `BROKEN`.
- READ_MEDIA_* / POST_NOTIFICATIONS → not requested (not required for current MediaStore-only
  flows). Acceptable.
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` declared but no media-projection feature exists — dead
  declaration.

---

## 10. Platform

- **Android:** Gradle 8.7, AGP 8.5.2, compileSdk=36 / targetSdk=36 / minSdk=28, JDK 17. Builds
  green (artifacts on disk). `release` build signed with the **debug keystore** → locally signed
  RC; Play upload is `BLOCKED_EXTERNAL_DEPENDENCY` (upload key). 64-bit ABIs configured
  (`arm64-v8a`, `armeabi-v7a`, `x86_64`); GPUImage Plus 16k variant used for Android 15+ 16 KB
  page-size compliance — yes, small `pagesize` in use is unverified on device.
- **iOS:** SwiftPM package, iOS 16 target, building only on macOS/Xcode 26. On
  Windows: `BLOCKED_EXTERNAL_DEPENDENCY` (documented honestly in `ios/README-iOS.md`). No SwiftUI
  app wiring yet → **not** App Store-ready source despite that README title.
- **Test evidence:** 10 shared + 6 app unit tests (0 failures) + Python contract suite. No
  instrumentation, no real-device run → spec §20 device tests `NOT_RUN`.

---

## 11. Performance risks

1. **Photo film path:** `BitmapFactory.decodeFile` full frame + GPU filter + JPEG re-encode on the
   IO dispatcher per shot; no downsampling, no buffer reuse, no sampling scale. Memory spikes on
   modern 48MP sensors despite `largeHeap=true`.
2. **in-process rule filtering on unknown tokens:** relies on a silent exception fallback; any
   crash inside native code is unobserved.
3. **Camera rebinding** (`unbindAll` on mode/lens/flash) causes preview dropout and re-configure
   latency on every toggle.
4. **Journey export blocks the UI** — runs on the main coroutine scope; long routes freeze the
   UI; the (declared) foreground service is never used; no cancellation; no progress.
5. **Journey renderer allocations:** a new ARGB_8888 bitmap + Canvas per card, per frame drawn via
   `surface.lockCanvas` without buffer reuse.
6. **Map depends on network demo tiles** (`demotiles.maplibre.org`) — no offline tiles; map fails
   open on no-network situations (raw view fallback only catches exceptions, not offline).
7. **Per-frame CPU histogram sampling** (even though currently unplugged) reads Y-plane rows on the
   analyzer thread — would compete with preview if ever attached without downsampling.
8. **Startup:** films seeded + capability refresh on `Dispatchers.Default` at `Application.onStart`
   — acceptable; heavy libs (MapLibre) load lazily per screen.
9. **MediaCodec surface mode:** the Journey renderer pushes full canvas frames to the encoder
   surface without frame pacing (no sleep); on-device the encoder may block. Acceptable for short
   clips, risky for 60s.

---

## 12. Implementation Plan (priority-ordered, gap-closing only)

Each item is a *missing/broken gap* to the spec — do not re-implement what already works.

**P0 — honest core behaviors first**
1. Fix the film-output path: either vendor GPUImage Plus LUT **PNG** lookups (or a real
   `.cube`→PNG/3D-texture shader stage) and use only rule tokens the CGE engine supports; remove
   silent-failure fallback; add a golden-pixel unit test proving the recipe changes output.
2. Wire last-mile video codec to the actual encoder choice (assert `Recorder` output matches
   `codecLabel`) and make fps participate in the recorder config, or drop the fake controls.
3. Add runtime location permission request + `LocationTracker` start/stop; persist GPS per capture.
4. Wire `HistogramAnalyzer` to an `ImageAnalysis` use case and render `HistogramView`/zebra;
   remove unproduced HUD elements (audio level, storage free) or feed them.

**P0 — journey truth**
5. Replace static-card MP4 with a real route render (map snapshot/DALUR tiles + polyline + moving
   camera + photo reveal + overview) at the preset's true resolution (1080p), and move export onto
   a real `JourneyExportService`/WorkManager job with progress + cancellation.
6. Implement playback LOG/LUT as an actual color transform (GPU) with the capture sidecar, not a
   label; keep master untouched.

**P0 — release evidence (spec §28, patch §8)**
7. Generate `CHECKSUMS.txt`, `DEVICE_CAPABILITY_REPORT.json`, `TEST_REPORT.md`,
   `STORE_READINESS.md` (honest states: `PASS`/`PASS_WITH_LIMITATIONS`/`BLOCKED_EXTERNAL_DEPENDENCY`/
   `NOT_RUN`), populate `release/THIRD_PARTY_NOTICES/`, write
   `docs/qa/FINAL_RELEASE_CANDIDATE_AUDIT.md`.
8. Commit the milestone history and record the final SHA (spec §29); remove misleading README
   references (missing `capability-report.ps1`, missing `DALUR_FILM_BUILD_PACK_v1_1/` dir,
   claimed-but-absent `docs/qa`/release files).

**P1 — PRO**
9. Add `Camera2Interop` for ISO/shutter/WB/focus + real per-device lens selector + torch + zoom.
10. External USB-C SSD capture path (`StoragePaths`/`MediaVerifier` actually used, error states,
    disconnect recovery, clip browser). Do not conflate with monitor output.

**P1 — Film system**
11. Full Film Creator (Easy + PRO controls) writing versioned portable recipe packages
    (interface already exported by `FilmRepository.exportPackage`).

**P2 — iOS**
12. SwiftUI app target + NextLevel/MapLibre wiring + Journey/Map; sync `Models.swift` with the
    Kotlin schema (hsl/lightLeak/frame/accuracy); build/archive on macOS.

**P2 — quality**
13. Instrumented + device tests (spec §20); lint tasks; accessibility/localization pass.

---

## Summary of state counts

`EXISTING` — photo/video capture, gallery+metadata, recipe store, capability detection, map
basics, journey timeline+MP4 skeleton, playback, settings subset, diagnostics, tests, build, store
pack.  
`PARTIAL` — PRO panel (state-only), live film preview, GPS capture, map refresh, journey animation
preview, journey export, playback LUT, settings, scripts, iOS, Moni artifacts.  
`MISSING` — Film Creator, real LUT application, manual control wiring, monitoring tools, USB-C
write path, location runtime grant, release evidence (5 files), final audit doc, lint/CI scripts,
instrumented tests, git history, iOS app target.  
`BROKEN` — GPU rule-string film application, GPS capture without permission, journey 1080p, sidecar
writing, map staleness.  
`CONFLICT` — spec pack unextracted vs README path claims; README references missing script/files;
release evidence claimed but absent; iOS model drift; zero-commit repo vs spec §29.