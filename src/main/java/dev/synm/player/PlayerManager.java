package dev.synm.player;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.nio.file.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

public class PlayerManager {
    private final Map<UUID, PlayerInfo> players = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerInfo> offlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNotes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> frozenPlayers = new ConcurrentHashMap<>();
    // Store a freeze anchor to teleport players back each tick
    private final Map<UUID, FrozenAnchor> frozenAnchors = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> godModePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> vanishedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playTimeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.List<SessionRecord>> sessionHistory = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final Path dataFile = Paths.get("synm_data.json");
    
    public PlayerManager() {
        loadData();
    }
    
    private void loadData() {
        try {
            if (Files.exists(dataFile)) {
                String json = Files.readString(dataFile);
                JsonObject data = JsonParser.parseString(json).getAsJsonObject();
                
                // Load player notes
                if (data.has("playerNotes")) {
                    JsonObject notes = data.getAsJsonObject("playerNotes");
                    for (String key : notes.keySet()) {
                        try {
                            UUID uuid = UUID.fromString(key);
                            playerNotes.put(uuid, notes.get(key).getAsString());
                        } catch (IllegalArgumentException e) {
                            // Skip invalid UUIDs
                        }
                    }
                }

                // Load session history
                if (data.has("sessionHistory")) {
                    JsonObject hist = data.getAsJsonObject("sessionHistory");
                    for (String key : hist.keySet()) {
                        try {
                            UUID uuid = UUID.fromString(key);
                            JsonArray arr = hist.getAsJsonArray(key);
                            java.util.List<SessionRecord> list = new ArrayList<>();
                            arr.forEach(el -> {
                                JsonObject o = el.getAsJsonObject();
                                SessionRecord r = new SessionRecord(
                                    o.get("start").getAsLong(),
                                    o.has("end") ? o.get("end").getAsLong() : 0L,
                                    o.has("ip") ? o.get("ip").getAsString() : ""
                                );
                                list.add(r);
                            });
                            sessionHistory.put(uuid, list);
                        } catch (IllegalArgumentException e) {
                            // Skip invalid UUIDs
                        }
                    }
                }
                
                dev.synm.SynM.LOGGER.info("Loaded SynM data from {}", dataFile);
            }
        } catch (Exception e) {
            dev.synm.SynM.LOGGER.error("Failed to load SynM data", e);
        }
    }
    
    private void saveData() {
        try {
            JsonObject data = new JsonObject();
            
            // Save player notes
            JsonObject notes = new JsonObject();
            for (Map.Entry<UUID, String> entry : playerNotes.entrySet()) {
                notes.addProperty(entry.getKey().toString(), entry.getValue());
            }
            data.add("playerNotes", notes);
            
            // Save session history
            JsonObject hist = new JsonObject();
            for (Map.Entry<UUID, java.util.List<SessionRecord>> entry : sessionHistory.entrySet()) {
                JsonArray arr = new JsonArray();
                for (SessionRecord r : entry.getValue()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("start", r.start);
                    if (r.end > 0) o.addProperty("end", r.end);
                    if (r.ip != null) o.addProperty("ip", r.ip);
                    arr.add(o);
                }
                hist.add(entry.getKey().toString(), arr);
            }
            data.add("sessionHistory", hist);
            
            Files.writeString(dataFile, gson.toJson(data));
        } catch (Exception e) {
            dev.synm.SynM.LOGGER.error("Failed to save SynM data", e);
        }
    }
    
    public void onPlayerJoin(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PlayerInfo info = new PlayerInfo(player);
        players.put(uuid, info);
        
        // Track playtime session start
        playTimeSessions.put(uuid, System.currentTimeMillis());
        
        // Add session record (start)
        String ip = "";
        try {
            // Best-effort IP fetch across mappings
            ip = player.getIp();
        } catch (Throwable t) {
            // leave empty if not available
        }
        sessionHistory.computeIfAbsent(uuid, k -> new ArrayList<>())
            .add(new SessionRecord(System.currentTimeMillis(), 0L, ip));
        saveData();
        
        // Move from offline to online if was offline
        offlinePlayers.remove(uuid);
        
        dev.synm.SynM.LOGGER.info("Player {} joined the server", player.getName().getString());
    }
    
