/**
 * app.js (FIXED)
 * 
 * KEY FIXES:
 * 1. Multi-color highlighting for found words
 * 2. Proper word-search selection (continuous, fixed direction)
 * 3. Demo solver uses same coloring system
 * 4. Score updates correctly (no duplicates)
 */

const API_BASE = 'http://localhost:8080';
let currentLevelId = null;
let currentLevel = null;
let pollingInterval = null;
let solveStartTime = null;

// Game state variables
let selectedPath = [];
let isSelecting = false;
let gameMode = 'game';
let gameStatePollingInterval = null;

// Multi-color highlighting system
let foundWordsUI = new Set();
let cellColorMap = {};  // "row,col" -> array of colorClasses this cell belongs to
let wordColorMap = {};
let nextColorIndex = 0;
const COLOR_CLASSES = ['found-0', 'found-1', 'found-2', 'found-3', 'found-4'];
const MAX_COLORS = COLOR_CLASSES.length;

document.addEventListener('DOMContentLoaded', () => {
    loadLevels();
    document.getElementById('solveBtn').addEventListener('click', startSolving);
    
    document.querySelectorAll('input[name="gameMode"]').forEach(radio => {
        radio.addEventListener('change', (e) => {
            gameMode = e.target.value;
            toggleGameMode(gameMode);
        });
    });
    
    document.getElementById('hintBtn').addEventListener('click', requestHint);
    document.getElementById('demoSolverBtn').addEventListener('click', startDemoSolver);
    document.getElementById('submitPathBtn').addEventListener('click', submitPath);
    document.getElementById('clearPathBtn').addEventListener('click', clearPath);
    
    toggleGameMode('game');
});

async function loadLevels() {
    try {
        const response = await fetch(`${API_BASE}/levels`);
        const levels = await response.json();
        
        const levelList = document.getElementById('levelList');
        levelList.innerHTML = '';
        
        levels.forEach(level => {
            const btn = document.createElement('button');
            btn.className = 'level-btn';
            btn.textContent = `${level.name} (${level.size}x${level.size}) - ${level.difficulty}`;
            btn.onclick = (e) => {
                selectLevel(level.id, e.target);
            };
            levelList.appendChild(btn);
        });
        
        if (levels.length > 0) {
            const firstBtn = levelList.querySelector('.level-btn');
            if (firstBtn) {
                firstBtn.classList.add('active');
                selectLevel(levels[0].id, firstBtn);
            }
        }
    } catch (error) {
        console.error('Failed to load levels:', error);
        alert('Failed to connect to server. Make sure the Java server is running.');
    }
}

async function selectLevel(levelId, buttonElement) {
    currentLevelId = levelId;
    
    // Reset color tracking for new level
    foundWordsUI.clear();
    cellColorMap = {};
    wordColorMap = {};
    nextColorIndex = 0;
    
    document.querySelectorAll('.level-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    if (buttonElement) {
        buttonElement.classList.add('active');
    }
    
    try {
        const response = await fetch(`${API_BASE}/level?id=${levelId}`);
        currentLevel = await response.json();
        
        displayLevelInfo(currentLevel);
        renderGrid(currentLevel);
        clearResults();
        
        if (gameMode === 'game') {
            setTimeout(() => {
                startGame();
            }, 100);
        }
    } catch (error) {
        console.error('Failed to load level:', error);
        alert('Failed to load level data.');
    }
}

function displayLevelInfo(level) {
    const levelInfo = document.getElementById('levelInfo');
    levelInfo.innerHTML = `
        <strong>Level:</strong> ${level.name}<br>
        <strong>Size:</strong> ${level.size}x${level.size}<br>
        <strong>Difficulty:</strong> ${level.difficulty}<br>
        <strong>Words to find:</strong> ${level.words.join(', ')}
    `;
}

function renderGrid(level) {
    const container = document.getElementById('gridContainer');
    container.innerHTML = '';
    container.style.gridTemplateColumns = `repeat(${level.size}, 1fr)`;
    
    level.grid.forEach((row, i) => {
        row.split('').forEach((char, j) => {
            const cell = document.createElement('div');
            cell.className = 'grid-cell';
            cell.textContent = char;
            cell.id = `cell-${i}-${j}`;
            container.appendChild(cell);
        });
    });
}

