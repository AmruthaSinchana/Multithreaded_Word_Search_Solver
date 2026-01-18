import java.util.*;
import java.util.concurrent.*;

public class ParallelWordSearchSolver {
    
    // All 8 directions (same as single-threaded solver)
    private static final int[][] DIRECTIONS = {
    {-1, 0},
    {1, 0},
    {0, -1},
    {0, 1}
};

    
    /**
     * Searches for a word in the grid (same algorithm as single-threaded).
     */
    private static boolean searchWord(char[][] grid, String word, int rows, int cols) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == word.charAt(0)) {
                    boolean[][] visited = new boolean[rows][cols];
                    if (dfs(grid, word, r, c, 0, visited, rows, cols)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private static boolean dfs(char[][] grid, String word, int r, int c, int idx,
                              boolean[][] visited, int rows, int cols) {
        if (idx == word.length()) return true;
        if (r < 0 || r >= rows || c < 0 || c >= cols) return false;
        if (visited[r][c] || grid[r][c] != word.charAt(idx)) return false;
        
        visited[r][c] = true;
        for (int[] dir : DIRECTIONS) {
            if (dfs(grid, word, r + dir[0], c + dir[1], idx + 1, visited, rows, cols)) {
                return true;
            }
        }
        visited[r][c] = false;
        return false;
    }
    
    /**
     * Solves words in parallel using ExecutorService.
     * 
     * WORK PARTITIONING: Each word is assigned to a thread.
     * WHY: Words are independent - no shared state during search.
     * 
     * SYNCHRONIZATION: ConcurrentHashMap ensures thread-safe writes.
     * WHY: Multiple threads write results concurrently.
     */
    public static WordSearchSolver.SolverResult solveMultithreaded(
            char[][] grid, String[] words, int size, 
            ProgressTracker progressTracker) {
        
        long startTime = System.nanoTime();
        
        // Thread-safe result storage
        ConcurrentHashMap<String, Boolean> results = new ConcurrentHashMap<>();
        
        // Create thread pool: one thread per word (or limit to available processors)
        int numThreads = Math.min(words.length, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        // Submit each word search as a separate task
        List<Future<?>> futures = new ArrayList<>();
        for (String word : words) {
            Future<?> future = executor.submit(() -> {
                // Each thread searches for its assigned word
                boolean found = searchWord(grid, word, size, size);
                results.put(word, found);
                
                // Update progress (thread-safe)
                if (progressTracker != null) {
                    progressTracker.incrementProgress(word, found);
                }
            });
            futures.add(future);
        }
        
        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            try {
                future.get(); // Blocks until task completes
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        
        executor.shutdown();
        
        long endTime = System.nanoTime();
        double executionTime = (endTime - startTime) / 1_000_000.0;
        
        return new WordSearchSolver.SolverResult(
            new HashMap<>(results), 
            executionTime, 
            "multithreaded"
        );
    }
    
    /**
     * ProgressTracker: Thread-safe progress state.
     * WHY: Multiple threads update progress concurrently, requiring synchronization.
     */
    public static class ProgressTracker {
        private final int totalWords;
        private final ConcurrentHashMap<String, Boolean> foundWords;
        private volatile int completedCount;
        
        public ProgressTracker(int totalWords) {
            this.totalWords = totalWords;
            this.foundWords = new ConcurrentHashMap<>();
            this.completedCount = 0;
        }
        
        /**
         * Thread-safe progress update.
         * WHY: synchronized ensures atomic increment and map update.
         */
        public synchronized void incrementProgress(String word, boolean found) {
            foundWords.put(word, found);
            completedCount++;
        }
        
        public int getCompletedCount() { return completedCount; }
        public int getTotalWords() { return totalWords; }
        public Map<String, Boolean> getFoundWords() { return new HashMap<>(foundWords); }
        
        /**
         * Returns progress as percentage.
         */
        public double getProgressPercent() {
            if (totalWords == 0) return 100.0;
            return (completedCount * 100.0) / totalWords;
        }
        
        /**
         * JSON representation for HTTP responses.
         */
        public String toJSON() {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"completed\": ").append(completedCount).append(",\n");
            json.append("  \"total\": ").append(totalWords).append(",\n");
            json.append("  \"progressPercent\": ").append(String.format("%.1f", getProgressPercent())).append(",\n");
            json.append("  \"foundWords\": {\n");
            int count = 0;
            for (Map.Entry<String, Boolean> entry : foundWords.entrySet()) {
                json.append("    \"").append(entry.getKey()).append("\": ").append(entry.getValue());
                if (count < foundWords.size() - 1) json.append(",");
                json.append("\n");
                count++;
            }
            json.append("  }\n");
            json.append("}");
            return json.toString();
        }
    }
}


