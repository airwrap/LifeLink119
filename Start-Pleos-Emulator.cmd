@echo off
setlocal
if not defined ANDROID_HOME (
  if defined ANDROID_SDK_ROOT (set "ANDROID_HOME=%ANDROID_SDK_ROOT%") else (set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk")
)
if not exist "%ANDROID_HOME%\emulator\emulator.exe" (
  echo Install Android Emulator and set ANDROID_HOME. See docs\OTHER_PC_GUIDE.md.
  exit /b 1
)
set "LIFELINK_AVD=Pleos_Connect_API_34"
if not "%~1"=="" set "LIFELINK_AVD=%~1"
"%ANDROID_HOME%\emulator\emulator.exe" -avd "%LIFELINK_AVD%" -no-snapshot -gpu host -feature -Vulkan
exit /b %ERRORLEVEL%