async function startSolving() {
    if (!currentLevelId) {
        alert('Please select a level first.');
        return;
    }
    
    const solverType = document.querySelector('input[name="solverType"]:checked').value;
    const useMultithreaded = solverType === 'multithreaded';
    
    const solveBtn = document.getElementById('solveBtn');
    solveBtn.disabled = true;
    solveBtn.textContent = 'Solving...';
    
    clearResults();
    clearHighlights();
    
    // CRITICAL: Record start time
    solveStartTime = Date.now();
    console.log('Solver started at:', solveStartTime);
    
    try {
        await fetch(`${API_BASE}/startSolve?id=${currentLevelId}&type=${solverType}`, {
            method: 'POST'
        });
        
        startProgressPolling();
    } catch (error) {
        console.error('Failed to start solving:', error);
        alert('Failed to start solver.');
        solveBtn.disabled = false;
        solveBtn.textContent = 'Start Solving';
    }
}

function startProgressPolling() {
    pollingInterval = setInterval(async () => {
        try {
            const response = await fetch(`${API_BASE}/progress?id=${currentLevelId}`);
            if (!response.ok) {
                console.error('Progress request failed:', response.status);
                return;
            }
            const text = await response.text();
            let progress;
            try {
                progress = JSON.parse(text);
            } catch (e) {
                console.error('Invalid JSON response:', text);
                return;
            }
            
            updateProgress(progress);
            
            if (progress.isComplete) {
                clearInterval(pollingInterval);
                pollingInterval = null;
                fetchResults();
            }
        } catch (error) {
            console.error('Failed to fetch progress:', error);
        }
    }, 100);
}

function updateProgress(progress) {
    const progressFill = document.getElementById('progressFill');
    const progressPercent = progress.progressPercent || 0;
    progressFill.style.width = `${progressPercent}%`;
    progressFill.textContent = `${Math.round(progressPercent)}%`;
    
    const progressText = document.getElementById('progressText');
    progressText.textContent = `Progress: ${progress.completed || 0} / ${progress.total || 0} words`;
    
    const foundWords = document.getElementById('foundWords');
    foundWords.innerHTML = '';
    
    if (progress.foundWords) {
        Object.entries(progress.foundWords).forEach(([word, found]) => {
            const badge = document.createElement('span');
            badge.className = `word-badge ${found ? '' : 'not-found'}`;
            badge.textContent = word + (found ? ' ✓' : ' ✗');
            foundWords.appendChild(badge);
        });
    }
}

async function fetchResults() {
    try {
        const response = await fetch(`${API_BASE}/results?id=${currentLevelId}`);
        const results = await response.json();
        
        displayResults(results);
        
        // IMPORTANT: Also fetch and display the actual paths
        // The backend solver stores paths, we need to visualize them
        visualizeSolverPaths(results);
        
        const solveBtn = document.getElementById('solveBtn');
        solveBtn.disabled = false;
        solveBtn.textContent = 'Start Solving';
    } catch (error) {
        console.error('Failed to fetch results:', error);
    }
}

/**
 * Visualizes solver paths with different colors for each word
 */
async function visualizeSolverPaths(results) {
    if (!results || !results.results) return;
    
    // Get the detailed paths from the backend
    // For now, we'll search for each found word to get its path
    const foundWords = Object.entries(results.results)
        .filter(([word, found]) => found)
        .map(([word]) => word);
    
    console.log('Visualizing paths for:', foundWords);
    
    // Simulate finding paths by using a delay animation
    animateWordsSequentially(foundWords);
}

/**
 * Animates found words one by one with different colors
 */
