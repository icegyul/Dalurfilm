# DALUR film — v1.0.0 Release Candidate

> **Your camera. Your film. Your moments.**

Premium mobile camera app: Easy camera + Pro cinema camera + DALUR-original
Film Recipes + GPS photo map + Journey video engine.

* No community feed. No marketplace in v1 (deferred to Phase 2 after physical validation).
* Single source of truth: `DALUR_FILM_BUILD_PACK_v1_1/DALUR_FILM_ONE_SHOT_MASTER_v1.1.md`
  (Part A embeds MASTER DEVELOPMENT SPEC v1.0; Parts B/C are patch + source pack).
  The build-pack ZIP is preserved at repo root as `DALUR_FILM_BUILD_PACK_v1_1.zip`.

## Structure

```text
app/                 Android app (Kotlin + Compose, CameraX + Camera2, MediaCodec)
shared/              Versioned JSON schemas + DALUR-original film recipes + .cube LUTs
films/bundled/       8 DALUR-original starter films (JSON + .cube, no third-party assets)
ios/                 iOS App Store-ready source (Swift/SwiftUI + AVFoundation + Metal)
scripts/             clean / lint / test / build / license-scan / capability / store-preflight
store/               Play + App Store metadata drafts (not submitted)
docs/architecture/   Module contracts + marketplace-expansion notes (no marketplace impl)
docs/qa/             TEST_REPORT + FINAL_RELEASE_CANDIDATE_AUDIT
third_party/source/  Pinned reference checkouts (git-ignored, see THIRD_PARTY_LOCK.json)
release/             APK/AAB + CHECKSUMS + capability/test/store/license evidence
tests/               Python contract tests (schemas, LUTs, journey, licenses)
```

## Build (Windows / Linux with JDK 17 + Android SDK)

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME="D:\Android\Sdk"
.\scripts\build-android.ps1        # debug APK + release APK + release AAB -> release/
.\scripts\run-tests.ps1            # python contract tests + gradle unit tests
.\scripts\license-scan.ps1         # GPL/AGPL gate + THIRD_PARTY_NOTICES check
.\scripts\capability-report.ps1    # host-side capability template -> release/
```

See `docs/qa/FINAL_RELEASE_CANDIDATE_AUDIT.md` for the release audit,
`release/TEST_REPORT.md` for test evidence, and `release/STORE_READINESS.md`
for store preflight (not submitted).

## Key product rules (enforced in code)

* PRO master is never baked with the monitoring LUT. `LOG VIEW <-> LUT VIEW`
  is a monitor/playback switch; recipe ID/version/hash persisted as sidecar.
* Log is capability-gated: no genuine Log profile -> Log disabled + reason shown.
* USB-C SSD recording and external monitor/recorder output are separate capabilities.
* Journey is built only from DALUR-captured GPS + media (no Google sign-in).
* Film system is DALUR-original only. No GPL LUTs, no trademarked stock names.
