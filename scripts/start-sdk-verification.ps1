param(
    [string]$SdkRoot=$env:ANDROID_HOME,
    [string]$JavaHome=$env:JAVA_HOME,
    [int]$Port=5582,
    [switch]$SkipBuild,
    [switch]$Ui,
    [switch]$CheckOnly
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $repo
if(!$SdkRoot){$SdkRoot=Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
if(!$JavaHome){
    $localJava=Join-Path (Split-Path $repo) '.tools\java'
    if(Test-Path -LiteralPath $localJava){$JavaHome=(Get-ChildItem -LiteralPath $localJava -Directory | Where-Object Name -Like 'jdk-17*' | Select-Object -First 1).FullName}
}
if(!(Test-Path -LiteralPath (Join-Path $SdkRoot 'emulator\emulator.exe'))){throw 'Android SDK Emulator is required. See docs/OTHER_PC_GUIDE.md.'}
if(!$JavaHome -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))){throw 'Set JAVA_HOME or -JavaHome to JDK 17. The probe must be signed with the same debug key as the app.'}
$javaRelease=Join-Path $JavaHome 'release'
if(!(Test-Path -LiteralPath $javaRelease) -or !((Get-Content -LiteralPath $javaRelease) -match '^JAVA_VERSION="17\.')){throw 'This project verifies JDK 17. Set JAVA_HOME or -JavaHome to JDK 17.'}
$image=Join-Path $SdkRoot 'system-images\android-34\pleos_car_emulator\x86_64\package.xml'
if(!(Test-Path -LiteralPath $image)){throw 'Install the official Pleos Connect 2.1.0/API34 image first. No real vehicle or localhost backend is needed.'}
$python=(Get-Command python -ErrorAction Stop).Source
$env:JAVA_HOME=$JavaHome;$env:ANDROID_HOME=$SdkRoot;$env:ANDROID_SDK_ROOT=$SdkRoot;$env:PYTHONIOENCODING='utf-8'
$adb=Join-Path $SdkRoot 'platform-tools\adb.exe'
$avd='LifeLink119_SDK_Compat_20261107';$serial='emulator-'+$Port
if($Port -lt 5554 -or $Port -gt 5680 -or $Port%2 -ne 0){throw 'Use an even emulator console port in 5554..5680.'}
function Checked([string]$File,[string[]]$Arguments){& $File @Arguments;if($LASTEXITCODE -ne 0){throw "Command failed ($LASTEXITCODE): $File"}}
if($CheckOnly){
    Write-Output "Prerequisites found: SDK=$SdkRoot; JDK=$JavaHome; Python=$python; AVD=$avd; serial=$serial"
    & $adb devices -l
    exit 0
}
if(!$SkipBuild){Checked (Join-Path $repo 'gradlew.bat') @(':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug','--console=plain','--no-parallel','--max-workers=1')}
$apk=Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
if(!(Test-Path -LiteralPath $apk)){throw 'Build the current debug APK first.'}
Checked $python @('scripts/prepare-sdk-probe.py','--build-only')
$registry=Join-Path $env:USERPROFILE ".android\avd\$avd.ini"
if(!(Test-Path -LiteralPath $registry)){
    $newPath=Join-Path $env:LOCALAPPDATA 'LifeLink119SdkVerify\avd-sdk-demo'
    if(Test-Path -LiteralPath $newPath){throw 'An unregistered test AVD directory exists; preserved. Inspect it before creating another AVD.'}
    Checked (Join-Path $SdkRoot 'cmdline-tools\latest\bin\avdmanager.bat') @('create','avd','--name',$avd,'--path',$newPath,'--package','system-images;android-34;pleos_car_emulator;x86_64','--device','Pleos_Connect_v2_0')
    $config=Join-Path $newPath 'config.ini';$lines=Get-Content -LiteralPath $config -Encoding UTF8
    Copy-Item -LiteralPath $config -Destination (Join-Path $newPath 'config.original.ini')
    $updates=@{'hw.lcd.width'='1280';'hw.lcd.height'='720';'hw.lcd.density'='160';'hw.lcd.vsync'='30';'hw.ramSize'='4096';'hw.gpu.enabled'='yes';'hw.gpu.mode'='swiftshader';'hw.audioInput'='yes';'hw.audioOutput'='yes';'fastboot.forceColdBoot'='yes';'fastboot.forceFastBoot'='no';'firstboot.bootFromDownloadableSnapshot'='no';'firstboot.bootFromLocalSnapshot'='no';'firstboot.saveToLocalSnapshot'='no'}
    $seen=@{};$updated=foreach($line in $lines){$key=($line -split '=',2)[0];$seen[$key]=$true;if($updates.ContainsKey($key)){"$key=$($updates[$key])"}else{$line}}
    foreach($key in $updates.Keys){if(!$seen.ContainsKey($key)){$updated+="$key=$($updates[$key])"}}
    [IO.File]::WriteAllLines($config,[string[]]$updated,[Text.UTF8Encoding]::new($false))
}
$devices=(& $adb devices) -join "`n"
if($devices -match "(?m)^$serial\s"){
    $actual=(& $adb -s $serial emu avd name | Select-Object -First 1).Trim()
    if($actual -ne $avd){throw 'Selected port belongs to another AVD; preserved.'}
}else{
    $logDir=Join-Path $repo 'verification\pleos-sdk-v16-runtime';New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    # Keep the GUI engine: -no-window was unreliable for this validation environment.
    Start-Process -FilePath (Join-Path $SdkRoot 'emulator\emulator.exe') -ArgumentList @('-avd',$avd,'-port',"$Port",'-no-snapshot','-gpu','swiftshader','-feature','-Vulkan','-memory','4096','-cores','4') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'launcher-emulator.log') -RedirectStandardError (Join-Path $logDir 'launcher-emulator-error.log') | Out-Null
}
Write-Output 'Waiting for cold boot. Existing AVDs, app data and services are preserved.'
$deadline=(Get-Date).AddMinutes(8);$boot=''
while((Get-Date) -lt $deadline){
    $priorPreference=$ErrorActionPreference;$ErrorActionPreference='Continue'
    $boot=(& $adb -s $serial shell getprop sys.boot_completed 2>$null) -join ''
    $ErrorActionPreference=$priorPreference
    if($boot.Trim() -eq '1'){break}
    Start-Sleep -Seconds 5
}
if($boot.Trim() -ne '1'){throw 'Pleos cold boot timed out. Do not treat a standard Android fixture as SDK proof.'}
$androidUser=(& $adb -s $serial shell am get-current-user).Trim()
if($androidUser -notmatch '^\d+$'){throw 'Android foreground user could not be identified.'}
Checked $python @('scripts/install-sdk-test-apk.py','--serial',$serial,'--apk',$apk)
Checked $python @('scripts/install-sdk-test-apk.py','--serial',$serial,'--apk',(Join-Path $repo 'verification\pleos-sdk-v16-runtime\probe\sdk-probe.apk'))
Checked $adb @('-s',$serial,'shell','cmd','package','compile','-m','speed','-f','com.lifelink119.app')
Checked $python @('scripts/verify-pleos-sdk-values.py','--serial',$serial,'--avd',$avd,'--port','18088')
if($Ui){
    Checked $python @('scripts/prepare-native-window.py','--serial',$serial)
    Checked $python @('scripts/verify-pleos-sdk-ui.py','--serial',$serial)
}
Write-Output 'SDK verification finished. See verification/pleos-sdk-current/results.json.'
