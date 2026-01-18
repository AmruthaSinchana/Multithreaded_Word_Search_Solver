# 🧩 Real-Time Multithreaded Word Search Solver (Java)

A college-level **client–server Word Search Puzzle Solver** built using **Plain Java (no frameworks)** and **Vanilla JavaScript**.
The project demonstrates **multithreading, parallel execution, synchronization, and real-time progress updates** while solving word search grids using **DFS**.

---

## 🚀 Highlights

- ✅ **Single-threaded solver** (baseline)
- ✅ **Multithreaded solver** using `ExecutorService` (thread pool)
- ✅ **DFS-based search** to locate words inside a character grid
- ✅ **Thread-safe result storage** using `ConcurrentHashMap`
- ✅ **Real-time progress updates** via frontend polling
- ✅ **Backend owns all levels** (frontend only requests + renders)

---

## 🏗️ Architecture

### Backend (Plain Java)
Handles:
- HTTP requests (custom lightweight server)
- Level storage (in-memory)
- Solving sessions + progress tracking
- Single-thread and parallel solving

Key files:
- `SimpleHTTPServer.java`
- `LevelRepository.java`
- `WordSearchSolver.java`
- `ParallelWordSearchSolver.java`
- `SolverService.java`

### Frontend (HTML/CSS/JS)
- Fetches puzzle levels from backend
- Starts solver (single / multithreaded)
- Polls progress and renders results

Files:
- `index.html`, `style.css`, `app.js`


## ⚙️ How Multithreading Works

- Words are treated as **independent tasks**
- Tasks are submitted to a **fixed thread pool**
- Each thread searches for assigned word(s) in parallel
- Results are safely stored using thread-safe collections

This improves total solving time compared to sequential execution.

---

## ▶️ How to Run

### 1) Compile Java Files
```bash
javac *.java
````

### 2) Start Server

```bash
java Main
```

Server runs at:
`http://localhost:8080`

### 3) Open Frontend

Open `index.html` directly in a browser
(or use a simple local server if needed)

> Note: Backend includes CORS headers for local testing.

---


