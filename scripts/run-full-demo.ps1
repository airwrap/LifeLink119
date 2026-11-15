param([string]$Serial='emulator-5580',[switch]$OpenBrowser,[ValidateSet('auto','host','swiftshader')][string]$Gpu='auto')
$ErrorActionPreference='Stop'
if($Serial -notmatch '^emulator-(\d+)$'){throw 'Use an emulator serial, for example emulator-5580.'}
$consolePort=[int]$Matches[1]
if($consolePort -lt 5554 -or $consolePort -gt 5682 -or $consolePort % 2 -ne 0){throw 'Use an even Android Emulator port between 5554 and 5682.'}
$projectRoot=Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'local-demo.ps1') -Action start -OpenBrowser:$OpenBrowser
$sdkPath=$env:ANDROID_HOME
if(!$sdkPath){$sdkPath=$env:ANDROID_SDK_ROOT}
if(!$sdkPath){$sdkPath=Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
$adbPath=Join-Path $sdkPath 'platform-tools\adb.exe'
$emulatorPath=Join-Path $sdkPath 'emulator\emulator.exe'
if(!(Test-Path -LiteralPath $adbPath) -or !(Test-Path -LiteralPath $emulatorPath)){throw 'The browser demo is running. For Android, install the SDK/Emulator and set ANDROID_HOME.'}
$oldPreference=$ErrorActionPreference
$ErrorActionPreference='Continue'
try {$knownDevices=& $adbPath devices 2>$null} finally {$ErrorActionPreference=$oldPreference}
if(!($knownDevices -match [regex]::Escape($Serial))) {
    $installedAvds=& $emulatorPath -list-avds
    if(!($installedAvds -contains 'Pleos_Connect_API_34')){throw 'Create the official Pleos API 34 AVD named Pleos_Connect_API_34 first. The browser demo remains available.'}
    $listener=Get-NetTCPConnection -LocalPort $consolePort,($consolePort+1) -State Listen -ErrorAction SilentlyContinue
    if($listener){throw 'The emulator port is in use by another program; it was preserved.'}
    $logs=Join-Path $projectRoot 'verification'
    New-Item -ItemType Directory -Path $logs -Force | Out-Null
    Start-Process -FilePath $emulatorPath -ArgumentList @('-avd','Pleos_Connect_API_34','-no-snapshot','-no-audio','-gpu',$Gpu,'-feature','-Vulkan','-memory','4096','-cores','4','-port',"$consolePort") -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'full-demo-emulator-out.log') -RedirectStandardError (Join-Path $logs 'full-demo-emulator-error.log') | Out-Null
}
Write-Host "Waiting for $Serial (up to 10 minutes on a cold boot)..."
& python (Join-Path $PSScriptRoot 'wait-emulator.py') --adb $adbPath --serial $Serial --seconds 600
if($LASTEXITCODE -ne 0){throw 'Emulator boot timeout. Check the emulator window and verification/full-demo-emulator-error.log.'}
$avdName=& $adbPath -s $Serial emu avd name
if(!($avdName -contains 'Pleos_Connect_API_34')){throw 'This serial is not the expected Pleos AVD. It was preserved.'}
& $adbPath -s $Serial reverse tcp:8781 tcp:8781
if($LASTEXITCODE -ne 0){throw 'Could not set up the localhost tunnel.'}
$apkPath=Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
if(!(Test-Path -LiteralPath $apkPath)){throw 'No debug APK found. Run Build-And-Test.cmd first.'}
& $adbPath -s $Serial install --no-streaming -r $apkPath
if($LASTEXITCODE -ne 0){throw 'APK installation failed. Wait for the Pleos user session to finish starting and retry.'}
& $adbPath -s $Serial shell am start -W --display 0 -f 0x10008000 -n com.lifelink119.app/.MainActivity
if($LASTEXITCODE -ne 0){throw 'App launch failed.'}
Write-Host 'Ready. Use the home actions or scenario gallery. Actual platform driving restrictions remain in effect.'
Write-Host 'Stop the Python server with Stop-Local-Demo.cmd; close the emulator window with X.'
