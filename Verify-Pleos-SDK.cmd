@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-sdk-verification.ps1" %*
set "resultCode=%ERRORLEVEL%"
if not "%resultCode%"=="0" pause
exit /b %resultCode%