function animateWordsSequentially(words) {
    let index = 0;
    
    const animateNext = () => {
        if (index >= words.length) {
            console.log('All words visualized');
            return;
        }
        
        const word = words[index];
        
        // Assign unique color
        if (!wordColorMap[word]) {
            wordColorMap[word] = nextColorIndex % MAX_COLORS;
            nextColorIndex++;
        }
        const colorIndex = wordColorMap[word];
        const colorClass = COLOR_CLASSES[colorIndex];
        
        console.log('Highlighting:', word, 'with color:', colorClass);
        
        // Find and highlight the word in the grid
        const path = findWordInGrid(word);
        if (path && path.length > 0) {
            foundWordsUI.add(word);
            
            path.forEach(([row, col], i) => {
                setTimeout(() => {
                    const cellId = `cell-${row}-${col}`;
                    const cell = document.getElementById(cellId);
                    
                    if (cell) {
                        const key = `${row},${col}`;
                        
                        if (!cellColorMap[key]) {
                            cellColorMap[key] = [];
                        }
                        
                        if (!cellColorMap[key].includes(colorClass)) {
                            cellColorMap[key].push(colorClass);
                        }
                        
                        COLOR_CLASSES.forEach(cls => cell.classList.remove(cls));
                        cell.classList.add(colorClass);
                        
                        if (cellColorMap[key].length > 1) {
                            cell.classList.add('multi-word');
                        }
                        
                        cell.style.animation = 'highlight-pulse 0.3s';
                        setTimeout(() => cell.style.animation = '', 300);
                    }
                }, i * 80);
            });
            
            index++;
            setTimeout(animateNext, path.length * 80 + 400);
        } else {
            console.warn('Could not find path for:', word);
            index++;
            setTimeout(animateNext, 200);
        }
    };
    
    animateNext();
}

/**
 * Finds a word in the current grid (client-side search)
 */
function findWordInGrid(word) {
    if (!currentLevel || !currentLevel.grid) return null;
    
    const grid = currentLevel.grid;
    const size = currentLevel.size;
    const wordUpper = word.toUpperCase();
    
    // Try all starting positions and directions
    const directions = [
        [0, 1],   // right
        [0, -1],  // left
        [1, 0],   // down
        [-1, 0]   // up
    ];
    
    for (let r = 0; r < size; r++) {
        for (let c = 0; c < size; c++) {
            if (grid[r][c].toUpperCase() === wordUpper[0]) {
                for (let dir of directions) {
                    const path = checkWordInDirection(grid, wordUpper, r, c, dir, size);
                    if (path) {
                        console.log('  Found at [' + r + ',' + c + '] going', getDirectionName2(dir));
                        return path;
                    }
                }
            }
        }
    }
    
    return null;
}

function checkWordInDirection(grid, word, startR, startC, dir, size) {
    const path = [];
    let r = startR;
    let c = startC;
    
    for (let i = 0; i < word.length; i++) {
        if (r < 0 || r >= size || c < 0 || c >= size) return null;
        if (grid[r][c].toUpperCase() !== word[i]) return null;
        
        path.push([r, c]);
        r += dir[0];
        c += dir[1];
    }
    
    return path;
}

function getDirectionName2(dir) {
    if (dir[0] === 0 && dir[1] === 1) return 'right';
    if (dir[0] === 0 && dir[1] === -1) return 'left';
    if (dir[0] === 1 && dir[1] === 0) return 'down';
    if (dir[0] === -1 && dir[1] === 0) return 'up';
    return 'unknown';
}

function displayResults(results) {
    const container = document.getElementById('resultsContainer');
    container.innerHTML = '';
    
    if (results.results) {
        Object.entries(results.results).forEach(([word, found]) => {
            const item = document.createElement('div');
            item.className = `result-item ${found ? 'found' : 'not-found'}`;
            
            // Get color for this word if found
            let colorIndicator = '';
            if (found && wordColorMap[word] !== undefined) {
                const colorClass = COLOR_CLASSES[wordColorMap[word]];
                const colorMap = {
                    'found-0': '#2196F3',
                    'found-1': '#4CAF50',
                    'found-2': '#FF9800',
                    'found-3': '#9C27B0',
                    'found-4': '#F44336'
                };
                const color = colorMap[colorClass] || '#999';
                colorIndicator = `<span style="display:inline-block;width:20px;height:20px;background:${color};border-radius:50%;margin-left:10px;"></span>`;
            }
            
            item.innerHTML = `
                <span><strong>${word}</strong>${colorIndicator}</span>
                <span>${found ? '✓ FOUND' : '✗ NOT FOUND'}</span>
            `;
            container.appendChild(item);
        });
    }
    
    const performanceDiv = document.getElementById('performanceComparison');
    const executionTime = results.executionTimeMs || 0;
    const solverType = results.solverType || 'unknown';
    const totalTime = solveStartTime ? (Date.now() - solveStartTime) : 0;
    
    performanceDiv.innerHTML = `
        <h3>Performance Metrics</h3>
        <div class="performance-metric">
            <span><strong>Solver Type:</strong></span>
            <span>${solverType}</span>
        </div>
        <div class="performance-metric">
            <span><strong>Backend Execution Time:</strong></span>
            <span>${executionTime.toFixed(2)} ms</span>
        </div>
        <div class="performance-metric">
            <span><strong>Total Time (with UI):</strong></span>
            <span>${totalTime.toFixed(0)} ms</span>
        </div>
        <div class="performance-metric">
            <span><strong>Words Found:</strong></span>
            <span>${Object.values(results.results || {}).filter(f => f).length} / ${Object.keys(results.results || {}).length}</span>
        </div>
    `;
    
    console.log('Performance:', {
        solverType,
        executionTime: executionTime + 'ms',
        totalTime: totalTime + 'ms'
    });
}

