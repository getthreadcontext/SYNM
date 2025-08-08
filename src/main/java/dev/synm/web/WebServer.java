package dev.synm.web;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import dev.synm.player.PlayerManager;

public class WebServer {
    private int port;
    private final PlayerManager playerManager;
    private final MinecraftServer server;
    private HttpServer httpServer;
    // Simple API key auth
    private String authKey;
    private final java.nio.file.Path authKeyFile = java.nio.file.Paths.get("synm_api_key.txt");
    
    public WebServer(int port, PlayerManager playerManager, MinecraftServer server) {
        this.port = port;
        this.playerManager = playerManager;
        this.server = server;
    }
    
    public void start() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Load existing auth key if present
            loadAuthKey();

            // Static content
            httpServer.createContext("/", new StaticHandler());
            
            // API endpoints
            httpServer.createContext("/api/players", new PlayersHandler());
            httpServer.createContext("/api/player/", new PlayerHandler());
            httpServer.createContext("/api/action/", new ActionHandler());
            // Auth endpoints
            httpServer.createContext("/api/auth/status", new AuthStatusHandler());
            httpServer.createContext("/api/auth/init", new AuthInitHandler());
            
            httpServer.setExecutor(Executors.newFixedThreadPool(10));
            httpServer.start();
            
            dev.synm.SynM.LOGGER.info("SynM web server started on port {} - Access at http://localhost:{}", port, port);
            
