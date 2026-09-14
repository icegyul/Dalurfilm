$ErrorActionPreference = "Stop"
Set-Location -LiteralPath "D:\## APP\DALUR film"
python scripts\license-scan.py
if ($LASTEXITCODE -ne 0) { throw "license scan FAILED (GPL/AGPL gate)" }
Write-Host "license scan clean"
