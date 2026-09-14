param(
  [string]$Root = "third_party\source"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $Root | Out-Null

function Fetch-Repo($Name, $Url, $Ref) {
  $Dir = Join-Path $Root $Name
  if (Test-Path (Join-Path $Dir ".git")) {
    git -C $Dir fetch --depth 1 origin $Ref
  } else {
    git clone --filter=blob:none --no-checkout $Url $Dir
  }
  git -C $Dir fetch --depth 1 origin $Ref
  git -C $Dir checkout --detach $Ref
}

Fetch-Repo "android-camera-samples" `
  "https://github.com/android/camera-samples.git" `
  "2f10e46fc7a09f7208f8d6de72482ecf3e5fb85a"

Fetch-Repo "android-gpuimage-plus" `
  "https://github.com/wysaid/android-gpuimage-plus.git" `
  "32bf703b9ffe49036853d027bc3350491985feae"

Fetch-Repo "google-timeline-visualizer" `
  "https://github.com/mahlernim/google-timeline-visualizer.git" `
  "785c814a03b32aaa8db965589e7d498d1ded5795"

Fetch-Repo "NextLevel" `
  "https://github.com/NextLevel/NextLevel.git" `
  "2fa42500caf7edd7136d23b64d9ecb5684d93d07"

Fetch-Repo "maplibre-native" `
  "https://github.com/maplibre/maplibre-native.git" `
  "9ee6f1c3b5b97fc2cba1c1042cadef87fa158476"

Write-Host "DALUR film GitHub sources fetched and pinned."
