$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools;D:\tools\gradle-8.7\bin;" + $env:Path
Set-Location -LiteralPath "D:\## APP\DALUR film"
New-Item -ItemType Directory -Force -Path release, builds\android | Out-Null
Write-Host "== assemble =="
& "D:\tools\gradle-8.7\bin\gradle.bat" :app:assembleDebug :app:assembleRelease :app:bundleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw "gradle build failed" }
Copy-Item app\build\outputs\apk\debug\app-debug.apk release\DALUR-film-android-debug.apk -Force
Copy-Item app\build\outputs\apk\release\app-release.apk release\DALUR-film-android-release.apk -Force
Copy-Item app\build\outputs\bundle\release\app-release.aab release\DALUR-film-android-release.aab -Force
Copy-Item release\DALUR-film-android-debug.apk builds\android\ -Force
Copy-Item release\DALUR-film-android-release.apk builds\android\ -Force
Copy-Item release\DALUR-film-android-release.aab builds\android\ -Force
Get-ChildItem release\DALUR-film-android-* | Format-Table Name, Length
Write-Host "BUILD OK"
