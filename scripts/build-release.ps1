param(
    [string]$SigningDirectory = '',
    [switch]$InitializeSigning
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$packageRoot = Split-Path -Parent $projectRoot
$localToolchain = Join-Path $packageRoot '.tools\lifelink-env.ps1'
if (Test-Path -LiteralPath $localToolchain) { . $localToolchain }
if (-not $env:JAVA_HOME -or -not $env:ANDROID_HOME) {
    throw 'Set JAVA_HOME (JDK 17) and ANDROID_HOME (API 34 / Build-Tools 35.0.0).'
}
if (-not $SigningDirectory) { $SigningDirectory = Join-Path $packageRoot '.tools\release-signing' }
$SigningDirectory = [System.IO.Path]::GetFullPath($SigningDirectory)
if ($SigningDirectory.TrimEnd('\') -eq $projectRoot.TrimEnd('\') -or $SigningDirectory.StartsWith($projectRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Keep release signing material outside the Git working tree.'
}
$configPath = Join-Path $SigningDirectory 'signing.json'
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
$apksigner = Join-Path $env:ANDROID_HOME 'build-tools\35.0.0\apksigner.bat'
$aapt = Join-Path $env:ANDROID_HOME 'build-tools\35.0.0\aapt.exe'
$envNames = @('LIFELINK_RELEASE_KEYSTORE','LIFELINK_RELEASE_STORE_PASSWORD','LIFELINK_RELEASE_KEY_ALIAS','LIFELINK_RELEASE_KEY_PASSWORD')
$previous = @{}
foreach ($name in $envNames) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

function Invoke-Checked {
    param([string]$Program, [string[]]$Arguments)
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed (exit $LASTEXITCODE)." }
}
function Unprotect-Password([string]$Protected) {
    $secure = ConvertTo-SecureString $Protected
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

try {
    if (-not (Test-Path -LiteralPath $configPath)) {
        if (-not $InitializeSigning) { throw 'No release key configured. First build: Build-Release.cmd -InitializeSigning' }
        New-Item -ItemType Directory -Path $SigningDirectory -Force | Out-Null
        $currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        Invoke-Checked 'icacls.exe' @($SigningDirectory, '/inheritance:r', '/grant:r', ('*' + $currentSid + ':(OI)(CI)F'), '*S-1-5-18:(OI)(CI)F')
        $keyPath = Join-Path $SigningDirectory 'lifelink119-release.p12'
        if (Test-Path -LiteralPath $keyPath) { throw 'An existing key has no configuration. Preserve it and restore signing.json.' }
        $randomBytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
        $password = [Convert]::ToBase64String($randomBytes)
        $protected = ConvertFrom-SecureString (ConvertTo-SecureString $password -AsPlainText -Force)
        $config = [ordered]@{ keyStore=$keyPath; keyAlias='lifelink119-release'; passwordDpapi=$protected }
        $config | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding UTF8
        $env:LIFELINK_RELEASE_STORE_PASSWORD = $password
        Invoke-Checked $keytool @('-genkeypair','-noprompt','-keystore',$keyPath,'-storetype','PKCS12','-storepass:env','LIFELINK_RELEASE_STORE_PASSWORD','-keypass:env','LIFELINK_RELEASE_STORE_PASSWORD','-alias','lifelink119-release','-keyalg','RSA','-keysize','3072','-sigalg','SHA256withRSA','-validity','10000','-dname','CN=LifeLink119 Release, OU=Contest, O=LifeLink119, C=KR')
        $password = $null
    }
    $config = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not (Test-Path -LiteralPath $config.keyStore)) { throw 'Configured release keystore is missing.' }
    $env:LIFELINK_RELEASE_KEYSTORE = $config.keyStore
    $env:LIFELINK_RELEASE_KEY_ALIAS = $config.keyAlias
    $env:LIFELINK_RELEASE_STORE_PASSWORD = Unprotect-Password $config.passwordDpapi
    $env:LIFELINK_RELEASE_KEY_PASSWORD = $env:LIFELINK_RELEASE_STORE_PASSWORD
    $voiceModel = Join-Path $packageRoot '.tools\speech\assets\speech\vosk-model-small-ko-0.22\am\final.mdl'
    $voicePrompts = Join-Path $packageRoot '.tools\speech\assets\voice'
    if (-not (Test-Path -LiteralPath $voiceModel)) {
        throw 'Prepare the bundled Korean speech model with: python scripts/prepare-offline-speech.py'
    }
    if (-not (Get-ChildItem -LiteralPath $voicePrompts -Filter '*.wav' -ErrorAction SilentlyContinue | Select-Object -First 1)) {
        throw 'Bundled Korean voice prompts are missing. Run scripts/prepare-offline-speech.py on Windows with Korean TTS.'
    }
    Push-Location $projectRoot
    try {
        Invoke-Checked (Join-Path $projectRoot 'gradlew.bat') @('spotlessCheck',':app:assembleDebug',':app:testDebugUnitTest',':app:assembleRelease',':app:testReleaseUnitTest',':app:lintRelease','-PlifelinkSubmission=true','--no-daemon','--console=plain')
    } finally { Pop-Location }
    $apk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path -LiteralPath $apk)) { throw 'Signed release APK was not produced.' }
    $sign = & $apksigner verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0 -or ($sign -join "`n") -match 'CN=Android Debug') { throw 'Release signature verification failed.' }
    $badging = & $aapt dump badging $apk
    if ($LASTEXITCODE -ne 0 -or ($badging -join "`n") -match 'application-debuggable') { throw 'APK metadata verification failed or APK is debuggable.' }
    $version = [regex]::Match(($badging -join "`n"), "versionName='([^']+)'").Groups[1].Value
    $artifactDir = Join-Path $projectRoot 'artifacts\submission'
    New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null
    $target = Join-Path $artifactDir ("LifeLink119-v" + $version + '-contest-release.apk')
    Copy-Item -LiteralPath $apk -Destination $target
    $digest = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText(($target + '.sha256'), ($digest + '  ' + [System.IO.Path]::GetFileName($target) + "`n"), [System.Text.Encoding]::ASCII)
    $sign | Set-Content -LiteralPath (Join-Path $artifactDir 'release-signature.txt') -Encoding UTF8
    $badging | Set-Content -LiteralPath (Join-Path $artifactDir 'release-metadata.txt') -Encoding UTF8
    $submissionDir = Join-Path $projectRoot 'apk'
    New-Item -ItemType Directory -Path $submissionDir -Force | Out-Null
    $submissionApk = Join-Path $submissionDir 'LifeLink119_LifeLink119_release.apk'
    Copy-Item -LiteralPath $target -Destination $submissionApk
    [System.IO.File]::WriteAllText(($submissionApk + '.sha256'), ($digest + '  ' + [System.IO.Path]::GetFileName($submissionApk) + "`n"), [System.Text.Encoding]::ASCII)
    Write-Host "Signed non-debuggable release APK: $target"
    Write-Host "Contest repository path (Git LFS): $submissionApk"
    Write-Host "SHA-256: $digest"
} finally {
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