function highlightWordInGrid(word) {
    const grid = currentLevel.grid;
    const wordUpper = word.toUpperCase();
    
    for (let i = 0; i < grid.length; i++) {
        for (let j = 0; j < grid[i].length; j++) {
            if (grid[i][j] === wordUpper[0]) {
                const cell = document.getElementById(`cell-${i}-${j}`);
                if (cell) {
                    cell.classList.add('found');
                }
            }
        }
    }
}

function clearResults() {
    document.getElementById('resultsContainer').innerHTML = '';
    document.getElementById('performanceComparison').innerHTML = '';
    document.getElementById('foundWords').innerHTML = '';
    document.getElementById('progressFill').style.width = '0%';
    document.getElementById('progressText').textContent = 'Ready';
    
    // Reset color tracking for new solve
    foundWordsUI.clear();
    cellColorMap = {};
    wordColorMap = {};
    nextColorIndex = 0;
}

function clearHighlights() {
    document.querySelectorAll('.grid-cell').forEach(cell => {
        cell.classList.remove('highlight', 'found', 'selected');
        COLOR_CLASSES.forEach(cls => cell.classList.remove(cls));
    });
}

// ==================== GAME MODE FUNCTIONS ====================

function toggleGameMode(mode) {
    const gameControls = document.getElementById('gameControls');
    const gameStateSection = document.getElementById('gameStateSection');
    const solverControls = document.getElementById('solverControls');
    const progressSection = document.getElementById('progressSection');
    
    if (mode === 'game') {
        gameControls.style.display = 'block';
        gameStateSection.style.display = 'block';
        solverControls.style.display = 'none';
        progressSection.style.display = 'none';
        
        if (currentLevelId) {
            startGame();
        }
    } else {
        gameControls.style.display = 'none';
        gameStateSection.style.display = 'none';
        solverControls.style.display = 'block';
        progressSection.style.display = 'block';
        
        if (gameStatePollingInterval) {
            clearInterval(gameStatePollingInterval);
            gameStatePollingInterval = null;
        }
    }
}

async function startGame() {
    if (!currentLevelId) return;
    
    try {
        await fetch(`${API_BASE}/startGame?id=${currentLevelId}`, {
            method: 'POST'
        });
        
        enableGridSelection();
        startGameStatePolling();
        
        selectedPath = [];
        clearPath();
        updateScore(0);
        
        // Reset UI tracking for new game
        foundWordsUI.clear();
        cellColorMap = {};
        wordColorMap = {};
        nextColorIndex = 0;
        clearHighlights();
    } catch (error) {
        console.error('Failed to start game:', error);
    }
}