            // Show additional info for different server types
            if (server.isDedicated()) {
                dev.synm.SynM.LOGGER.info("Running on dedicated server");
                dev.synm.SynM.LOGGER.info("Web portal available at: http://your-server-ip:{}", port);
            } else {
                dev.synm.SynM.LOGGER.info("Running on integrated server (local/LAN world)");
                dev.synm.SynM.LOGGER.info("Web portal available at: http://localhost:{}", port);
            }
            
        } catch (IOException e) {
            dev.synm.SynM.LOGGER.error("Failed to start web server on port {}. Port might be in use.", port, e);
            
            // Try alternative port if default fails
            if (port == 4444) {
                dev.synm.SynM.LOGGER.info("Trying alternative port 4445...");
                try {
                    httpServer = HttpServer.create(new InetSocketAddress(4445), 0);
                    this.port = 4445; // Update port number
                    
                    // Re-setup contexts
                    httpServer.createContext("/", new StaticHandler());
                    httpServer.createContext("/api/players", new PlayersHandler());
                    httpServer.createContext("/api/player/", new PlayerHandler());
                    httpServer.createContext("/api/action/", new ActionHandler());
                    // Auth endpoints on fallback too
                    httpServer.createContext("/api/auth/status", new AuthStatusHandler());
                    httpServer.createContext("/api/auth/init", new AuthInitHandler());
                    
                    httpServer.setExecutor(Executors.newFixedThreadPool(10));
                    httpServer.start();
                    
                    dev.synm.SynM.LOGGER.info("SynM web server started on alternative port {} - Access at http://localhost:{}", port, port);
                } catch (IOException e2) {
                    dev.synm.SynM.LOGGER.error("Failed to start web server on alternative port 4445", e2);
                }
            }
        }
    }
    
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            dev.synm.SynM.LOGGER.info("Web server stopped");
        }
    }
    
    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            byte[] data = getWebAsset(path);
            if (data == null) {
                // SPA fallback to index
                data = getWebAsset("/index.html");
                if (data == null) {
                    data = getMainPage().getBytes("UTF-8");
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                } else {
                    exchange.getResponseHeaders().set("Content-Type", contentTypeFor(".html"));
                }
            } else {
                exchange.getResponseHeaders().set("Content-Type", contentTypeFor(path));
            }
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
    }
    
    private class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                if (!isAuthorized(exchange)) { sendUnauthorized(exchange); return; }
                String response = playerManager.getPlayersJson();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes("UTF-8"));
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    
    private class PlayerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                if (!isAuthorized(exchange)) { sendUnauthorized(exchange); return; }
                String path = exchange.getRequestURI().getPath();
                String uuidStr = path.substring("/api/player/".length());
                
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String response = playerManager.getPlayerJson(uuid);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes("UTF-8"));
                    }
                } catch (IllegalArgumentException e) {
                    exchange.sendResponseHeaders(400, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    
    private class ActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                if (!isAuthorized(exchange)) { sendUnauthorized(exchange); return; }
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                
                if (parts.length >= 4) {
                    String action = parts[3];
                    
                    // Read request body
                    Map<String, String> params = parseFormData(exchange);
                    String uuidStr = params.get("uuid");
                    
                    if (uuidStr != null) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            boolean success = handleAction(action, uuid, params);
                            
                            String response = success ? "{\"success\": true}" : "{\"success\": false}";
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                            exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write(response.getBytes("UTF-8"));
                            }
                        } catch (IllegalArgumentException e) {
                            exchange.sendResponseHeaders(400, -1);
                        }
                    } else {
                        exchange.sendResponseHeaders(400, -1);
                    }
                } else {
                    exchange.sendResponseHeaders(400, -1);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
        
        private Map<String, String> parseFormData(HttpExchange exchange) throws IOException {
            Map<String, String> params = new HashMap<>();
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), "UTF-8");
                String[] pairs = body.split("&");
                for (String pair : pairs) {
                    if (pair.isEmpty()) continue;
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        try {
                            String key = java.net.URLDecoder.decode(keyValue[0], java.nio.charset.StandardCharsets.UTF_8);
                            String value = java.net.URLDecoder.decode(keyValue[1], java.nio.charset.StandardCharsets.UTF_8);
                            params.put(key, value);
                        } catch (Exception ignored) {
                            params.put(keyValue[0], keyValue[1]);
                        }
                    }
                }
            }
            return params;
        }
        
        private boolean handleAction(String action, UUID uuid, Map<String, String> params) {
            server.execute(() -> {
                switch (action) {
                    case "heal":
                        playerManager.healPlayer(uuid);
                        break;
                    case "feed":
                        playerManager.feedPlayer(uuid);
                        break;
                    case "creative":
                        playerManager.setPlayerGameMode(uuid, GameMode.CREATIVE);
                        break;
                    case "survival":
                        playerManager.setPlayerGameMode(uuid, GameMode.SURVIVAL);
                        break;
                    case "adventure":
                        playerManager.setPlayerGameMode(uuid, GameMode.ADVENTURE);
                        break;
                    case "spectator":
                        playerManager.setPlayerGameMode(uuid, GameMode.SPECTATOR);
                        break;
                    case "setgamemode":
                        String gameMode = params.getOrDefault("gamemode", "survival");
                        playerManager.setPlayerGameModeByName(uuid, gameMode);
                        break;
                    case "kill":
                        playerManager.killPlayer(uuid);
                        break;
                    case "removehunger":
                        playerManager.removeHunger(uuid);
                        break;
                    case "clearinventory":
                        playerManager.clearInventory(uuid);
                        break;
                    case "teleport":
                        try {
                            double x = Double.parseDouble(params.getOrDefault("x", "0"));
                            double y = Double.parseDouble(params.getOrDefault("y", "64"));
                            double z = Double.parseDouble(params.getOrDefault("z", "0"));
                            playerManager.teleportPlayer(uuid, x, y, z);
                        } catch (NumberFormatException e) {
                            dev.synm.SynM.LOGGER.error("Invalid teleport coordinates", e);
                        }
                        break;
                    case "kick":
                        String reason = params.getOrDefault("reason", "Kicked by admin");
                        playerManager.kickPlayer(uuid, reason);
                        break;
                    case "freeze":
                        boolean freeze = Boolean.parseBoolean(params.getOrDefault("freeze", "true"));
                        playerManager.freezePlayer(uuid, freeze);
                        break;
                    case "godmode":
                        playerManager.toggleGodMode(uuid);
                        break;
                    case "fly":
                        playerManager.toggleFlyMode(uuid);
                        break;
                    case "vanish":
                        playerManager.toggleVanishMode(uuid);
                        break;
                    case "setnote":
                        String note = params.getOrDefault("note", "");
                        playerManager.setPlayerNote(uuid, note);
                        break;
                    case "effect":
                        String effect = params.getOrDefault("effect", "speed");
                        int duration = 60;
                        int amplifier = 0;
                        try { duration = Integer.parseInt(params.getOrDefault("duration", "60")); } catch (Exception ignored) {}
                        try { amplifier = Integer.parseInt(params.getOrDefault("amplifier", "0")); } catch (Exception ignored) {}
                        playerManager.applyEffect(uuid, effect, duration, amplifier);
                        break;
                    case "cleareffects":
                        playerManager.clearEffects(uuid);
                        break;
                    case "broadcast":
                        String msg = params.getOrDefault("message", "");
                        server.execute(() -> playerManager.broadcast(java.net.URLDecoder.decode(msg, java.nio.charset.StandardCharsets.UTF_8)));
                        break;
                    case "ban":
                        // Prefer player name if online; otherwise use UUID (some commands accept UUID)
                        String banReason = params.getOrDefault("reason", "Banned by admin");
                        String name = null;
                        var info = playerManager.getPlayer(uuid);
                        if (info != null && info.getPlayer() != null) {
                            name = info.getPlayer().getGameProfile().getName();
                        }
                        String target = name != null ? name : uuid.toString();
                        server.getCommandManager().executeWithPrefix(server.getCommandSource(),
                            String.format("ban %s %s", target, java.net.URLDecoder.decode(banReason, java.nio.charset.StandardCharsets.UTF_8)));
                        break;
                    case "unban":
                        String unbanTarget = uuid.toString();
                        var i2 = playerManager.getPlayer(uuid);
                        if (i2 != null && i2.getPlayer() != null) {
                            unbanTarget = i2.getPlayer().getGameProfile().getName();
                        }
                        server.getCommandManager().executeWithPrefix(server.getCommandSource(),
                            String.format("pardon %s", unbanTarget));
                        break;
                }
            });
            return true;
        }
    }

    // Auth: status endpoint
    private class AuthStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); return; }
            String json = "{\"initialized\": " + (isAuthInitialized() ? "true" : "false") + "}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes("UTF-8").length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(json.getBytes("UTF-8")); }
        }
    }

    // Auth: init endpoint (generates key if not present OR accepts user-provided key)
    private class AuthInitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); return; }
            if (isAuthInitialized()) { exchange.sendResponseHeaders(409, -1); return; }
            // Try to read a user-provided key from body (x-www-form-urlencoded: key=...)
            String provided = null;
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (!body.isEmpty()) {
                    for (String pair : body.split("&")) {
                        if (pair == null || pair.isEmpty()) continue;
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2 && java.net.URLDecoder.decode(kv[0], java.nio.charset.StandardCharsets.UTF_8).equals("key")) {
                            provided = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8).trim();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}

            String key;
            if (provided != null && !provided.isEmpty()) {
                // Basic validation: at least 8 chars
                if (provided.length() < 8) {
                    String err = "{\"error\":\"key_too_short\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(400, err.getBytes("UTF-8").length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(err.getBytes("UTF-8")); }
                    return;
                }
                key = setAndStoreKey(provided);
            } else {
                key = generateAndStoreKey();
            }
            String json = "{\"key\": \"" + key + "\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes("UTF-8").length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(json.getBytes("UTF-8")); }
        }
    }

    // ===== Auth helpers =====
    private void loadAuthKey() {
        try {
            if (java.nio.file.Files.exists(authKeyFile)) {
                authKey = java.nio.file.Files.readString(authKeyFile).trim();
                if (authKey.isEmpty()) authKey = null;
            }
        } catch (Exception e) {
            dev.synm.SynM.LOGGER.error("Failed to read auth key file", e);
        }
    }

    private String generateAndStoreKey() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        String key = bytesToHex(bytes);
        try {
            java.nio.file.Files.writeString(authKeyFile, key);
            authKey = key;
            dev.synm.SynM.LOGGER.info("Generated SynM API key at {}", authKeyFile.toAbsolutePath());
        } catch (Exception e) {
            dev.synm.SynM.LOGGER.error("Failed to write auth key file", e);
        }
        return key;
    }

    private String setAndStoreKey(String key) {
        try {
            java.nio.file.Files.writeString(authKeyFile, key);
            authKey = key;
            dev.synm.SynM.LOGGER.info("Stored user-provided SynM API key at {}", authKeyFile.toAbsolutePath());
        } catch (Exception e) {
            dev.synm.SynM.LOGGER.error("Failed to write auth key file", e);
        }
        return key;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private boolean isAuthInitialized() { return authKey != null && !authKey.isEmpty(); }

    private boolean isAuthorized(HttpExchange exchange) {
        // If key not initialized yet, allow API access so UI can initialize
        if (!isAuthInitialized()) return true;
        // Header check
        String headerKey = exchange.getRequestHeaders().getFirst("X-Auth-Key");
        if (headerKey != null && headerKey.equals(authKey)) return true;
        // Cookie check
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) {
            String[] parts = cookie.split(";\s*");
            for (String p : parts) {
                if (p.startsWith("synm_key=")) {
                    String val = p.substring("synm_key=".length());
                    if (val.equals(authKey)) return true;
                }
            }
        }
        return false;
    }

    private void sendUnauthorized(HttpExchange exchange) throws IOException {
        String json = "{\"error\":\"unauthorized\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(401, json.getBytes("UTF-8").length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(json.getBytes("UTF-8")); }
    }
    
    private String getMainPage() {
        try (InputStream is = getClass().getResourceAsStream("/index.html")) {
            if (is != null) {
                return new String(is.readAllBytes(), "UTF-8");
            }
        } catch (IOException e) {
            dev.synm.SynM.LOGGER.error("Failed to load index.html", e);
        }
        
        // Fallback basic HTML
        return "<!DOCTYPE html><html><head><title>SynM</title></head><body>" +
               "<h1>SynM Server Management Portal</h1>" +
               "<p>Error loading interface. Check server logs.</p>" +
               "</body></html>";
    }

    private byte[] getWebAsset(String path) {
    // Single-file build outputs to resources root (index.html)
    String normalized = path.startsWith("/") ? path : "/" + path;
        try (InputStream is = getClass().getResourceAsStream(normalized)) {
            if (is != null) return is.readAllBytes();
        } catch (IOException ignored) {}
        return null;
    }

    private String contentTypeFor(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html")) return "text/html; charset=UTF-8";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
}
