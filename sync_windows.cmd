@echo off
cd /d "%~dp0"
where py >nul 2>&1
if %errorlevel%==0 (py -3 tools\sync_github.py) else (python tools\sync_github.py)
pause
