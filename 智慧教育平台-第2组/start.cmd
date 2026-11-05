@echo off
setlocal
cd /d "%~dp0"

set PORT=8080
if not "%~1"=="" set PORT=%~1
if /i "%~1"=="rebuild" set PORT=8080

echo ============================================================
echo  Zhijiao Education Platform - backend launcher
echo  project: %CD%
echo  port   : %PORT%
echo ============================================================
echo.

if /i "%~1"=="rebuild" goto build
if not exist "%TEMP%\zhijiao-build\classpath.txt" goto build
goto run

:build
echo [1/2] compiling backend (about 20-30s) ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-backend.ps1"
if errorlevel 1 (
  echo.
  echo BUILD FAILED - see messages above.
  pause
  exit /b 1
)
echo.

:run
echo [2/2] starting backend ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\run-backend.ps1" -Port %PORT%
echo.
echo backend stopped.
pause