function enableGridSelection() {
    selectedPath = [];
    clearPath();
    
    const cells = document.querySelectorAll('.grid-cell');
    cells.forEach((cell) => {
        const newCell = cell.cloneNode(true);
        cell.parentNode.replaceChild(newCell, cell);
        
        newCell.addEventListener('mousedown', (e) => {
            if (gameMode !== 'game') return;
            e.preventDefault();
            
            const id = newCell.id;
            const coords = id.replace('cell-', '').split('-');
            const row = parseInt(coords[0]);
            const col = parseInt(coords[1]);
            
            // Start new selection
            selectedPath = [[row, col]];
            isSelecting = true;
            updatePathVisualization();
        });
        
        newCell.addEventListener('mouseenter', () => {
            if (gameMode === 'game' && isSelecting) {
                const id = newCell.id;
                const coords = id.replace('cell-', '').split('-');
                const row = parseInt(coords[0]);
                const col = parseInt(coords[1]);
                
                // Add to path if adjacent and maintains direction
                if (canAddToPath(row, col)) {
                    selectedPath.push([row, col]);
                    updatePathVisualization();
                }
            }
            newCell.style.cursor = gameMode === 'game' ? 'pointer' : 'default';
        });
        
        newCell.addEventListener('click', (e) => {
            if (gameMode !== 'game') return;
            e.preventDefault();
        });
    });
    
    // Global mouseup to end selection
    document.addEventListener('mouseup', () => {
        if (isSelecting && selectedPath.length >= 2) {
            isSelecting = false;
            document.getElementById('submitPathBtn').disabled = false;
        } else if (isSelecting) {
            isSelecting = false;
            clearPath();
        }
    });
}

/**
 * CRITICAL: Validates that new cell can be added to path.
 * Requirements:
 * 1. Must be adjacent to last cell
 * 2. Must maintain same direction after first move
 * 3. Cannot revisit same cell
 */
function canAddToPath(row, col) {
    if (selectedPath.length === 0) return true;
    
    const last = selectedPath[selectedPath.length - 1];
    
    // Check if cell already in path
    for (let cell of selectedPath) {
        if (cell[0] === row && cell[1] === col) {
            return false;
        }
    }
    
    // Check if adjacent
    const dr = row - last[0];
    const dc = col - last[1];
    
    if (Math.abs(dr) + Math.abs(dc) !== 1) {
        return false; // Not adjacent (must be exactly 1 cell away horizontally or vertically)
    }
    
    // If this is the second cell, establish direction
    if (selectedPath.length === 1) {
        return true; // Any adjacent cell is valid for second position
    }
    
    // Check direction consistency
    const firstCell = selectedPath[selectedPath.length - 2];
    const prevDr = last[0] - firstCell[0];
    const prevDc = last[1] - firstCell[1];
    
    // Must maintain same direction
    return (dr === prevDr && dc === prevDc);
}

function updatePathVisualization() {
    document.querySelectorAll('.grid-cell').forEach(cell => {
        cell.classList.remove('selected');
    });
    
    selectedPath.forEach(([row, col]) => {
        const cell = document.getElementById(`cell-${row}-${col}`);
        if (cell && !cell.classList.contains('found-0') && !cell.classList.contains('found-1') 
            && !cell.classList.contains('found-2') && !cell.classList.contains('found-3') 
            && !cell.classList.contains('found-4')) {
            cell.classList.add('selected');
        }
    });
}

