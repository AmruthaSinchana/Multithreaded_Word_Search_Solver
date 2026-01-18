# Quick Start Guide

## Running the Application

### Step 1: Compile
```bash
javac *.java
```

### Step 2: Start Server
```bash
java Main
```

You should see:
```
Server started on http://localhost:8080
Open index.html in your browser
```

### Step 3: Open Frontend
Open `index.html` in your web browser.

**Note**: If you encounter CORS issues, you can:
- Use a simple HTTP server: `python -m http.server 3000` (then open `http://localhost:3000/index.html`)
- Or open the file directly (some browsers allow this for local files)

## How It Works

### 1. Select a Level
- Click on any level button
- The grid and word list will load from the backend

### 2. Choose Solver Type
- **Single-Threaded**: Baseline sequential search
- **Multithreaded**: Parallel search using ExecutorService

### 3. Start Solving
- Click "Start Solving"
- Watch real-time progress updates
- Words are highlighted as they are found
- Results appear when complete

### 4. View Results
- See which words were found
- Compare execution times
- Performance metrics displayed

## Architecture Flow

```
Frontend (index.html)
    ↓ (fetch /levels)
Backend (LevelRepository)
    ↓ (returns level list)
Frontend (displays levels)
    ↓ (user selects level, fetch /level?id=1)
Backend (returns level data)
    ↓ (user clicks "Start Solving", POST /startSolve)
Backend (SolverService starts background thread)
    ↓ (frontend polls /progress every 100ms)
Backend (returns progress updates)
    ↓ (when complete, fetch /results)
Backend (returns final results with timing)
Frontend (displays results)
```

## Key Points for Viva

1. **Backend owns all data**: Levels are stored in `LevelRepository`, never in frontend
2. **Non-blocking execution**: Solver runs in background threads
3. **Real-time updates**: Polling mechanism provides live progress
4. **Work partitioning**: Words are distributed across threads
5. **Synchronization**: ConcurrentHashMap ensures thread-safe results
6. **Performance measurement**: Nanosecond precision timing

## Troubleshooting

**Server won't start?**
- Check if port 8080 is already in use
- Make sure all Java files compiled successfully

**Frontend can't connect?**
- Verify server is running
- Check browser console for errors
- Ensure CORS headers are working (included in server code)

**No progress updates?**
- Check browser console for polling errors
- Verify solver is actually running (check server console)


