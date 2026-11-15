param([int]$Port=5584,[string]$SdkRoot=$env:ANDROID_HOME)
$ErrorActionPreference='Stop'
if($Port -lt 5554 -or $Port -gt 5680 -or $Port%2){throw 'Choose an even emulator port in 5554..5680.'}
$repo=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
if(!$SdkRoot){$SdkRoot=Join-Path (Split-Path $repo) '.tools\android-sdk'}
$adb=Join-Path $SdkRoot 'platform-tools\adb.exe'
$avd='LifeLink119_QA_API_34'
$serial="emulator-$Port"
$registry=Join-Path $env:USERPROFILE ".android\avd\$avd.ini"
if(!(Test-Path -LiteralPath $registry)){
    # A new image only. Never copy userdata or snapshots from a daily demo AVD.
    $previousPreference=$ErrorActionPreference
    $ErrorActionPreference='Continue'
    try { & (Join-Path $SdkRoot 'cmdline-tools\latest\bin\avdmanager.bat') create avd --name $avd --package 'system-images;android-34;pleos_car_emulator;x86_64' --device 'Pleos_Connect_v2_0' }
    finally { $ErrorActionPreference=$previousPreference }
    if($LASTEXITCODE){throw 'Could not create the dedicated QA AVD.'}
}
$devices=(& $adb devices) -join "`n"
if($devices -match "(?m)^$serial\s"){
    $actual=(& $adb -s $serial emu avd name | Select-Object -First 1).Trim()
    if($actual -ne $avd){throw 'Port belongs to another AVD; preserved.'}
}else{
    if(Get-NetTCPConnection -LocalPort $Port,($Port+1) -State Listen -ErrorAction SilentlyContinue){throw 'Emulator port is occupied; preserved.'}
    $logs=Join-Path $repo 'verification\qa';New-Item -ItemType Directory -Path $logs -Force | Out-Null
    Start-Process -FilePath (Join-Path $SdkRoot 'emulator\emulator.exe') -ArgumentList @('-avd',$avd,'-port',"$Port",'-no-snapshot','-no-audio','-gpu','auto','-feature','-Vulkan','-memory','4096','-cores','4') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'emulator.log') -RedirectStandardError (Join-Path $logs 'emulator-error.log') | Out-Null
}
& python (Join-Path $PSScriptRoot 'wait-emulator.py') --adb $adb --serial $serial --seconds 600
if($LASTEXITCODE){throw 'QA AVD boot timed out.'}
Write-Output "Dedicated QA AVD ready: $avd / $serial. Daily demo data was preserved."
