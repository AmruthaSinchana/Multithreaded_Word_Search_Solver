import java.util.*;

/**
 * LevelConfig.java
 * 
 * Configuration for level difficulty and game rules.
 * Controls grid size, allowed directions, word overlap, and thread pool settings.
 * 
 * WHY: Separates level configuration from level data, allowing flexible
 * difficulty scaling and game rule customization.
 */
public class LevelConfig {
    private int levelId;
    private int gridSize;
    private Set<String> allowedDirections; // "horizontal", "vertical", "diagonal", "reverse"
    private int wordOverlapFrequency; // 0-100, how often words can overlap
    private int threadPoolSize; // Number of threads for this level
    private int maxHints; // Maximum hints allowed per level
    private Map<String, Integer> scoringRules; // Scoring rules for this level
    
    public LevelConfig(int levelId, int gridSize) {
        this.levelId = levelId;
        this.gridSize = gridSize;
        this.allowedDirections = new HashSet<>();
        // Only horizontal, vertical, and diagonal - NO reverse
        this.allowedDirections.addAll(Arrays.asList("horizontal", "vertical", "diagonal"));
        this.wordOverlapFrequency = 50;
        this.threadPoolSize = Math.min(Runtime.getRuntime().availableProcessors(), 4);
        this.maxHints = 3;
        this.scoringRules = new HashMap<>();
        initializeDefaultScoring();
    }
    
    private void initializeDefaultScoring() {
        scoringRules.put("correct_word", 10);
        scoringRules.put("diagonal_word", 15);
        scoringRules.put("hint_used", -5);
        scoringRules.put("incorrect_attempt", -2);
    }
    
    // Getters
    public int getLevelId() { return levelId; }
    public int getGridSize() { return gridSize; }
    public Set<String> getAllowedDirections() { return new HashSet<>(allowedDirections); }
    public int getWordOverlapFrequency() { return wordOverlapFrequency; }
    public int getThreadPoolSize() { return threadPoolSize; }
    public int getMaxHints() { return maxHints; }
    public Map<String, Integer> getScoringRules() { return new HashMap<>(scoringRules); }
    
    // Setters for configuration
    public void setAllowedDirections(Set<String> directions) {
        this.allowedDirections = new HashSet<>(directions);
    }
    
    public void setWordOverlapFrequency(int frequency) {
        this.wordOverlapFrequency = Math.max(0, Math.min(100, frequency));
    }
    
    public void setThreadPoolSize(int size) {
        this.threadPoolSize = Math.max(1, size);
    }
    
    public void setMaxHints(int maxHints) {
        this.maxHints = Math.max(0, maxHints);
    }
    
    public void setScoringRule(String rule, int points) {
        scoringRules.put(rule, points);
    }
    
    /**
     * Converts to JSON for HTTP responses.
     */
    public String toJSON() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"levelId\": ").append(levelId).append(",\n");
        json.append("  \"gridSize\": ").append(gridSize).append(",\n");
        json.append("  \"threadPoolSize\": ").append(threadPoolSize).append(",\n");
        json.append("  \"maxHints\": ").append(maxHints).append(",\n");
        json.append("  \"allowedDirections\": [\n");
        int count = 0;
        for (String dir : allowedDirections) {
            json.append("    \"").append(dir).append("\"");
            if (count < allowedDirections.size() - 1) json.append(",");
            json.append("\n");
            count++;
        }
        json.append("  ]\n");
        json.append("}");
        return json.toString();
    }
}

