@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-voice-demo.ps1" %*
exit /b %ERRORLEVEL%
