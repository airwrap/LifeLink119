@echo off
setlocal
if "%~1"=="" (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-full-demo.ps1" -OpenBrowser
) else (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-full-demo.ps1" -Serial "%~1" -OpenBrowser
)
exit /b %ERRORLEVEL%
