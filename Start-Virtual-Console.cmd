@echo off
setlocal
pushd "%~dp0"
python scripts\virtual_console.py status
if errorlevel 1 (popd & exit /b 1)
start "" "http://127.0.0.1:8088/"
popd
