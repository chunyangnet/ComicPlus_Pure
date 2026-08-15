[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$androidRoot = Join-Path $projectRoot "android"
$bootstrapFile = Join-Path $androidRoot "keystore.properties"
$expectedSignerFile = Join-Path $androidRoot "release-signing.sha256"
$signingRoot = [IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA "ComicPlus\signing"))
$keystoreFile = Join-Path $signingRoot "comic-plus-release.p12"
$propertiesFile = Join-Path $signingRoot "comic-plus-signing.properties"

if (Test-Path -LiteralPath $expectedSignerFile) {
    throw "This project already has a fixed release identity. Restore the signing backup instead of generating a new key."
}
if ((Test-Path -LiteralPath $keystoreFile) -or (Test-Path -LiteralPath $propertiesFile)) {
    throw "Release signing already exists at $signingRoot. Refusing to replace the app identity."
}

$keytool = Get-Command keytool -ErrorAction Stop | Select-Object -ExpandProperty Source
$passwordBytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Fill($passwordBytes)
$password = [Convert]::ToBase64String($passwordBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$alias = "comic-plus-release"

New-Item -ItemType Directory -Path $signingRoot -Force | Out-Null
try {
    & $keytool `
        -genkeypair `
        -noprompt `
        -storetype PKCS12 `
        -keystore $keystoreFile `
        -storepass $password `
        -keypass $password `
        -alias $alias `
        -dname "CN=Comic Plus Release,OU=Android,O=Comic Plus,C=CN" `
        -keyalg RSA `
        -keysize 4096 `
        -sigalg SHA256withRSA `
        -validity 10000
    if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }

    $escapedStoreFile = $keystoreFile.Replace('\', '/')
    $properties = @(
        "storeFile=$escapedStoreFile",
        "storePassword=$password",
        "keyAlias=$alias",
        "keyPassword=$password"
    ) -join [Environment]::NewLine
    [IO.File]::WriteAllText($propertiesFile, $properties + [Environment]::NewLine, [Text.Encoding]::ASCII)

    $escapedPropertiesFile = $propertiesFile.Replace('\', '/')
    [IO.File]::WriteAllText(
        $bootstrapFile,
        "propertiesFile=$escapedPropertiesFile" + [Environment]::NewLine,
        [Text.Encoding]::ASCII
    )

    $keytoolOutput = (& $keytool -list -v -storetype PKCS12 -keystore $keystoreFile -storepass $password -alias $alias | Out-String)
    if ($LASTEXITCODE -ne 0) { throw "Could not read generated signing certificate" }
    $fingerprint = [regex]::Match($keytoolOutput, 'SHA256:\s*([0-9A-F:]+)').Groups[1].Value.Replace(':', '').ToLowerInvariant()
    if ($fingerprint -notmatch '^[0-9a-f]{64}$') { throw "Could not parse generated signing certificate fingerprint" }
    [IO.File]::WriteAllText($expectedSignerFile, $fingerprint + [Environment]::NewLine, [Text.Encoding]::ASCII)

    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $signingRoot /inheritance:r /grant:r "${identity}:(OI)(CI)F" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not restrict signing directory permissions" }

    [pscustomobject]@{
        Keystore = $keystoreFile
        Properties = $propertiesFile
        ProjectBootstrap = $bootstrapFile
        Alias = $alias
    } | Format-List
} catch {
    if (Test-Path -LiteralPath $keystoreFile) { Remove-Item -LiteralPath $keystoreFile -Force }
    if (Test-Path -LiteralPath $propertiesFile) { Remove-Item -LiteralPath $propertiesFile -Force }
    if (Test-Path -LiteralPath $bootstrapFile) { Remove-Item -LiteralPath $bootstrapFile -Force }
    if (Test-Path -LiteralPath $expectedSignerFile) { Remove-Item -LiteralPath $expectedSignerFile -Force }
    throw
} finally {
    [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
    $password = $null
}
