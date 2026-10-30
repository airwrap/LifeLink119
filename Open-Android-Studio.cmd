@echo off
setlocal
if not defined ANDROID_STUDIO_HOME set "ANDROID_STUDIO_HOME=%ProgramFiles%\Android\Android Studio"
if not exist "%ANDROID_STUDIO_HOME%\bin\studio64.exe" (
  echo Set ANDROID_STUDIO_HOME to your Android Studio installation folder.
  exit /b 1
)
rem Android Studio uses its bundled runtime. Select Gradle JDK 17 in IDE settings.
start "" "%ANDROID_STUDIO_HOME%\bin\studio64.exe" "%~dp0."
