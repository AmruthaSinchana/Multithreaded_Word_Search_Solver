import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * GameService.java (FIXED - Correct Scoring Logic)
 * 
 * KEY FIXES:
 * 1. Track found words in Set to prevent duplicate scoring
 * 2. Demo solver does NOT affect score
 * 3. Score updates only once per unique word
 * 4. Clear score tracking between levels
 */
public class GameService {
    private static GameService instance;
    private ConcurrentHashMap<Integer, GameSession> activeSessions;
    
    private GameService() {
        activeSessions = new ConcurrentHashMap<>();
    }
    
    public static synchronized GameService getInstance() {
        if (instance == null) {
            instance = new GameService();
        }
        return instance;
    }
    
    public void startGame(int levelId) {
        Level level = LevelRepository.getInstance().getLevel(levelId);
        if (level == null) return;
        
        LevelConfig config = createLevelConfig(levelId, level.getSize(), level.getDifficulty());
        PuzzleEngine engine = new PuzzleEngine(level.getGrid(), config.getThreadPoolSize());
        
        GameHintManager hintManager = new GameHintManager(engine, level.getWords(), config);
        GameScoreManager scoreManager = new GameScoreManager(config.getScoringRules());
        GameSolverController solverController = new GameSolverController(engine, config);
        
        GameSession session = new GameSession(level, engine, hintManager, scoreManager, solverController, config);
        activeSessions.put(levelId, session);
    }
    
    private LevelConfig createLevelConfig(int levelId, int size, String difficulty) {
        LevelConfig config = new LevelConfig(levelId, size);
        // ONLY horizontal and vertical
        config.setAllowedDirections(new HashSet<>(Arrays.asList("horizontal", "vertical")));
        switch (difficulty.toLowerCase()) {
            case "easy":
                config.setMaxHints(5);
                config.setThreadPoolSize(2);
                break;
            case "medium":
                config.setMaxHints(3);
                config.setThreadPoolSize(4);
                break;
            default:
                config.setMaxHints(2);
                config.setThreadPoolSize(Math.min(Runtime.getRuntime().availableProcessors(), 8));
        }
        return config;
    }
    
    public String validatePath(int levelId, String pathJson) {
        GameSession session = activeSessions.get(levelId);
        return session != null ? session.validatePath(pathJson) : "{\"error\": \"No active game session\"}";
    }
    
    public String generateHint(int levelId) {
        GameSession session = activeSessions.get(levelId);
        return session != null ? session.generateHint() : "{\"error\": \"No active game session\"}";
    }
    
    public String startDemoSolver(int levelId) {
        GameSession session = activeSessions.get(levelId);
        return session != null ? session.startDemoSolver() : "{\"error\": \"No active game session\"}";
    }
    
    public String getDemoSolverResults(int levelId) {
        GameSession session = activeSessions.get(levelId);
        return session != null ? session.getDemoSolverResults() : "{\"error\": \"No active game session\"}";
    }
    
    public String getScore(int levelId) {
        GameSession session = activeSessions.get(levelId);
        return session != null ? session.getScore() : "{\"error\": \"No active game session\"}";
    }
    
    public String getGameState(int levelId) {
        GameSession session = activeSessions.get(levelId);
        return session != null ? session.getGameState() : "{\"error\": \"No active game session\"}";
    }
    
    // Inner classes remain similar but with scoring fixes
    private static class GameHintManager {
        private PuzzleEngine engine;
        private Set<String> foundWords;
        private Set<String> allWords;
        private LevelConfig config;
        private int hintsUsed;
        
        public GameHintManager(PuzzleEngine engine, String[] allWords, LevelConfig config) {
            this.engine = engine;
            this.allWords = new HashSet<>();
            for (String word : allWords) {
                this.allWords.add(word.toUpperCase());
            }
            this.foundWords = new HashSet<>();
            this.config = config;
            this.hintsUsed = 0;
        }
        
        public synchronized void markWordFound(String word) {
            foundWords.add(word.toUpperCase());
        }
        
