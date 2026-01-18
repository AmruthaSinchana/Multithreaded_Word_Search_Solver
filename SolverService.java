import java.util.concurrent.*;

public class SolverService {
    private static SolverService instance;
    
    // Active solving sessions: levelId -> session data
    private ConcurrentHashMap<Integer, SolverSession> activeSessions;
    
    private SolverService() {
        activeSessions = new ConcurrentHashMap<>();
    }
    
    public static synchronized SolverService getInstance() {
        if (instance == null) {
            instance = new SolverService();
        }
        return instance;
    }
    
    /**
     * Starts a solving session in background.
     * WHY: Non-blocking execution - HTTP handler returns immediately.
     */
    public void startSolve(int levelId, boolean useMultithreaded) {
        Level level = LevelRepository.getInstance().getLevel(levelId);
        if (level == null) {
            return;
        }
        
        SolverSession session = new SolverSession(level, useMultithreaded);
        activeSessions.put(levelId, session);
        
        // Execute in background thread (non-blocking)
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            session.execute();
        });
        executor.shutdown();
    }
    
    /**
     * Gets current progress for a level.
     */
    public String getProgress(int levelId) {
        SolverSession session = activeSessions.get(levelId);
        if (session == null) {
            return "{\"error\": \"No active session\"}";
        }
        return session.getProgressJSON();
    }
    
    /**
     * Gets final results for a level.
     */
    public String getResults(int levelId) {
        SolverSession session = activeSessions.get(levelId);
        if (session == null) {
            return "{\"error\": \"No active session\"}";
        }
        return session.getResultsJSON();
    }
    
    /**
     * SolverSession: Encapsulates a single solving session.
     * WHY: Keeps state (progress, results) for a specific solve operation.
     */
    private static class SolverSession {
        private Level level;
        private boolean useMultithreaded;
        private ParallelWordSearchSolver.ProgressTracker progressTracker;
        private WordSearchSolver.SolverResult result;
        private volatile boolean isComplete;
        
        public SolverSession(Level level, boolean useMultithreaded) {
            this.level = level;
            this.useMultithreaded = useMultithreaded;
            this.progressTracker = new ParallelWordSearchSolver.ProgressTracker(level.getWords().length);
            this.isComplete = false;
        }
        
        /**
         * Executes the solver (runs in background thread).
         * WHY: This is where actual computation happens asynchronously.
         */
        public void execute() {
            if (useMultithreaded) {
                result = ParallelWordSearchSolver.solveMultithreaded(
                    level.getGrid(), 
                    level.getWords(), 
                    level.getSize(),
                    progressTracker
                );
            } else {
                // For single-threaded, we still track progress (simulated)
                result = WordSearchSolver.solveSingleThreaded(
                    level.getGrid(), 
                    level.getWords(), 
                    level.getSize()
                );
                // Update progress tracker for consistency
                for (String word : level.getWords()) {
                    progressTracker.incrementProgress(word, result.getWordResults().get(word));
                }
            }
            isComplete = true;
        }
        
        public String getProgressJSON() {
            // Merge progress JSON with isComplete field
            String baseJSON = progressTracker.toJSON();
            // Remove closing brace and add isComplete field
            String jsonWithoutBrace = baseJSON.substring(0, baseJSON.length() - 1);
            return jsonWithoutBrace + ",\n  \"isComplete\": " + isComplete + "\n}";
        }
        
        public String getResultsJSON() {
            if (!isComplete) {
                return "{\"error\": \"Solving in progress\"}";
            }
            return result.toJSON();
        }
    }
}

