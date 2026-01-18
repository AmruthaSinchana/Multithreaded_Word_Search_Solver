import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * SimpleHTTPServer.java
 * 
 * Plain Java HTTP server implementation.
 * 
 * WHY: No frameworks - demonstrates core Java networking and HTTP handling.
 * This is educational and shows exactly how HTTP works.
 */
public class SimpleHTTPServer {
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private boolean running;
    
    public SimpleHTTPServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        threadPool = Executors.newFixedThreadPool(10); // Handle 10 concurrent requests
        running = true;
    }
    
    /**
     * Starts the server and accepts connections.
     * WHY: Each request handled in separate thread for concurrency.
     */
    public void start() {
        System.out.println("Server started on http://localhost:8080");
        System.out.println("Open index.html in your browser");
        
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(new RequestHandler(clientSocket));
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    public void stop() {
        running = false;
        try {
            serverSocket.close();
            threadPool.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    private static class RequestHandler implements Runnable {
        private Socket clientSocket;
        
        public RequestHandler(Socket socket) {
            this.clientSocket = socket;
        }
        
        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream())
                );
                PrintWriter out = new PrintWriter(
                    clientSocket.getOutputStream(), true
                );
                
                // Parse HTTP request
                String requestLine = in.readLine();
                if (requestLine == null) return;
                
                String[] parts = requestLine.split(" ");
                if (parts.length < 2) return;
                
                String method = parts[0];
                String path = parts[1];
                
                // Read headers and POST body if present
                String line;
                String postBody = null;
                int contentLength = 0;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        try {
                            contentLength = Integer.parseInt(line.substring(15).trim());
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }
                }
                
                // Read POST body if present
                if (method.equals("POST") && contentLength > 0) {
                    char[] buffer = new char[contentLength];
                    int bytesRead = in.read(buffer, 0, contentLength);
                    if (bytesRead > 0) {
                        postBody = new String(buffer, 0, bytesRead);
                    }
                }
                
                // Handle CORS (for local development)
                String response = handleRequest(method, path, postBody);
                
                // Send HTTP response
                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: application/json");
                out.println("Access-Control-Allow-Origin: *");
                out.println("Access-Control-Allow-Methods: GET, POST, OPTIONS");
                out.println("Access-Control-Allow-Headers: Content-Type");
                out.println();
                out.println(response);
                
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        /**
         * Routes requests to appropriate handlers.
         * WHY: Simple routing without framework - clear and educational.
         */
        private String handleRequest(String method, String path, String postBody) {
            // Handle OPTIONS (CORS preflight)
            if (method.equals("OPTIONS")) {
                return "{}";
            }
            
            // Parse query parameters
            String[] pathParts = path.split("\\?");
            String endpoint = pathParts[0];
            String query = pathParts.length > 1 ? pathParts[1] : "";
            
            try {
                // Route to appropriate handler
                if (endpoint.equals("/levels")) {
                    return LevelRepository.getInstance().getAllLevelsJSON();
                }
                else if (endpoint.equals("/level")) {
                    int id = extractId(query);
                    Level level = LevelRepository.getInstance().getLevel(id);
                    if (level != null) {
                        return level.toJSON();
                    }
                    return "{\"error\": \"Level not found\"}";
                }
                else if (endpoint.equals("/startSolve") && method.equals("POST")) {
                    int id = extractId(query);
                    String solverType = extractParam(query, "type");
                    boolean useMultithreaded = "multithreaded".equals(solverType);
                    SolverService.getInstance().startSolve(id, useMultithreaded);
                    return "{\"status\": \"started\", \"levelId\": " + id + "}";
                }
                else if (endpoint.equals("/progress")) {
                    int id = extractId(query);
                    return SolverService.getInstance().getProgress(id);
                }
                else if (endpoint.equals("/results")) {
                    int id = extractId(query);
                    return SolverService.getInstance().getResults(id);
                }
                // Game endpoints
                else if (endpoint.equals("/startGame") && method.equals("POST")) {
                    int id = extractId(query);
                    GameService.getInstance().startGame(id);
                    return "{\"status\": \"game_started\", \"levelId\": " + id + "}";
                }
                else if (endpoint.equals("/validatePath") && method.equals("POST")) {
                    int id = extractId(query);
                    // Try to get path from POST body first, then query param
                    String pathJson = postBody != null && !postBody.isEmpty() ? 
                        postBody : extractParam(query, "path");
                    return GameService.getInstance().validatePath(id, pathJson);
                }
                else if (endpoint.equals("/hint")) {
                    int id = extractId(query);
                    return GameService.getInstance().generateHint(id);
                }
                else if (endpoint.equals("/startDemoSolver") && method.equals("POST")) {
                    int id = extractId(query);
                    return GameService.getInstance().startDemoSolver(id);
                }
                else if (endpoint.equals("/demoSolverResults")) {
                    int id = extractId(query);
                    return GameService.getInstance().getDemoSolverResults(id);
                }
                else if (endpoint.equals("/score")) {
                    int id = extractId(query);
                    return GameService.getInstance().getScore(id);
                }
                else if (endpoint.equals("/gameState")) {
                    int id = extractId(query);
                    return GameService.getInstance().getGameState(id);
                }
                else {
                    return "{\"error\": \"Unknown endpoint\"}";
                }
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }
        
        private int extractId(String query) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("id=")) {
                    return Integer.parseInt(param.substring(3));
                }
            }
            return -1;
        }
        
        private String extractParam(String query, String name) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith(name + "=")) {
                    return param.substring(name.length() + 1);
                }
            }
            return "";
        }
    }
}