        public Hint generateHint(Set<String> allowedDirections) {
            if (hintsUsed >= config.getMaxHints()) {
                return new Hint("No hints remaining", null, null, null);
            }
            Set<String> remainingWords = new HashSet<>(allWords);
            remainingWords.removeAll(foundWords);
            if (remainingWords.isEmpty()) {
                return new Hint("All words found!", null, null, null);
            }
            String[] remainingArray = remainingWords.toArray(new String[0]);
            Map<String, PuzzleEngine.WordPath> paths = engine.searchWordsParallel(remainingArray, allowedDirections);
            if (paths.isEmpty()) {
                return new Hint("No valid paths found", null, null, null);
            }
            String[] foundArray = paths.keySet().toArray(new String[0]);
            String selectedWord = foundArray[new Random().nextInt(foundArray.length)];
            PuzzleEngine.WordPath path = paths.get(selectedWord);
            hintsUsed++;
            return generatePartialHint(selectedWord, path);
        }
        
        private Hint generatePartialHint(String word, PuzzleEngine.WordPath path) {
            Random random = new Random();
            int hintType = random.nextInt(3);
            List<int[]> pathCells = path.getPath();
            switch (hintType) {
                case 0:
                    int[] startCell = pathCells.get(0);
                    return new Hint("Word starts at row " + (startCell[0] + 1) + ", col " + (startCell[1] + 1),
                                  startCell, null, null);
                case 1:
                    int[] dir = getDirection(pathCells);
                    String directionName = getDirectionName(dir);
                    return new Hint("Word goes " + directionName, null, directionName, null);
                default:
                    int letterIndex = random.nextInt(word.length());
                    int[] letterCell = pathCells.get(letterIndex);
                    return new Hint("Letter at row " + (letterCell[0] + 1) + ", col " + (letterCell[1] + 1) + " is '" + word.charAt(letterIndex) + "'",
                                  letterCell, null, String.valueOf(word.charAt(letterIndex)));
            }
        }
        
        private int[] getDirection(List<int[]> path) {
            if (path.size() < 2) return new int[]{0, 1};
            int[] first = path.get(0);
            int[] second = path.get(1);
            return new int[]{second[0] - first[0], second[1] - first[1]};
        }
        
        private String getDirectionName(int[] dir) {
            if (dir[0] == 0) return "horizontally";
            if (dir[1] == 0) return "vertically";
            return "diagonally"; // Should never happen with our constraints
        }
        
        public int getHintsUsed() { return hintsUsed; }
        public int getRemainingHints() { return config.getMaxHints() - hintsUsed; }
        
        public static class Hint {
            private String message;
            private int[] cellHint;
            private String directionHint;
            private String letterHint;
            
            public Hint(String message, int[] cellHint, String directionHint, String letterHint) {
                this.message = message;
                this.cellHint = cellHint;
                this.directionHint = directionHint;
                this.letterHint = letterHint;
            }
            
            public String getMessage() { return message; }
            public int[] getCellHint() { return cellHint; }
            public String getDirectionHint() { return directionHint; }
            public String getLetterHint() { return letterHint; }
            
            public String toJSON() {
                StringBuilder json = new StringBuilder();
                json.append("{\n  \"message\": \"").append(message).append("\",\n");
                if (cellHint != null) json.append("  \"cellHint\": [").append(cellHint[0]).append(", ").append(cellHint[1]).append("],\n");
                if (directionHint != null) json.append("  \"directionHint\": \"").append(directionHint).append("\",\n");
                if (letterHint != null) json.append("  \"letterHint\": \"").append(letterHint).append("\",\n");
                json.append("  \"hintsRemaining\": 0\n}");
                return json.toString();
            }
        }
    }
    
    private static class GameScoreManager {
        private AtomicInteger score;
        private ReentrantReadWriteLock lock;
        private boolean scoringEnabled;
        private Map<String, Integer> scoringRules;
        
        public GameScoreManager(Map<String, Integer> scoringRules) {
            this.score = new AtomicInteger(0);
            this.lock = new ReentrantReadWriteLock();
            this.scoringEnabled = true;
            this.scoringRules = new ConcurrentHashMap<>(scoringRules);
        }
        
        public int getScore() { return score.get(); }
        
