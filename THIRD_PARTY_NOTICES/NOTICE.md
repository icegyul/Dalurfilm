# DALUR film — Third-Party Notices (v1.0.0-rc1)

Generated: 2026-09-13. Exact refs pinned in `THIRD_PARTY_LOCK.json`.

## Runtime / shipped dependencies
* org.wysaid:gpuimage-plus:3.2.0-16k — MIT — https://github.com/wysaid/android-gpuimage-plus @ 32bf703b9ffe49036853d027bc3350491985feae
* org.maplibre.gl:android-sdk:11.12.2 — BSD-2-Clause — https://github.com/maplibre/maplibre-native @ 9ee6f1c3b5b97fc2cba1c1042cadef87fa158476
* AndroidX CameraX/Camera2/Compose/Media3, Play Services location — Apache-2.0
* YahiaAngelo/Film-Luts — MIT — https://github.com/YahiaAngelo/Film-Luts (13 `dalur_*` film LUTs in `shared/src/main/assets/luts/`, resampled 13pt→17pt and renamed to DALUR-original names; see `LICENSE-YahiaAngelo-Film-Luts.MIT.txt`)

## Reference only (not shipped)
* android/camera-samples @ 2f10e46fc7a09f7208f8d6de72482ecf3e5fb85a — Apache-2.0 / repo terms — CameraX/Camera2/HDR/RAW patterns
* google-timeline-visualizer @ 785c814a03b32aaa8db965589e7d498d1ded5795 — MIT — journey interaction reference only
* NextLevel @ 2fa42500caf7edd7136d23b64d9ecb5684d93d07 — MIT — iOS camera foundation reference
* PhotonCam — research only; NO code/assets vendored (GPL film-asset risk avoided by design)

## DALUR-original + licensed assets
The 8 original `dalur_*_01` `*.cube`/`*.json` recipes in `shared/src/main/assets/`
are DALUR originals. The 13 `dalur_*_1*`/`dalur_*_2*/_4*/_8*` film LUTs ship under
the MIT license above (derived from YahiaAngelo/Film-Luts G'MIC output: resampled to
17pt and renamed; recipe JSONs are DALUR-authored). No GPL film LUTs and no
trademarked film-stock names are shipped.
