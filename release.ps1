# Builds a signed release APK and updates version.json to match app/build.gradle.kts.
# Usage:  ./release.ps1 -Notes "What changed"
param([string]$Notes = "Update")

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$gradleFile = Join-Path $root "app\build.gradle.kts"
$g = Get-Content $gradleFile -Raw

$code = [regex]::Match($g, 'versionCode\s*=\s*(\d+)').Groups[1].Value
$name = [regex]::Match($g, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $code -or -not $name) { throw "Could not read versionCode/versionName" }

$tag = "v$name"
$json = [ordered]@{
  versionCode = [int]$code
  versionName = $name
  tag         = $tag
  apk         = "app-release.apk"
  notes       = $Notes
} | ConvertTo-Json
Set-Content -Path (Join-Path $root "version.json") -Value $json -Encoding utf8
Write-Host "version.json -> code $code, name $name, tag $tag"

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& (Join-Path $root "gradlew.bat") -p $root assembleRelease
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

$apk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
Write-Host ""
Write-Host "Built: $apk"
Write-Host "Next:"
Write-Host "  1) git add -A; git commit -m `"$tag`"; git push"
Write-Host "  2) Create GitHub release tag $tag and attach app-release.apk"
