@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\local-demo.ps1" -Action stop
exit /b %ERRORLEVEL%
