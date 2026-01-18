import java.util.*;

/**
 * WordSearchSolver.java
 * 
 * Single-threaded baseline word search solver.
 * 
 * WHY: Provides baseline performance for comparison with multithreaded version.
 * This demonstrates the improvement from parallelization.
 */
public class WordSearchSolver {
    
    // All 8 directions: up, down, left, right, and 4 diagonals
    private static final int[][] DIRECTIONS = {
    {-1, 0}, // vertical
    {1, 0},
    {0, -1}, // horizontal
    {0, 1}
};

    
    /**
     * Searches for a word in the grid using DFS.
     * WHY: Depth-first search is efficient for word search puzzles.
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
    
    /**
     * Depth-first search helper.
     * WHY: Recursive DFS explores all paths from a starting position.
     */
    private static boolean dfs(char[][] grid, String word, int r, int c, int idx,
                              boolean[][] visited, int rows, int cols) {
        // Base case: found entire word
        if (idx == word.length()) {
            return true;
        }
        
        // Boundary check
        if (r < 0 || r >= rows || c < 0 || c >= cols) {
            return false;
        }
        
        // Already visited or character mismatch
        if (visited[r][c] || grid[r][c] != word.charAt(idx)) {
            return false;
        }
        
        // Mark as visited
        visited[r][c] = true;
        
        // Try all 8 directions
        for (int[] dir : DIRECTIONS) {
            if (dfs(grid, word, r + dir[0], c + dir[1], idx + 1, visited, rows, cols)) {
                return true;
            }
        }
        
        // Backtrack
        visited[r][c] = false;
        return false;
    }
    
    /**
     * Solves all words sequentially (single-threaded).
     * WHY: This is the baseline - processes words one at a time.
     */
    public static SolverResult solveSingleThreaded(char[][] grid, String[] words, int size) {
        long startTime = System.nanoTime();
        Map<String, Boolean> results = new HashMap<>();
        
        for (String word : words) {
            boolean found = searchWord(grid, word, size, size);
            results.put(word, found);
        }
        
        long endTime = System.nanoTime();
        double executionTime = (endTime - startTime) / 1_000_000.0; // Convert to milliseconds
        
        return new SolverResult(results, executionTime, "single-threaded");
    }
    
    /**
     * Result container for solver output.
     */
    public static class SolverResult {
        private Map<String, Boolean> wordResults;
        private double executionTimeMs;
        private String solverType;
        
        public SolverResult(Map<String, Boolean> wordResults, double executionTimeMs, String solverType) {
            this.wordResults = wordResults;
            this.executionTimeMs = executionTimeMs;
            this.solverType = solverType;
        }
        
        public Map<String, Boolean> getWordResults() { return wordResults; }
        public double getExecutionTimeMs() { return executionTimeMs; }
        public String getSolverType() { return solverType; }
        
        public String toJSON() {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"solverType\": \"").append(solverType).append("\",\n");
            json.append("  \"executionTimeMs\": ").append(String.format("%.2f", executionTimeMs)).append(",\n");
            json.append("  \"results\": {\n");
            int count = 0;
            for (Map.Entry<String, Boolean> entry : wordResults.entrySet()) {
                json.append("    \"").append(entry.getKey()).append("\": ").append(entry.getValue());
                if (count < wordResults.size() - 1) json.append(",");
                json.append("\n");
                count++;
            }
            json.append("  }\n");
            json.append("}");
            return json.toString();
        }
    }
}

