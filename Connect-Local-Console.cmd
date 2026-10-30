@echo off
setlocal
if not defined ANDROID_HOME (
  if defined ANDROID_SDK_ROOT (set "ANDROID_HOME=%ANDROID_SDK_ROOT%") else (set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk")
)
if not exist "%ANDROID_HOME%\platform-tools\adb.exe" (
  echo Set ANDROID_HOME to your Android SDK folder.
  exit /b 1
)
if "%~1"=="" (
  "%ANDROID_HOME%\platform-tools\adb.exe" reverse tcp:8781 tcp:8781
) else (
  "%ANDROID_HOME%\platform-tools\adb.exe" -s "%~1" reverse tcp:8781 tcp:8781
)
if errorlevel 1 (
  echo If multiple devices are connected, run Connect-Local-Console.cmd emulator-5580
  exit /b 1
)
echo Local demo tunnel ready: Android 127.0.0.1:8781 to this PC 127.0.0.1:8781.
