/**
 * Level.java
 * 
 * Represents a word search puzzle level.
 * Contains grid dimensions, character grid, word list, and metadata.
 * 
 * WHY: Encapsulates all level data in one place for easy serialization
 * and management. This makes it clear where levels are stored (backend).
 */
public class Level {
    private int id;
    private String name;
    private int size; // NxN grid
    private char[][] grid;
    private String[] words;
    private String difficulty;
    
    public Level(int id, String name, int size, char[][] grid, String[] words, String difficulty) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.grid = grid;
        this.words = words;
        this.difficulty = difficulty;
    }
    
    // Getters
    public int getId() { return id; }
    public String getName() { return name; }
    public int getSize() { return size; }
    public char[][] getGrid() { return grid; }
    public String[] getWords() { return words; }
    public String getDifficulty() { return difficulty; }
    
    /**
     * Converts level to JSON format for HTTP responses.
     * WHY: Simple JSON serialization without external libraries.
     */
    public String toJSON() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"id\": ").append(id).append(",\n");
        json.append("  \"name\": \"").append(name).append("\",\n");
        json.append("  \"size\": ").append(size).append(",\n");
        json.append("  \"difficulty\": \"").append(difficulty).append("\",\n");
        json.append("  \"grid\": [\n");
        for (int i = 0; i < size; i++) {
            json.append("    \"");
            for (int j = 0; j < size; j++) {
                json.append(grid[i][j]);
            }
            json.append("\"");
            if (i < size - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ],\n");
        json.append("  \"words\": [\n");
        for (int i = 0; i < words.length; i++) {
            json.append("    \"").append(words[i]).append("\"");
            if (i < words.length - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}");
        return json.toString();
    }
}


