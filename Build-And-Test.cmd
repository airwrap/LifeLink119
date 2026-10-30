@echo off
setlocal
rem Respect each developer's environment. Legacy local tools are optional fallbacks.
if not defined JAVA_HOME if exist "%~dp0..\.tools\java\jdk-17.0.20.1+1\bin\java.exe" set "JAVA_HOME=%~dp0..\.tools\java\jdk-17.0.20.1+1"
if not defined JAVA_HOME (
  echo Set JAVA_HOME to JDK 17. See docs\OTHER_PC_GUIDE.md.
  exit /b 1
)
if not exist "%JAVA_HOME%\bin\javac.exe" (
  echo JAVA_HOME must point to a JDK folder containing bin\javac.exe.
  exit /b 1
)
if not defined GRADLE_USER_HOME if exist "%~dp0..\.tools\gradle-cache" set "GRADLE_USER_HOME=%~dp0..\.tools\gradle-cache"
if not defined ANDROID_HOME (
  if defined ANDROID_SDK_ROOT (set "ANDROID_HOME=%ANDROID_SDK_ROOT%") else (set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk")
)
pushd "%~dp0"
call gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
set "LIFELINK_BUILD_EXIT=%ERRORLEVEL%"
popd
exit /b %LIFELINK_BUILD_EXIT%
