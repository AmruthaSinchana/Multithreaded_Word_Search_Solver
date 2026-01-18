import java.util.*;
import java.util.concurrent.*;

public class PuzzleEngine {
    private static final int[][] ALL_DIRECTIONS = {
        {0, 1},  // right (horizontal)
        {0, -1}, // left (horizontal)
        {1, 0},  // down (vertical)
        {-1, 0}  // up (vertical)
    };
    
    private final char[][] grid;
    private final int rows;
    private final int cols;
    private final ExecutorService executor;
    
    public PuzzleEngine(char[][] grid, int threadPoolSize) {
        this.grid = grid;
        this.rows = grid.length;
        this.cols = grid[0].length;
        this.executor = Executors.newFixedThreadPool(threadPoolSize);
    }
    
    private static WordPath searchWordPath(char[][] grid, String word, int rows, int cols) {
        word = word.toUpperCase();
        
        // Try each cell as a starting point
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char cellChar = Character.toUpperCase(grid[r][c]);
                char firstChar = word.charAt(0);
                
                if (cellChar == firstChar) {
                    // Try each of the 4 directions
                    for (int[] dir : ALL_DIRECTIONS) {
                        WordPath path = searchInDirection(grid, word, r, c, dir, rows, cols);
                        if (path != null) {
                            // Found it! Log for debugging
                            String dirName = getDirectionName(dir);
                            System.out.println("    Found '" + word + "' starting at [" + r + "," + c + "] going " + dirName);
                            return path;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    private static String getDirectionName(int[] dir) {
        if (dir[0] == 0 && dir[1] == 1) return "right";
        if (dir[0] == 0 && dir[1] == -1) return "left";
        if (dir[0] == 1 && dir[1] == 0) return "down";
        if (dir[0] == -1 && dir[1] == 0) return "up";
        return "unknown";
    }
    
    /**
     * Searches in a single fixed direction (horizontal or vertical only).
     * This ensures words follow a straight line.
     */
    private static WordPath searchInDirection(char[][] grid, String word, int startR, int startC,
                                              int[] dir, int rows, int cols) {
        List<int[]> path = new ArrayList<>();
        int r = startR;
        int c = startC;
        
        for (int i = 0; i < word.length(); i++) {
            // Check bounds
            if (r < 0 || r >= rows || c < 0 || c >= cols) {
                return null;
            }
            
            // Check character match (case insensitive)
            if (Character.toUpperCase(grid[r][c]) != Character.toUpperCase(word.charAt(i))) {
                return null;
            }
            
            path.add(new int[]{r, c});
            
            // Move to next cell in the SAME direction
            r += dir[0];
            c += dir[1];
        }
        
        return new WordPath(word, path);
    }
    
    /**
     * MULTITHREADED: Searches for multiple words in parallel.
     */
    public Map<String, WordPath> searchWordsParallel(String[] words, Set<String> allowedDirections) {
        Map<String, WordPath> results = new ConcurrentHashMap<>();
        List<Future<WordPath>> futures = new ArrayList<>();
        
        System.out.println("Searching for " + words.length + " words in parallel...");
        
        for (String word : words) {
            Future<WordPath> future = executor.submit(new Callable<WordPath>() {
                @Override
                public WordPath call() {
                    WordPath path = searchWordPath(grid, word, rows, cols);
                    if (path != null) {
                        System.out.println("  Found: " + word + " at path of length " + path.getPath().size());
                    } else {
                        System.out.println("  NOT FOUND: " + word);
                    }
                    return path;
                }
            });
            futures.add(future);
        }
        
        for (int i = 0; i < futures.size(); i++) {
            try {
                WordPath path = futures.get(i).get(2, TimeUnit.SECONDS);
                if (path != null) {
                    results.put(words[i].toUpperCase(), path);
                }
            } catch (Exception e) {
                System.err.println("  Error searching for " + words[i] + ": " + e.getMessage());
            }
        }
        
        System.out.println("Search complete. Found " + results.size() + " out of " + words.length + " words.");
        return results;
    }
    
    /**
     * MULTITHREADED: Validates a player-selected path.
     * 
     * Requirements:
     * 1. Cells must be adjacent
     * 2. Must move in ONE fixed direction (horizontal OR vertical)
     * 3. Word must exist in grid
     */
    public ValidationResult validatePath(List<int[]> playerPath, Set<String> allowedDirections) {
        if (playerPath.size() < 2) {
            return new ValidationResult(false, "", true);
        }
        
        // Build word from path
        StringBuilder wordBuilder = new StringBuilder();
        for (int[] cell : playerPath) {
            if (cell[0] >= 0 && cell[0] < rows && cell[1] >= 0 && cell[1] < cols) {
                wordBuilder.append(grid[cell[0]][cell[1]]);
            }
        }
        String word = wordBuilder.toString().toUpperCase();
        
        // Validate path structure
        if (!isValidPath(playerPath)) {
            return new ValidationResult(false, word, true);
        }
        
        // Check if word exists in grid using the same search logic
        Future<WordPath> future = executor.submit(() -> {
            return searchWordPath(grid, word, rows, cols);
        });
        
        try {
            WordPath found = future.get(500, TimeUnit.MILLISECONDS);
            if (found != null) {
                // Verify the player's path matches a valid path
                if (pathsMatch(playerPath, found.getPath())) {
                    return new ValidationResult(true, word, true);
                }
            }
        } catch (Exception e) {
            // Timeout or error
        }
        
        return new ValidationResult(false, word, true);
    }
    
    /**
     * Validates that a path is continuous, adjacent, and in ONE direction.
     */
    private boolean isValidPath(List<int[]> path) {
        if (path.size() < 2) return false;
        
        // Determine direction from first two cells
        int[] first = path.get(0);
        int[] second = path.get(1);
        int dr = second[0] - first[0];
        int dc = second[1] - first[1];
        
        // Must be adjacent (1 cell away in one direction)
        if (Math.abs(dr) + Math.abs(dc) != 1) {
            return false;
        }
        
        // Must be horizontal OR vertical (not diagonal)
        if (dr != 0 && dc != 0) {
            return false;
        }
        
        // Check all subsequent cells follow the same direction
        for (int i = 1; i < path.size(); i++) {
            int[] prev = path.get(i - 1);
            int[] curr = path.get(i);
            int currDr = curr[0] - prev[0];
            int currDc = curr[1] - prev[1];
            
            // Must maintain same direction
            if (currDr != dr || currDc != dc) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Checks if two paths represent the same cells.
     */
    private boolean pathsMatch(List<int[]> path1, List<int[]> path2) {
        if (path1.size() != path2.size()) return false;
        
        for (int i = 0; i < path1.size(); i++) {
            if (path1.get(i)[0] != path2.get(i)[0] || path1.get(i)[1] != path2.get(i)[1]) {
                return false;
            }
        }
        
        return true;
    }
    
    public void shutdown() {
        executor.shutdown();
    }
    
    public static class WordPath {
        private String word;
        private List<int[]> path;
        
        public WordPath(String word, List<int[]> path) {
            this.word = word;
            this.path = new ArrayList<>(path);
        }
        
        public String getWord() { return word; }
        public List<int[]> getPath() { return new ArrayList<>(path); }
        
        public String toJSON() {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"word\": \"").append(word).append("\",\n");
            json.append("  \"path\": [\n");
            for (int i = 0; i < path.size(); i++) {
                json.append("    [").append(path.get(i)[0]).append(", ").append(path.get(i)[1]).append("]");
                if (i < path.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ]\n");
            json.append("}");
            return json.toString();
        }
    }
    
    public static class ValidationResult {
        private boolean valid;
        private String word;
        private boolean isForward;
        
        public ValidationResult(boolean valid, String word, boolean isForward) {
            this.valid = valid;
            this.word = word;
            this.isForward = isForward;
        }
        
        public boolean isValid() { return valid; }
        public String getWord() { return word; }
        public boolean isForward() { return isForward; }
        
        public String toJSON() {
            return "{\n" +
                   "  \"valid\": " + valid + ",\n" +
                   "  \"word\": \"" + word + "\",\n" +
                   "  \"isForward\": " + isForward + "\n" +
                   "}";
        }
    }
}