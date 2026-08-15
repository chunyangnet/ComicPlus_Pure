[CmdletBinding()]
param(
    [switch]$SkipLint
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$androidRoot = Join-Path $projectRoot "android"
$outputRoot = Join-Path $projectRoot "output"
$gradle = Join-Path $androidRoot "gradlew.bat"
$expectedSignerFile = Join-Path $androidRoot "release-signing.sha256"

$sdkCandidates = @(@(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA "Android\Sdk" })
) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) })
if (-not $sdkCandidates) {
    throw "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}
$env:ANDROID_HOME = [IO.Path]::GetFullPath($sdkCandidates[0])

$tasks = @("--no-daemon", "clean", "assembleRelease")
if (-not $SkipLint) { $tasks += "lintVitalRelease" }
Push-Location $androidRoot
try {
    & $gradle @tasks
    if ($LASTEXITCODE -ne 0) { throw "Release build failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$resolvedOutputRoot = [IO.Path]::GetFullPath($outputRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
Get-ChildItem -LiteralPath $resolvedOutputRoot -File | Where-Object {
    $_.Name -like "ComicPlus_Pure-*-debug.apk*" -or
    $_.Name -like "ComicPlus_Pure-*-release*.apk*" -or
    $_.Name -like "Comic-Plus-*-release*.apk*" -or
    $_.Name -like "Comic-Plus-release-v*.apk*"
} | ForEach-Object {
    if (-not $_.FullName.StartsWith($resolvedOutputRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove file outside output directory: $($_.FullName)"
    }
    Remove-Item -LiteralPath $_.FullName -Force
}

$builtRoot = Join-Path $androidRoot "app\build\outputs\apk\release"
$sourceApk = Join-Path $builtRoot "app-release.apk"
if (-not (Test-Path -LiteralPath $sourceApk -PathType Leaf)) {
    throw "A signed release APK was not produced. Restore the Comic Plus signing backup."
}
$metadataFile = Join-Path $builtRoot "output-metadata.json"
if (-not (Test-Path -LiteralPath $metadataFile -PathType Leaf)) {
    throw "Android output metadata was not produced."
}
$outputMetadata = Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json
$versionName = [string]$outputMetadata.elements[0].versionName
if ($versionName -notmatch '^[0-9A-Za-z][0-9A-Za-z._-]{0,63}$') {
    throw "Invalid release version name in Android output metadata."
}

$buildTools = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME "build-tools") -Directory |
    Sort-Object { [version]($_.Name -replace '-.*$', '') } -Descending |
    Select-Object -First 1
$apkSigner = if ($buildTools) { Join-Path $buildTools.FullName "apksigner.bat" } else { $null }
if (-not $apkSigner -or -not (Test-Path -LiteralPath $apkSigner -PathType Leaf)) {
    throw "Android apksigner was not found."
}
if (-not (Test-Path -LiteralPath $expectedSignerFile -PathType Leaf)) {
    throw "Expected release signer fingerprint is missing: $expectedSignerFile"
}
$expectedSigner = (Get-Content -LiteralPath $expectedSignerFile -Raw).Trim().ToLowerInvariant()
if ($expectedSigner -notmatch '^[0-9a-f]{64}$') { throw "Invalid expected release signer fingerprint" }
$verification = (& $apkSigner verify --verbose --print-certs $sourceApk 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed" }
$actualSigner = [regex]::Match($verification, 'certificate SHA-256 digest:\s*([0-9a-fA-F]+)').Groups[1].Value.ToLowerInvariant()
if ($actualSigner -ne $expectedSigner) {
    throw "Unexpected release signer. Expected $expectedSigner but received $actualSigner"
}

$destination = Join-Path $resolvedOutputRoot "Comic-Plus-release-v$versionName.apk"
Copy-Item -LiteralPath $sourceApk -Destination $destination -Force
$hash = Get-FileHash -LiteralPath $destination -Algorithm SHA256
$hashPath = "$destination.sha256"
Set-Content -LiteralPath $hashPath -Value "$($hash.Hash.ToLowerInvariant())  $([IO.Path]::GetFileName($destination))" -Encoding ascii

[pscustomobject]@{
    Apk = $destination
    SizeBytes = (Get-Item -LiteralPath $destination).Length
    Sha256 = $hash.Hash
    Signed = $true
    SignerSha256 = $actualSigner
} | Format-List
