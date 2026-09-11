Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "  Starting VoxShield AI (Backend + Frontend)" -ForegroundColor Green
Write-Host "===================================================" -ForegroundColor Cyan

$root = $PSScriptRoot

# Start Backend
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\backend'; .\venv\Scripts\uvicorn.exe app.main:app --reload --host 0.0.0.0 --port 8000"

# Start Frontend
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\frontend'; npm run dev"

Write-Host "`n[OK] Backend starting at:  http://localhost:8000" -ForegroundColor Yellow
Write-Host "[OK] Swagger API Docs at: http://localhost:8000/docs" -ForegroundColor Yellow
Write-Host "[OK] Frontend Dashboard:  http://localhost:5173`n" -ForegroundColor Green