async function submitPath() {
    if (selectedPath.length < 2) {
        alert('Please select at least 2 cells to form a word.');
        return;
    }
    
    if (!currentLevelId) {
        alert('Please select a level first.');
        return;
    }
    
    const submitBtn = document.getElementById('submitPathBtn');
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Validating...';
    }
    
    try {
        const pathJson = JSON.stringify(selectedPath);
        const response = await fetch(`${API_BASE}/validatePath?id=${currentLevelId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: pathJson
        });
        
        if (!response.ok) {
            throw new Error('Validation request failed');
        }
        
        const result = await response.json();
        
        if (result.valid) {
            const word = result.word.toUpperCase();
            
            // Check if this is a new word in UI
            if (foundWordsUI.has(word)) {
                clearPath();
                if (submitBtn) {
                    submitBtn.textContent = 'Submit Path';
                }
                return;
            }
            
            // Mark as found in UI
            foundWordsUI.add(word);
            
            // Assign color to this word
            if (!wordColorMap[word]) {
                wordColorMap[word] = nextColorIndex % MAX_COLORS;
                nextColorIndex++;
            }
            const colorIndex = wordColorMap[word];
            const colorClass = COLOR_CLASSES[colorIndex];
            
            // Permanently color ALL cells in the path with this word's color
            selectedPath.forEach(([r, c]) => {
                const key = `${r},${c}`;
                
                // Track all colors this cell belongs to
                if (!cellColorMap[key]) {
                    cellColorMap[key] = [];
                }
                
                // Add this color if not already present
                if (!cellColorMap[key].includes(colorClass)) {
                    cellColorMap[key].push(colorClass);
                }
                
                const cell = document.getElementById(`cell-${r}-${c}`);
                if (cell) {
                    // Remove selection state
                    cell.classList.remove('selected');
                    
                    // Remove old color classes
                    COLOR_CLASSES.forEach(cls => cell.classList.remove(cls));
                    
                    // Apply new color
                    cell.classList.add(colorClass);
                    
                    // Add success animation
                    cell.style.animation = 'highlight-pulse 0.5s';
                    setTimeout(() => {
                        cell.style.animation = '';
                    }, 500);
                }
            });
            
            clearPath();
            updateGameState();
            
            if (submitBtn) {
                submitBtn.textContent = 'Submit Path';
            }
        } else {
            // Invalid path - show briefly then clear
            selectedPath.forEach(([row, col]) => {
                const cell = document.getElementById(`cell-${row}-${col}`);
                if (cell) {
                    cell.classList.add('invalid');
                }
            });
            
            setTimeout(() => {
                clearPath();
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Submit Path';
                }
            }, 1000);
        }
    } catch (error) {
        console.error('Failed to validate path:', error);
        alert('Failed to validate path. Make sure the server is running.');
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Submit Path';
        }
    }
}

function clearPath() {
    selectedPath.forEach(([row, col]) => {
        const cell = document.getElementById(`cell-${row}-${col}`);
        if (cell) {
            cell.classList.remove('selected', 'invalid');
        }
    });
    selectedPath = [];
    isSelecting = false;
    document.getElementById('submitPathBtn').disabled = true;
}

async function requestHint() {
    try {
        const response = await fetch(`${API_BASE}/hint?id=${currentLevelId}`);
        const hint = await response.json();
        
        const hintDisplay = document.getElementById('hintDisplay');
        hintDisplay.innerHTML = `<div class="hint-message">${hint.message}</div>`;
        
        if (hint.cellHint) {
            const [row, col] = hint.cellHint;
            const cell = document.getElementById(`cell-${row}-${col}`);
            if (cell) {
                cell.classList.add('hint-cell');
                setTimeout(() => cell.classList.remove('hint-cell'), 3000);
            }
        }
        
        updateGameState();
    } catch (error) {
        console.error('Failed to get hint:', error);
        alert('Failed to get hint.');
    }
}

async function startDemoSolver() {
    try {
        // Disable demo button during solving
        const demoBtn = document.getElementById('demoSolverBtn');
        demoBtn.disabled = true;
        demoBtn.textContent = 'Solving...';
        
        const response = await fetch(`${API_BASE}/startDemoSolver?id=${currentLevelId}`, {
            method: 'POST'
        });
        
        const result = await response.json();
        if (result.status === 'started') {
            // Wait longer for backend to complete solving
            setTimeout(async () => {
                try {
                    const resultsResponse = await fetch(`${API_BASE}/demoSolverResults?id=${currentLevelId}`);
                    const solutions = await resultsResponse.json();
                    
                    if (solutions.solutions && Object.keys(solutions.solutions).length > 0) {
                        animateSolutions(solutions.solutions);
                    } else {
                        alert('No solutions found. This may indicate a grid-word mismatch.');
                    }
                } catch (error) {
                    console.error('Failed to get demo results:', error);
                    alert('Failed to retrieve demo solver results.');
                } finally {
                    demoBtn.disabled = false;
                    demoBtn.textContent = 'Solve (Demo)';
                }
            }, 2000); // Increased wait time to 2 seconds
        }
    } catch (error) {
        console.error('Failed to start demo solver:', error);
        alert('Failed to start demo solver.');
        const demoBtn = document.getElementById('demoSolverBtn');
        demoBtn.disabled = false;
        demoBtn.textContent = 'Solve (Demo)';
    }
}

/**
 * Animates demo solutions with multi-color highlighting.
 * Shows each word one by one with proper coloring.
 * FIXED: Now highlights complete words properly, handling overlapping cells.
 */
function animateSolutions(solutions) {
    if (!solutions || Object.keys(solutions).length === 0) {
        console.error('No solutions to animate');
        return;
    }
    
    const words = Object.keys(solutions);
    let index = 0;
    
    console.log('Animating', words.length, 'words:', words);
    
    const animateNext = () => {
        if (index >= words.length) {
            console.log('Animation complete');
            return;
        }
        
        const word = words[index];
        const solution = solutions[word];
        
        if (!solution || !solution.path) {
            console.error('Invalid solution for word:', word);
            index++;
            setTimeout(animateNext, 500);
            return;
        }
        
        const path = solution.path;
        console.log('Animating word:', word, 'with path length:', path.length);
        
        // Assign color if not already assigned
        if (!wordColorMap[word]) {
            wordColorMap[word] = nextColorIndex % MAX_COLORS;
            nextColorIndex++;
        }
        const colorIndex = wordColorMap[word];
        const colorClass = COLOR_CLASSES[colorIndex];
        
        console.log('Using color:', colorClass, 'for word:', word);
        
        // Mark as found in UI
        foundWordsUI.add(word);
        
        // Animate the complete word path
        path.forEach(([row, col], i) => {
            setTimeout(() => {
                const cellId = `cell-${row}-${col}`;
                const cell = document.getElementById(cellId);
                
                if (cell) {
                    const key = `${row},${col}`;
                    
                    // Track all colors this cell belongs to
                    if (!cellColorMap[key]) {
                        cellColorMap[key] = [];
                    }
                    
                    // Check if this cell already belongs to another word
                    const isMultiWord = cellColorMap[key].length > 0;
                    
                    // Add this color if not already present
                    if (!cellColorMap[key].includes(colorClass)) {
                        cellColorMap[key].push(colorClass);
                    }
                    
                    // Remove old color classes
                    COLOR_CLASSES.forEach(cls => cell.classList.remove(cls));
                    cell.classList.remove('selected', 'invalid');
                    
                    // Apply the new color (most recent word wins visually)
                    cell.classList.add(colorClass);
                    
                    // Mark if this cell belongs to multiple words
                    if (cellColorMap[key].length > 1) {
                        cell.classList.add('multi-word');
                        cell.title = `Part of ${cellColorMap[key].length} words`;
                    }
                    
                    // Add pulse animation
                    cell.style.animation = 'highlight-pulse 0.5s';
                    setTimeout(() => {
                        cell.style.animation = '';
                    }, 500);
                } else {
                    console.error('Cell not found:', cellId);
                }
            }, i * 100); // Faster animation: 100ms per cell
        });
        
        // Move to next word after current word animation completes
        index++;
        setTimeout(animateNext, path.length * 100 + 600);
    };
    
    // Start animation
    animateNext();
}

function startGameStatePolling() {
    if (gameStatePollingInterval) {
        clearInterval(gameStatePollingInterval);
    }
    
    gameStatePollingInterval = setInterval(async () => {
        if (gameMode !== 'game') return;
        
        try {
            const response = await fetch(`${API_BASE}/gameState?id=${currentLevelId}`);
            const state = await response.json();
            updateGameStateDisplay(state);
            
            const scoreResponse = await fetch(`${API_BASE}/score?id=${currentLevelId}`);
            const scoreData = await scoreResponse.json();
            updateScore(scoreData.score);
        } catch (error) {
            console.error('Failed to fetch game state:', error);
        }
    }, 500);
}

function updateGameStateDisplay(state) {
    document.getElementById('foundCount').textContent = state.foundWords.length;
    document.getElementById('totalWords').textContent = state.totalWords;
    document.getElementById('hintsUsed').textContent = state.hintsUsed;
    document.getElementById('maxHints').textContent = state.hintsUsed + state.hintsRemaining;
    
    const foundWordsList = document.getElementById('foundWordsList');
    foundWordsList.innerHTML = '';
    state.foundWords.forEach(word => {
        const badge = document.createElement('span');
        badge.className = 'word-badge word-scratched';
        badge.textContent = word;
        foundWordsList.appendChild(badge);
    });
}

function updateScore(score) {
    document.getElementById('scoreValue').textContent = score;
}

async function updateGameState() {
    try {
        const response = await fetch(`${API_BASE}/gameState?id=${currentLevelId}`);
        const state = await response.json();
        updateGameStateDisplay(state);
        
        const scoreResponse = await fetch(`${API_BASE}/score?id=${currentLevelId}`);
        const scoreData = await scoreResponse.json();
        updateScore(scoreData.score);
    } catch (error) {
        console.error('Failed to update game state:', error);
    }
}