        public void addCorrectWord(boolean isDiagonal) {
            if (!scoringEnabled) return;
            // NO diagonal bonus since we don't allow diagonals
            int points = scoringRules.getOrDefault("correct_word", 10);
            score.addAndGet(points);
        }
        
        public void deductHintPoints() {
            if (!scoringEnabled) return;
            score.addAndGet(scoringRules.getOrDefault("hint_used", -5));
        }
        
        public void deductIncorrectAttempt() {
            if (!scoringEnabled) return;
            score.addAndGet(scoringRules.getOrDefault("incorrect_attempt", -2));
        }
        
        public void setScoringEnabled(boolean enabled) {
            lock.writeLock().lock();
            try { this.scoringEnabled = enabled; } finally { lock.writeLock().unlock(); }
        }
        
        public String toJSON() {
            return "{\n  \"score\": " + score.get() + ",\n  \"scoringEnabled\": " + scoringEnabled + "\n}";
        }
    }
    
    private static class GameSolverController {
        private PuzzleEngine engine;
        private Map<String, PuzzleEngine.WordPath> allSolutions;
        private boolean isSolving;
        
        public GameSolverController(PuzzleEngine engine, LevelConfig config) {
            this.engine = engine;
            this.allSolutions = new ConcurrentHashMap<>();
            this.isSolving = false;
        }
        
        public void solveAllWords(String[] words) {
            if (isSolving) return;
            isSolving = true;
            allSolutions.clear();
            Set<String> allDirections = new HashSet<>();
            allDirections.add("all");
            Map<String, PuzzleEngine.WordPath> solutions = engine.searchWordsParallel(words, allDirections);
            allSolutions.putAll(solutions);
            isSolving = false;
        }
        
        public String getSolutionsJSON() {
            StringBuilder json = new StringBuilder();
            json.append("{\n  \"demoMode\": true,\n  \"totalWords\": ").append(allSolutions.size()).append(",\n  \"solutions\": {\n");
            int count = 0;
            for (Map.Entry<String, PuzzleEngine.WordPath> entry : allSolutions.entrySet()) {
                json.append("    \"").append(entry.getKey()).append("\": {\n");
                PuzzleEngine.WordPath path = entry.getValue();
                json.append("      \"word\": \"").append(path.getWord()).append("\",\n      \"path\": [\n");
                List<int[]> pathCells = path.getPath();
                for (int i = 0; i < pathCells.size(); i++) {
                    json.append("        [").append(pathCells.get(i)[0]).append(", ").append(pathCells.get(i)[1]).append("]");
                    if (i < pathCells.size() - 1) json.append(",");
                    json.append("\n");
                }
                json.append("      ]\n    }");
                if (count < allSolutions.size() - 1) json.append(",");
                json.append("\n");
                count++;
            }
            json.append("  }\n}");
            return json.toString();
        }
    }
    
    private static class GameSession {
        private Level level;
        private PuzzleEngine engine;
        private GameHintManager hintManager;
        private GameScoreManager scoreManager;
        private GameSolverController solverController;
        private LevelConfig config;
        private Set<String> foundWords; // CRITICAL: Prevents duplicate scoring
        
        public GameSession(Level level, PuzzleEngine engine, GameHintManager hintManager,
                         GameScoreManager scoreManager, GameSolverController solverController,
                         LevelConfig config) {
            this.level = level;
            this.engine = engine;
            this.hintManager = hintManager;
            this.scoreManager = scoreManager;
            this.solverController = solverController;
            this.config = config;
            this.foundWords = ConcurrentHashMap.newKeySet();
        }
        
