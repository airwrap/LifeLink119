param([int]$Port=5584,[switch]$Restart)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$sdk=if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path (Split-Path -Parent $root) '.tools\android-sdk' }
$adb=Join-Path $sdk 'platform-tools\adb.exe'
if (!(Test-Path -LiteralPath $adb)) { throw 'Set ANDROID_HOME to the installed Android SDK.' }
$apk=Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (!(Test-Path -LiteralPath $apk)) { throw 'Run Build-And-Test.cmd before starting the voice demo.' }
& (Join-Path $PSScriptRoot 'start-voice-demo.ps1') -Port $Port -Restart:$Restart
& (Join-Path $PSScriptRoot 'local-demo.ps1') -Action start
$serial="emulator-$Port"
$ready=$false
for ($i=0;$i -lt 90;$i++) {
    $boot=(& $adb -s $serial shell getprop sys.boot_completed 2>$null)
    if ([string]$boot -eq '1') { $ready=$true; break }
    Start-Sleep -Seconds 1
}
if (!$ready) { throw 'QA emulator boot timed out; existing data was preserved.' }
$name=(& $adb -s $serial emu avd name | Select-Object -First 1).Trim()
if ($name -ne 'LifeLink119_QA_API_34') { throw 'Unexpected AVD; no APK was installed.' }
function Invoke-DemoAdb([string[]]$Arguments) {
    & $adb -s $serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Demo device command failed.' }
}
Invoke-DemoAdb @('reverse','tcp:8781','tcp:8781')
Invoke-DemoAdb @('install','-r',$apk)
Invoke-DemoAdb @('shell','am','start','-n','com.lifelink119.app/.MainActivity')
Write-Host 'Voice demo ready. In the app, choose More > automatic voice demonstration.'
