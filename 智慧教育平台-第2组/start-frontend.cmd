@echo off
setlocal
cd /d "%~dp0frontend"
where node >nul 2>nul
if errorlevel 1 (
  echo Node.js is required. Install Node.js 20.19+ or 22.12+ first.
  exit /b 1
)
if not exist node_modules\vite\bin\vite.js (
  call npm ci
  if errorlevel 1 exit /b 1
)
if /i "%~1"=="build" goto build
echo Vue app: http://127.0.0.1:5173/app/
echo Backend proxy: http://127.0.0.1:8080
call npm run dev
exit /b %errorlevel%
:build
call npm run build
exit /b %errorlevel%
