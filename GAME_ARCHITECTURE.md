# Multithreaded Word Search Game - Architecture Documentation

## Overview

This document explains the multithreaded architecture of the Word Search Game. All game operations (validation, hints, solving) run in parallel using ExecutorService, ensuring the UI thread is never blocked.

## Core Architecture Principles

### 1. Multithreading is MANDATORY
- **ALL** word search operations run in background threads
- UI thread only submits tasks and processes results
- No solving logic on UI thread

### 2. Immutable Data
- Grid and word lists are **read-only** across threads
- No shared mutable state during search operations
- Thread-safe result aggregation

### 3. Separation of Concerns
- **UI**: Rendering and user input only
- **Engine**: All validation, hint, and solving logic
- **Service**: Session management and HTTP handling

## Component Architecture

### PuzzleEngine.java
**CORE MULTITHREADED ENGINE**

- **Purpose**: Central engine for all word search operations
- **Threading**: Uses ExecutorService with configurable thread pool
- **Operations**:
  - `searchWordsParallel()`: Searches multiple words concurrently
  - `validatePath()`: Validates player path in parallel
- **Why Multithreading**: Each word search is independent, perfect for parallelization

**Key Design Decisions**:
- Grid is immutable (read-only) - no synchronization needed
- Each word search is a Callable task
- Results collected via Future objects
- Thread-safe result aggregation using ConcurrentHashMap

### ValidationEngine (within PuzzleEngine)
**MULTITHREADED PATH VALIDATION**

- **Purpose**: Validates player-selected paths
- **Strategy**: Runs parallel validation tasks against all words
- **Returns**: Result as soon as any validation succeeds
- **Why**: Fast response time, non-blocking UI

### HintManager.java
**MULTITHREADED HINT GENERATION**

- **Purpose**: Generates partial hints for remaining words
- **Strategy**: 
  - Identifies all remaining unfound words
  - Runs parallel search tasks to locate valid paths
  - Selects ONE word and provides partial clue only
- **Hint Types**:
  - Starting cell coordinates
  - Direction hint
  - One unrevealed letter
- **Never reveals**: Full word or complete path

**Why Multithreading**: 
- Searches all remaining words in parallel
- Fast hint delivery without blocking UI

### ScoreManager.java
**THREAD-SAFE SCORING**

- **Purpose**: Manages game scoring with thread safety
- **Synchronization**: 
  - AtomicInteger for score (lock-free)
  - ReentrantReadWriteLock for complex operations
- **Scoring Rules**:
  - Correct word: +10 points
  - Diagonal word: +15 points (bonus)
  - Hint used: -5 points
  - Incorrect attempt: -2 points
- **Why Thread-Safe**: Multiple threads may update score simultaneously

### SolverController.java
**DEMO SOLVER MODE**

- **Purpose**: Demonstration-only solver (evaluation mode)
- **Features**:
  - Uses SAME multithreaded engine as validation/hints
  - Computes all word paths in parallel
  - Animates solutions sequentially
  - Disables scoring
- **Why Separate**: Clearly marked as demonstration-only, not part of game

### GameService.java
**SESSION MANAGEMENT**

- **Purpose**: Manages active game sessions
- **Components per Session**:
  - PuzzleEngine (multithreaded operations)
  - HintManager (hint generation)
  - ScoreManager (thread-safe scoring)
  - SolverController (demo mode)
  - LevelConfig (game rules)
- **Why**: Separates game logic from HTTP handling

### LevelConfig.java
**DIFFICULTY CONFIGURATION**

- **Purpose**: Configures level difficulty and game rules
- **Controls**:
  - Grid size
  - Allowed directions (horizontal, vertical, diagonal, reverse)
  - Word overlap frequency
  - Thread pool size (scales with difficulty)
  - Maximum hints per level
  - Scoring rules

## Multithreading Implementation Details

### Work Partitioning Strategy

**Word-Level Partitioning**:
- Each word is assigned to a separate thread
- Words are independent - no shared state during search
- Natural parallelization with minimal synchronization

**Why This Strategy**:
- Simple and efficient
- No race conditions (each thread works on different word)
- Only results need synchronization (ConcurrentHashMap)

### Thread Pool Configuration

- **Easy Level**: 2 threads
- **Medium Level**: 4 threads
- **Hard/Expert Level**: Up to 8 threads (based on available processors)

**Why**: Scales thread pool with difficulty and workload

### Synchronization Mechanisms