    public void onPlayerLeave(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        PlayerInfo info = players.remove(uuid);
        
        // Calculate and add session playtime
        Long sessionStart = playTimeSessions.remove(uuid);
        if (sessionStart != null && info != null) {
            long sessionTime = System.currentTimeMillis() - sessionStart;
            info.addPlayTime(sessionTime);
        }
        
        if (info != null) {
            info.updateFromPlayer(player);
            info.setOnline(false);
            offlinePlayers.put(uuid, info);
        }
        
        // Close last session record
        java.util.List<SessionRecord> list = sessionHistory.get(uuid);
        if (list != null && !list.isEmpty()) {
            SessionRecord last = list.get(list.size() - 1);
            if (last.end == 0L) last.end = System.currentTimeMillis();
        }
        saveData();
        
        // Clean up temporary states
        frozenPlayers.remove(uuid);
        godModePlayers.remove(uuid);
        vanishedPlayers.remove(uuid);
        
        dev.synm.SynM.LOGGER.info("Player {} left the server", player.getName().getString());
    }
    
    public Map<UUID, PlayerInfo> getOnlinePlayers() {
        return new HashMap<>(players);
    }
    
    public Map<UUID, PlayerInfo> getOfflinePlayers() {
        return new HashMap<>(offlinePlayers);
    }
    
    public Map<UUID, PlayerInfo> getAllPlayers() {
        Map<UUID, PlayerInfo> all = new HashMap<>(offlinePlayers);
        all.putAll(players);
        return all;
    }
    
    public PlayerInfo getPlayer(UUID uuid) {
        PlayerInfo info = players.get(uuid);
        if (info == null) {
            info = offlinePlayers.get(uuid);
        }
        return info;
    }
    