        public String validatePath(String pathJson) {
            List<int[]> playerPath = parsePathFromJSON(pathJson);
            
            StringBuilder wordBuilder = new StringBuilder();
            for (int[] cell : playerPath) {
                if (cell[0] >= 0 && cell[0] < level.getSize() && cell[1] >= 0 && cell[1] < level.getSize()) {
                    wordBuilder.append(level.getGrid()[cell[0]][cell[1]]);
                }
            }
            String playerWord = wordBuilder.toString().toUpperCase();
            
            // Check if word is in the level's word list
            boolean wordInList = false;
            for (String word : level.getWords()) {
                if (word.toUpperCase().equals(playerWord)) {
                    wordInList = true;
                    break;
                }
            }
            
            if (!wordInList) {
                scoreManager.deductIncorrectAttempt();
                return "{\"valid\": false, \"word\": \"" + playerWord + "\", \"error\": \"Word not in list\"}";
            }
            
            // CRITICAL: Check if already found BEFORE validation to prevent double-scoring
            if (foundWords.contains(playerWord)) {
                return "{\"valid\": false, \"word\": \"" + playerWord + "\", \"error\": \"Word already found\"}";
            }
            
            // Validate path structure
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<PuzzleEngine.ValidationResult> future = executor.submit(() -> {
                return engine.validatePath(playerPath, config.getAllowedDirections());
            });
            try {
                PuzzleEngine.ValidationResult result = future.get(2, TimeUnit.SECONDS);
                if (result.isValid()) {
                    // CRITICAL: Add to found words BEFORE scoring to prevent race condition
                    boolean wasNew = foundWords.add(playerWord);
                    if (wasNew) {
                        hintManager.markWordFound(playerWord);
                        scoreManager.addCorrectWord(false); // No diagonals
                        return "{\"valid\": true, \"word\": \"" + playerWord + "\", \"isForward\": true}";
                    } else {
                        // Race condition caught - word was added between check and validation
                        return "{\"valid\": false, \"word\": \"" + playerWord + "\", \"error\": \"Word already found\"}";
                    }
                } else {
                    scoreManager.deductIncorrectAttempt();
                    return "{\"valid\": false, \"word\": \"" + playerWord + "\", \"error\": \"Invalid path\"}";
                }
            } catch (Exception e) {
                scoreManager.deductIncorrectAttempt();
                return "{\"valid\": false, \"word\": \"" + playerWord + "\", \"error\": \"" + e.getMessage() + "\"}";
            } finally {
                executor.shutdown();
            }
        }
        
        private List<int[]> parsePathFromJSON(String json) {
            List<int[]> path = new ArrayList<>();
            String[] parts = json.replaceAll("[\\[\\]\\{}\"]", "").split(",");
            for (int i = 0; i < parts.length; i += 2) {
                if (i + 1 < parts.length) {
                    try {
                        int row = Integer.parseInt(parts[i].trim());
                        int col = Integer.parseInt(parts[i + 1].trim());
                        path.add(new int[]{row, col});
                    } catch (NumberFormatException e) {}
                }
            }
            return path;
        }
        
        public String generateHint() {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<GameHintManager.Hint> future = executor.submit(() -> {
                return hintManager.generateHint(config.getAllowedDirections());
            });
            try {
                GameHintManager.Hint hint = future.get(2, TimeUnit.SECONDS);
                scoreManager.deductHintPoints();
                String json = hint.toJSON();
                json = json.substring(0, json.length() - 2) + ",\n  \"hintsRemaining\": " + hintManager.getRemainingHints() + "\n}";
                return json;
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            } finally {
                executor.shutdown();
            }
        }
        
        public String startDemoSolver() {
            // CRITICAL: Disable scoring for demo mode
            scoreManager.setScoringEnabled(false);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> solverController.solveAllWords(level.getWords()));
            executor.shutdown();
            return "{\"status\": \"started\", \"demoMode\": true}";
        }
        
        public String getDemoSolverResults() {
            return solverController.getSolutionsJSON();
        }
        
        public String getScore() {
            return scoreManager.toJSON();
        }
        
        public String getGameState() {
            StringBuilder json = new StringBuilder();
            json.append("{\n  \"foundWords\": [\n");
            int count = 0;
            for (String word : foundWords) {
                json.append("    \"").append(word).append("\"");
                if (count < foundWords.size() - 1) json.append(",");
                json.append("\n");
                count++;
            }
            json.append("  ],\n  \"totalWords\": ").append(level.getWords().length).append(",\n");
            json.append("  \"hintsUsed\": ").append(hintManager.getHintsUsed()).append(",\n");
            json.append("  \"hintsRemaining\": ").append(hintManager.getRemainingHints()).append(",\n");
            json.append("  \"score\": ").append(scoreManager.getScore()).append("\n}");
            return json.toString();
        }
    }
}