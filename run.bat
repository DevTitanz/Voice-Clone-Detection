@echo off
title VoxShield AI - Deepfake Defense
echo ===================================================
echo   Starting VoxShield AI (Backend + Frontend)
echo ===================================================
echo.

:: Launch Backend in a separate window
start "VoxShield AI - Backend API" cmd /k "cd /d "%~dp0backend" && venv\Scripts\uvicorn.exe app.main:app --reload --host 0.0.0.0 --port 8000"

:: Launch Frontend in a separate window
start "VoxShield AI - Web Dashboard" cmd /k "cd /d "%~dp0frontend" && npm run dev"

echo [OK] Backend starting on:  http://localhost:8000
echo [OK] Swagger API Docs:     http://localhost:8000/docs
echo [OK] Frontend Dashboard:   http://localhost:5173
echo.
echo Press any key to close this launcher (servers will keep running).
pause >nul