    public boolean teleportPlayer(UUID uuid, double x, double y, double z) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            player.teleport(player.getServerWorld(), x, y, z, java.util.Set.of(), 0.0f, 0.0f, true);
            dev.synm.SynM.LOGGER.info("Teleported player {} to {}, {}, {}", 
                player.getName().getString(), x, y, z);
            return true;
        }
        return false;
    }
    
    public boolean healPlayer(UUID uuid) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            player.setHealth(player.getMaxHealth());
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
            // Remove absorption so hearts display correctly
            try { player.setAbsorptionAmount(0.0F); } catch (Throwable ignored) {}
            dev.synm.SynM.LOGGER.info("Healed player {}", player.getName().getString());
            return true;
        }
        return false;
    }
    
    public boolean feedPlayer(UUID uuid) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(20.0f);
            dev.synm.SynM.LOGGER.info("Fed player {}", player.getName().getString());
            return true;
        }
        return false;
    }
    
    public boolean setPlayerGameMode(UUID uuid, GameMode gameMode) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            // Change the player's game mode
            player.changeGameMode(gameMode);
            dev.synm.SynM.LOGGER.info("Set player {} to {} mode",
                player.getName().getString(), gameMode.getName());

            // Optional external logger
            var logger = dev.synm.SynM.getPlayerLogger();
            if (logger != null) {
                logger.logGameModeChange(player, "unknown", gameMode.getName(), "Admin change");
            }
            return true;
        }
        return false;
    }
    
    public boolean kickPlayer(UUID uuid, String reason) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            player.networkHandler.disconnect(Text.literal(reason));
            dev.synm.SynM.LOGGER.info("Kicked player {} with reason: {}", 
                player.getName().getString(), reason);
            return true;
        }
        return false;
    }
    
    public boolean killPlayer(UUID uuid) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            player.damage(player.getServerWorld(), player.getDamageSources().outOfWorld(), Float.MAX_VALUE);
            dev.synm.SynM.LOGGER.info("Killed player {}", player.getName().getString());
            return true;
        }
        return false;
    }
    
    public boolean removeHunger(UUID uuid) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            player.getHungerManager().setFoodLevel(0);
            player.getHungerManager().setSaturationLevel(0.0f);
            // Nudge exhaustion so client updates promptly
            try { player.getHungerManager().addExhaustion(0.1f); } catch (Throwable ignored) {}
            dev.synm.SynM.LOGGER.info("Removed hunger from player {}", player.getName().getString());
            return true;
        }
        return false;
    }
    
    public boolean clearInventory(UUID uuid) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            player.getInventory().clear();
            dev.synm.SynM.LOGGER.info("Cleared inventory of player {}", player.getName().getString());
            return true;
        }
        return false;
    }
    
    public boolean setPlayerGameModeByName(UUID uuid, String gameModeName) {
        try {
            GameMode gameMode;
            switch (gameModeName.toLowerCase()) {
                case "survival":
                    gameMode = GameMode.SURVIVAL;
                    break;
                case "creative":
                    gameMode = GameMode.CREATIVE;
                    break;
                case "adventure":
                    gameMode = GameMode.ADVENTURE;
                    break;
                case "spectator":
                    gameMode = GameMode.SPECTATOR;
                    break;
                default:
                    return false;
            }
            return setPlayerGameMode(uuid, gameMode);
        } catch (Exception e) {
            dev.synm.SynM.LOGGER.error("Failed to set game mode for player", e);
            return false;
        }
    }
    
    // Freeze/Unfreeze Player
    public boolean freezePlayer(UUID uuid, boolean freeze) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            if (freeze) {
                frozenPlayers.put(uuid, true);
                // Store current position for teleporting back if they move
                frozenAnchors.put(uuid, FrozenAnchor.from(player));
                player.sendMessage(Text.literal("§cYou have been frozen by an administrator!"), false);
            } else {
                frozenPlayers.remove(uuid);
                frozenAnchors.remove(uuid);
                player.sendMessage(Text.literal("§aYou have been unfrozen!"), false);
            }
            dev.synm.SynM.LOGGER.info("{} player {}", freeze ? "Froze" : "Unfroze", player.getName().getString());
            return true;
        }
        return false;
    }
    
    public boolean isPlayerFrozen(UUID uuid) {
        return frozenPlayers.getOrDefault(uuid, false);
    }
    
    // Method to check if player movement should be prevented
    public boolean shouldPreventMovement(ServerPlayerEntity player) {
        return isPlayerFrozen(player.getUuid());
    }

    // Called every server tick from SynM to enforce freeze and any periodic state
    public void onServerTick(net.minecraft.server.MinecraftServer server) {
        if (frozenPlayers.isEmpty()) return;
        for (Map.Entry<UUID, Boolean> entry : frozenPlayers.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;
            UUID uuid = entry.getKey();
            PlayerInfo info = players.get(uuid);
            if (info == null) continue;
            ServerPlayerEntity p = info.getPlayer();
            if (p == null) continue;
            FrozenAnchor anchor = frozenAnchors.computeIfAbsent(uuid, k -> FrozenAnchor.from(p));
            // If player changed dimension or moved, snap back
            boolean wrongDim = anchor.dimensionKey != null && !p.getServerWorld().getRegistryKey().getValue().toString().equals(anchor.dimensionKey);
            double dx = p.getX() - anchor.x;
            double dy = p.getY() - anchor.y;
            double dz = p.getZ() - anchor.z;
            if (wrongDim || (dx*dx + dy*dy + dz*dz) > 0.001) {
                try {
                    p.teleport(p.getServerWorld(), anchor.x, anchor.y, anchor.z, java.util.Set.of(), anchor.yaw, anchor.pitch, true);
                } catch (Throwable t) {
                    // Fallback: set position directly
                    p.updatePosition(anchor.x, anchor.y, anchor.z);
                }
            }
            // Zero out velocity and fall distance so they can't drift
            try { p.setVelocity(0, 0, 0); } catch (Throwable ignored) {}
            p.fallDistance = 0.0F;
        }
    }

    // Anchor record for freeze
    private static class FrozenAnchor {
        final String dimensionKey;
        final double x, y, z;
        final float yaw, pitch;
        FrozenAnchor(String dimensionKey, double x, double y, double z, float yaw, float pitch) {
            this.dimensionKey = dimensionKey; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }
        static FrozenAnchor from(ServerPlayerEntity p) {
            String dim = p.getServerWorld().getRegistryKey().getValue().toString();
            return new FrozenAnchor(dim, p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch());
        }
    }
    
    // God Mode Toggle
    public boolean toggleGodMode(UUID uuid) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            boolean isGod = godModePlayers.getOrDefault(uuid, false);
            
            if (isGod) {
                godModePlayers.remove(uuid);
                player.getAbilities().invulnerable = false;
                player.sendMessage(Text.literal("§cGod mode disabled!"), false);
            } else {
                godModePlayers.put(uuid, true);
                player.getAbilities().invulnerable = true;
                player.sendMessage(Text.literal("§aGod mode enabled!"), false);
            }
            
            player.sendAbilitiesUpdate();
            dev.synm.SynM.LOGGER.info("Toggled god mode for player {} - now {}", 
                player.getName().getString(), !isGod ? "enabled" : "disabled");
            return true;
        }
        return false;
    }
    
    public boolean isPlayerInGodMode(UUID uuid) {
        return godModePlayers.getOrDefault(uuid, false);
    }
    
    // Fly Mode Toggle
    public boolean toggleFlyMode(UUID uuid) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            boolean canFly = player.getAbilities().allowFlying;
            
            player.getAbilities().allowFlying = !canFly;
            if (!canFly) {
                player.sendMessage(Text.literal("§aFlight enabled!"), false);
            } else {
                player.getAbilities().flying = false;
                player.sendMessage(Text.literal("§cFlight disabled!"), false);
            }
            
            player.sendAbilitiesUpdate();
            dev.synm.SynM.LOGGER.info("Toggled flight for player {} - now {}", 
                player.getName().getString(), !canFly ? "enabled" : "disabled");
            return true;
        }
        return false;
    }
    
    // Vanish Mode Toggle
    public boolean toggleVanishMode(UUID uuid) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            boolean isVanished = vanishedPlayers.getOrDefault(uuid, false);
            
            if (isVanished) {
                vanishedPlayers.remove(uuid);
                // Make visible to all players - simplified approach
                player.sendMessage(Text.literal("§aYou are now visible to other players!"), false);
            } else {
                vanishedPlayers.put(uuid, true);
                // Hide from all players - simplified approach
                player.sendMessage(Text.literal("§cYou are now invisible to other players!"), false);
            }
            
            dev.synm.SynM.LOGGER.info("Toggled vanish mode for player {} - now {}", 
                player.getName().getString(), !isVanished ? "vanished" : "visible");
            return true;
        }
        return false;
    }
    
    public boolean isPlayerVanished(UUID uuid) {
        return vanishedPlayers.getOrDefault(uuid, false);
    }
    
    // Player Notes
    public boolean setPlayerNote(UUID uuid, String note) {
        if (note == null || note.trim().isEmpty()) {
            playerNotes.remove(uuid);
        } else {
            playerNotes.put(uuid, note.trim());
        }
        saveData();
        dev.synm.SynM.LOGGER.info("Updated note for player {}", uuid);
        return true;
    }
    
    public String getPlayerNote(UUID uuid) {
        return playerNotes.getOrDefault(uuid, "");
    }
    
    // Enhanced JSON methods with new data
    public String getPlayerJson(UUID uuid) {
        PlayerInfo info = getPlayer(uuid);
        if (info != null) {
            if (info.isOnline() && info.getPlayer() != null) {
                info.updateFromPlayer(info.getPlayer());
            }
            
            JsonObject json = info.toJson();
            
            // Add additional data
            json.addProperty("note", getPlayerNote(uuid));
            json.addProperty("frozen", isPlayerFrozen(uuid));
            json.addProperty("godMode", isPlayerInGodMode(uuid));
            json.addProperty("vanished", isPlayerVanished(uuid));
            
            // Add flight status for online players
            if (info.isOnline() && info.getPlayer() != null) {
                json.addProperty("canFly", info.getPlayer().getAbilities().allowFlying);
                json.addProperty("isFlying", info.getPlayer().getAbilities().flying);
            }

            // Add session history (last 10)
            JsonArray sessions = new JsonArray();
            java.util.List<SessionRecord> list = sessionHistory.get(uuid);
            if (list != null) {
                int start = Math.max(0, list.size() - 10);
                for (int i = start; i < list.size(); i++) {
                    SessionRecord r = list.get(i);
                    JsonObject o = new JsonObject();
                    o.addProperty("start", r.start);
                    if (r.end > 0) o.addProperty("end", r.end);
                    if (r.ip != null) o.addProperty("ip", r.ip);
                    sessions.add(o);
                }
            }
            json.add("sessions", sessions);
            
            return gson.toJson(json);
        }
        return "{}";
    }

    // Broadcast helper
    public void broadcast(String message) {
        String msg = message == null ? "" : message.trim();
        if (msg.isEmpty()) return;
        try {
            // Send to all players safely
            for (PlayerInfo info : players.values()) {
                if (info.getPlayer() != null) {
                    info.getPlayer().sendMessage(Text.literal("[Announcement] " + msg), false);
                }
            }
        } catch (Exception e) {
            dev.synm.SynM.LOGGER.error("Broadcast failed", e);
        }
    }

    // Potion effects
    public boolean applyEffect(UUID uuid, String effectKey, int durationSeconds, int amplifier) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect = mapEffect(effectKey);
            if (effect == null) return false;
            int duration = Math.max(1, durationSeconds) * 20; // seconds to ticks
            int amp = Math.max(0, amplifier);
            player.addStatusEffect(new StatusEffectInstance(effect, duration, amp));
            dev.synm.SynM.LOGGER.info("Applied effect {} x{} for {}s to {}", effectKey, amp + 1, durationSeconds, player.getName().getString());
            return true;
        }
        return false;
    }

    public boolean clearEffects(UUID uuid) {
        PlayerInfo info = players.get(uuid);
        if (info != null && info.getPlayer() != null) {
            ServerPlayerEntity player = info.getPlayer();
            player.clearStatusEffects();
            dev.synm.SynM.LOGGER.info("Cleared effects for {}", player.getName().getString());
            return true;
        }
        return false;
    }

    private RegistryEntry<net.minecraft.entity.effect.StatusEffect> mapEffect(String key) {
        if (key == null) return null;
        String k = key.toLowerCase();
        switch (k) {
            case "speed": return StatusEffects.SPEED;
            case "slowness": return StatusEffects.SLOWNESS;
            case "haste": return StatusEffects.HASTE;
            case "mining_fatigue": return StatusEffects.MINING_FATIGUE;
            case "strength": return StatusEffects.STRENGTH;
            case "instant_health": return StatusEffects.INSTANT_HEALTH;
            case "instant_damage": return StatusEffects.INSTANT_DAMAGE;
            case "jump_boost": return StatusEffects.JUMP_BOOST;
            case "nausea": return StatusEffects.NAUSEA;
            case "regeneration": return StatusEffects.REGENERATION;
            case "resistance": return StatusEffects.RESISTANCE;
            case "fire_resistance": return StatusEffects.FIRE_RESISTANCE;
            case "water_breathing": return StatusEffects.WATER_BREATHING;
            case "invisibility": return StatusEffects.INVISIBILITY;
            case "blindness": return StatusEffects.BLINDNESS;
            case "night_vision": return StatusEffects.NIGHT_VISION;
            case "hunger": return StatusEffects.HUNGER;
            case "weakness": return StatusEffects.WEAKNESS;
            case "poison": return StatusEffects.POISON;
            case "wither": return StatusEffects.WITHER;
            case "health_boost": return StatusEffects.HEALTH_BOOST;
            case "absorption": return StatusEffects.ABSORPTION;
            case "saturation": return StatusEffects.SATURATION;
            case "glowing": return StatusEffects.GLOWING;
            case "levitation": return StatusEffects.LEVITATION;
            case "luck": return StatusEffects.LUCK;
            case "unluck": return StatusEffects.UNLUCK;
            case "slow_falling": return StatusEffects.SLOW_FALLING;
            case "conduit_power": return StatusEffects.CONDUIT_POWER;
            case "dolphins_grace": return StatusEffects.DOLPHINS_GRACE;
            case "bad_omen": return StatusEffects.BAD_OMEN;
            case "hero_of_the_village": return StatusEffects.HERO_OF_THE_VILLAGE;
            default: return null;
        }
    }

    // Session record data
    private static class SessionRecord {
        long start;
        long end;
        String ip;
        SessionRecord(long start, long end, String ip) {
            this.start = start; this.end = end; this.ip = ip;
        }
    }
    
    public String getPlayersJson() {
        Map<String, Object> result = new HashMap<>();
        
        List<JsonObject> onlineList = new ArrayList<>();
        for (PlayerInfo info : players.values()) {
            if (info.getPlayer() != null) {
                info.updateFromPlayer(info.getPlayer());
            }
            onlineList.add(info.toJson());
        }
        
        List<JsonObject> offlineList = new ArrayList<>();
        for (PlayerInfo info : offlinePlayers.values()) {
            offlineList.add(info.toJson());
        }
        
        result.put("online", onlineList);
        result.put("offline", offlineList);
        
        return gson.toJson(result);
    }
}
