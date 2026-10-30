param(
    [Parameter(Mandatory = $true)]
    [string]$Target
)

$ErrorActionPreference = 'Stop'
$src = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not (Test-Path $Target)) { throw "대상 Android Studio 프로젝트 폴더가 없습니다: $Target" }
$targetAbs = (Resolve-Path $Target).Path
if ($src -eq $targetAbs) { throw 'ZIP 원본 폴더가 아닌, Android Studio에서 만든 별도 프로젝트 폴더를 지정하세요.' }
if (-not (Test-Path (Join-Path $targetAbs 'settings.gradle.kts'))) { throw '대상 폴더에 settings.gradle.kts가 없습니다. Android Studio Empty Activity 프로젝트를 먼저 생성하세요.' }
if (-not (Test-Path (Join-Path $targetAbs 'gradle/wrapper/gradle-wrapper.jar'))) { throw '대상 폴더에 Gradle wrapper JAR가 없습니다. Android Studio가 생성한 wrapper를 보존해 주세요.' }

$backup = Join-Path $targetAbs ('.lifelink-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $backup | Out-Null
$files = Get-ChildItem -Path $src -File -Recurse -Force | Where-Object { $_.FullName -notmatch '[\\/](\.git|\.gradle|build)[\\/]' }
foreach ($file in $files) {
    $relative = $file.FullName.Substring($src.Length).TrimStart([char]'\', [char]'/')
    $destination = Join-Path $targetAbs $relative
    $parent = Split-Path $destination -Parent
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    if (Test-Path $destination) {
        $saved = Join-Path $backup $relative
        New-Item -ItemType Directory -Force -Path (Split-Path $saved -Parent) | Out-Null
        Copy-Item $destination $saved -Force
    }
    Copy-Item $file.FullName $destination -Force
}
Write-Host "LifeLink119 소스 설치 완료: $targetAbs" -ForegroundColor Green
Write-Host "기존 파일 백업: $backup" -ForegroundColor Yellow
Write-Host 'Android Studio -> Gradle JDK 17 -> Sync -> Pleos Connect Emulator -> Run' -ForegroundColor Cyan
