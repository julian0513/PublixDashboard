# Quick Start - Local Testing

## 🚀 Start All Services (4 Terminals)

### Terminal 1: Database
```powershell
cd "C:\Coding\Publix AI Dashboard"
docker compose up -d db
```

### Terminal 2: ML Service
```powershell
cd "C:\Coding\Publix AI Dashboard\ml_service\app"
uvicorn main:app --reload --port 8000
```

### Terminal 3: Backend
```powershell
cd "C:\Coding\Publix AI Dashboard\backend"
mvn spring-boot:run
```

### Terminal 4: Frontend
```powershell
cd "C:\Coding\Publix AI Dashboard\frontend"
npm run dev
```

## ✅ Verify Services

- **ML Service:** http://localhost:8000/health
- **Backend:** http://localhost:8080/api/sales?date=2025-10-01
- **Frontend:** http://localhost:5173

## 🧪 Quick Test

Run the automated test script:
```powershell
powershell -ExecutionPolicy Bypass -File test-local.ps1
```

## 📊 Generate Test Data

**Option 1: Via Browser Console**
```javascript
fetch('http://localhost:8080/api/historical/generate', {method: 'POST'})
  .then(r => console.log('Done!'))
```

**Option 2: Via PowerShell**
```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/historical/generate" -Method POST
```

## 🎯 Test New Features

1. **Open Frontend:** http://localhost:5173
2. **Click any product** in forecast table
3. **Test Basket Analysis tab** - Should show frequently bought together items
4. **Test Discount Insights tab** - Should show optimal discount recommendations
5. **Generate Sample Data** - Click button in Basket Analysis tab

## 📝 Full Testing Guide

See `LOCAL_TESTING_GUIDE.md` for comprehensive testing instructions.

