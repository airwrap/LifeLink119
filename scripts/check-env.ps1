param([switch]$WebOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$problems = @()
function Test-RequiredPath([string]$Label, [string]$Path) {
    if (Test-Path -LiteralPath $Path) { Write-Host "OK   $Label" }
    else { Write-Host "FAIL $Label - $Path"; $script:problems += $Label }
}
Write-Host 'LifeLink119 environment check (no installation or app-data changes)'
try {
    $pythonProbe = @'
import sys
from importlib.metadata import version
print("Python", sys.version.split()[0], "jsonschema", version("jsonschema"))
sys.exit(0 if sys.version_info >= (3, 10) else 1)
'@
    $pythonProbe | & python -
    if ($LASTEXITCODE -ne 0) { throw 'Python 3.10+ and console/requirements.txt are required.' }
} catch { Write-Host "FAIL Python: $_"; $problems += 'Python dependencies' }
if (!$WebOnly) {
    $javaRoot = $env:JAVA_HOME
    if (!$javaRoot) {
        $legacyJdk = Join-Path $projectRoot '..\.tools\java\jdk-17.0.20.1+1'
        if (Test-Path -LiteralPath (Join-Path $legacyJdk 'bin\javac.exe')) { $javaRoot = $legacyJdk }
    }
    if (!$javaRoot) { Write-Host 'FAIL JAVA_HOME: set it to JDK 17'; $problems += 'JAVA_HOME' }
    else {
        Test-RequiredPath 'JDK compiler' (Join-Path $javaRoot 'bin\javac.exe')
        $releaseFile = Join-Path $javaRoot 'release'
        if ((Test-Path -LiteralPath $releaseFile) -and ((Get-Content -LiteralPath $releaseFile -Raw) -match '(?m)^JAVA_VERSION="17[.\-"]')) {
            Write-Host "OK   JDK 17: $javaRoot"
        } else { Write-Host 'FAIL Use JDK 17 for the project baseline'; $problems += 'JDK version' }
    }
    $sdkRoot = $env:ANDROID_HOME
    if (!$sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
    if (!$sdkRoot) { $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    Write-Host "SDK: $sdkRoot"
    Test-RequiredPath 'Android SDK API 34' (Join-Path $sdkRoot 'platforms\android-34\android.jar')
    Test-RequiredPath 'Build Tools 35.0.0' (Join-Path $sdkRoot 'build-tools\35.0.0\aapt2.exe')
    Test-RequiredPath 'ADB' (Join-Path $sdkRoot 'platform-tools\adb.exe')
    $emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
    Test-RequiredPath 'Android Emulator' $emulator
    if (Test-Path -LiteralPath $emulator) {
        $avds = @(& $emulator -list-avds)
        if ($LASTEXITCODE -ne 0) { $problems += 'AVD inventory' }
        elseif ($avds -contains 'Pleos_Connect_API_34') { Write-Host 'OK   Pleos_Connect_API_34 AVD name (verify its official image in Device Manager)' }
        else { Write-Host 'FAIL Create official Pleos API 34 AVD named Pleos_Connect_API_34'; $problems += 'Pleos AVD' }
    }
    Test-RequiredPath 'Gradle wrapper' (Join-Path $projectRoot 'gradle\wrapper\gradle-wrapper.jar')
}
if ($problems.Count) { Write-Host "Missing/invalid checks: $($problems -join ', ')"; exit 1 }
Write-Host 'Environment checks passed. Build and runtime verification are separate steps.'
exit 0
