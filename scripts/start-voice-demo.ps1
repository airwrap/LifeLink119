param([int]$Port=5584,[switch]$Restart)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$tools=Join-Path (Split-Path -Parent $root) '.tools'
$sdk=if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $tools 'android-sdk' }
$adb=Join-Path $sdk 'platform-tools\adb.exe'
$avd='LifeLink119_QA_API_34'
$serial="emulator-$Port"
$devices=& $adb devices
if ($devices -match "^$serial\s+device") {
    $name=(& $adb -s $serial emu avd name | Select-Object -First 1).Trim()
    if ($name -ne $avd) { throw 'Voice launcher only operates the dedicated QA AVD; existing device preserved.' }
    if (!$Restart) { Write-Host 'QA emulator is already running. Use -Restart to apply voice audio settings.'; exit 0 }
    & $adb -s $serial emu kill | Out-Null
    for ($i=0;$i -lt 30;$i++) {
        if (!((& $adb devices) -match "^$serial\s")) { break }
        Start-Sleep -Milliseconds 500
    }
}
$capture=@(Get-ChildItem 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Capture' -ErrorAction SilentlyContinue |
    ForEach-Object { Get-ItemProperty $_.PSPath } | Where-Object { ($_.DeviceState -band 1) -eq 1 })
$previousIn=$env:QEMU_AUDIO_IN_DRV
$previousOut=$env:QEMU_AUDIO_OUT_DRV
$avdConfig=Join-Path $env:USERPROFILE ".android\avd\$avd.avd\config.ini"
$config=Get-Content -LiteralPath $avdConfig -Encoding UTF8
$config=@($config | Where-Object { $_ -notmatch '^hw\.audio(Input|Output)\s*=' })
$config += ('hw.audioInput=' + $(if ($capture.Count -gt 0) {'yes'} else {'no'}))
$config += 'hw.audioOutput=yes'
$config | Set-Content -LiteralPath $avdConfig -Encoding UTF8
try {
    $env:QEMU_AUDIO_OUT_DRV='dsound'
    $env:QEMU_AUDIO_IN_DRV=if ($capture.Count -gt 0) {'dsound'} else {'none'}
    $log=Join-Path $tools 'logs'
    New-Item -ItemType Directory -Force -Path $log | Out-Null
    $arguments=@('-avd',$avd,'-port',"$Port",'-gpu','host','-feature','-Vulkan','-no-snapshot','-no-metrics','-crash-report-mode','disabled','-no-boot-anim')
    if ($capture.Count -gt 0) { $arguments += '-allow-host-audio' }
    Start-Process -FilePath (Join-Path $sdk 'emulator\emulator.exe') -ArgumentList $arguments -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $log 'voice-emulator.log') -RedirectStandardError (Join-Path $log 'voice-emulator-error.log') | Out-Null
} finally {
    $env:QEMU_AUDIO_IN_DRV=$previousIn
    $env:QEMU_AUDIO_OUT_DRV=$previousOut
}
Write-Host "Voice QA emulator starting: $serial. Host microphone available: $($capture.Count -gt 0)"
if ($capture.Count -eq 0) { Write-Host 'Speech output enabled; connect a Windows microphone and restart this launcher for spoken input.' }
Write-Host 'After boot: adb -s emulator-5584 reverse tcp:8781 tcp:8781'