1. **ConcurrentHashMap**: Thread-safe result storage
2. **AtomicInteger**: Lock-free score updates
3. **ReentrantReadWriteLock**: Complex score operations
4. **Future Objects**: Result collection from parallel tasks
5. **ExecutorService**: Thread pool management

## HTTP Endpoints

### Game Endpoints
- `POST /startGame?id={id}` - Start new game session
- `POST /validatePath?id={id}&path={json}` - Validate player path (multithreaded)
- `GET /hint?id={id}` - Generate hint (multithreaded)
- `POST /startDemoSolver?id={id}` - Start demo solver
- `GET /demoSolverResults?id={id}` - Get demo solver results
- `GET /score?id={id}` - Get current score
- `GET /gameState?id={id}` - Get game state (found words, hints used)

### Existing Solver Endpoints (Preserved)
- `GET /levels` - List all levels
- `GET /level?id={id}` - Get level data
- `POST /startSolve?id={id}&type={single|multithreaded}` - Start solver
- `GET /progress?id={id}` - Get solving progress
- `GET /results?id={id}` - Get solver results

## Frontend Architecture

### Game Mode vs Solver Mode

**Game Mode** (Default):
- Interactive word selection
- Path building on grid
- Hint system
- Scoring
- Demo solver

**Solver Mode**:
- Original solver functionality
- Performance comparison
- Analysis mode

### Player Interaction Flow

1. **Path Selection**:
   - Click cells to build path
   - Only adjacent cells allowed
   - Visual feedback (selected cells highlighted)

2. **Path Validation**:
   - Submit path → Backend validates (multithreaded)
   - Returns result → UI updates
   - Score updated (thread-safe)

3. **Hint Generation**:
   - Request hint → Backend searches remaining words (parallel)
   - Returns partial clue → UI displays
   - Score deducted (thread-safe)

4. **Demo Solver**:
   - Start demo → Backend solves all words (parallel)
   - Results returned → UI animates sequentially
   - Scoring disabled

## Code Quality & Documentation

### Comments Explain:
- **WHY** multithreading is used
- **HOW** tasks are parallelized
- **HOW** shared data is kept safe
- Design decisions for academic evaluation

### Code Structure:
- Clear separation of concerns
- No business logic in UI
- All operations non-blocking
- Thread-safe throughout

## Testing & Evaluation Points

### For Viva/Evaluation:

1. **Multithreading**:
   - Explain ExecutorService usage
   - Word-level partitioning strategy
   - Thread safety mechanisms

2. **Performance**:
   - Parallel vs sequential comparison
   - Thread pool sizing
   - Scalability considerations

3. **Synchronization**:
   - Why ConcurrentHashMap
   - AtomicInteger for score
   - Future objects for results

4. **Architecture**:
   - Separation of concerns
   - Immutable data structures
   - Non-blocking operations

## File Structure

```
Backend (Java):
├── PuzzleEngine.java          # Core multithreaded engine
├── HintManager.java           # Multithreaded hint generation
├── ScoreManager.java          # Thread-safe scoring
├── SolverController.java      # Demo solver mode
├── GameService.java           # Game session management
├── LevelConfig.java           # Difficulty configuration
├── Level.java                  # Level data (existing)
├── LevelRepository.java        # Level storage (existing)
├── WordSearchSolver.java      # Single-threaded baseline (existing)
├── ParallelWordSearchSolver.java  # Multithreaded solver (existing)
├── SolverService.java         # Solver sessions (existing)
├── SimpleHTTPServer.java      # HTTP server (extended)
└── Main.java                  # Entry point (existing)

Frontend:
├── index.html                 # UI (extended with game controls)
├── style.css                  # Styling (extended)
└── app.js                     # Frontend logic (extended)
```

## Key Features Implemented

✅ **Multithreaded Engine** - All operations use ExecutorService  
✅ **Player Validation** - Multithreaded path validation  
✅ **Hint System** - Parallel hint generation  
✅ **Demo Solver** - Uses same engine, clearly marked as demo  
✅ **Scoring System** - Thread-safe score management  
✅ **Multiple Difficulty Levels** - Configurable per level  
✅ **Separation of Concerns** - UI, Engine, Service layers  
✅ **Comprehensive Documentation** - Comments explain design decisions  

## Constraints Met

✅ **No frameworks** - Plain Java only  
✅ **No databases** - In-memory storage  
✅ **Multithreading is CORE** - All operations parallelized  
✅ **UI thread never blocked** - All operations in background  
✅ **Clear architecture** - Easy to explain in viva  
✅ **Existing code preserved** - Solver mode still works  

