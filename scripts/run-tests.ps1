$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:ANDROID_HOME\platform-tools;D:\tools\gradle-8.7\bin;" + $env:Path
Set-Location -LiteralPath "D:\## APP\DALUR film"

Write-Host "== python contract tests =="
python tests\python\test_contracts.py
if ($LASTEXITCODE -ne 0) { throw "contract tests failed" }

Write-Host "== gradle unit tests =="
& "D:\tools\gradle-8.7\bin\gradle.bat" :shared:test :app:testDebugUnitTest --console=plain
if ($LASTEXITCODE -ne 0) { throw "gradle unit tests failed" }
Write-Host "ALL TESTS PASSED"
