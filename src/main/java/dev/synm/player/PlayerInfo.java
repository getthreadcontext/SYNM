package dev.synm.player;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.Inventory;

import java.util.UUID;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

public class PlayerInfo {
    private final UUID uuid;
    private final String username;
    private boolean online;
    private long lastSeen;
    private long firstJoined;
    private long totalPlayTime; // in milliseconds
    
    // Player stats
    private double health;
    private double maxHealth;
    private int foodLevel;
    private float saturationLevel;
    private GameMode gameMode;
    private double x, y, z;
    private String dimensionName;
    private int experienceLevel;
    private float experienceProgress;
    
    // Inventory data
    private List<InventoryItem> inventory;
    private List<InventoryItem> hotbar;
    private List<InventoryItem> armor;
    private InventoryItem offhand;
    
    // Keep reference to online player
    private ServerPlayerEntity player;
    
    public PlayerInfo(ServerPlayerEntity player) {
        this.uuid = player.getUuid();
        this.username = player.getName().getString();
        this.online = true;
        this.firstJoined = Instant.now().toEpochMilli();
        this.lastSeen = this.firstJoined;
        this.totalPlayTime = 0;
        this.player = player;
        
        updateFromPlayer(player);
    }
    
    public void updateFromPlayer(ServerPlayerEntity player) {
        this.health = player.getHealth();
        this.maxHealth = player.getMaxHealth();
        this.foodLevel = player.getHungerManager().getFoodLevel();
        this.saturationLevel = player.getHungerManager().getSaturationLevel();
        this.gameMode = player.interactionManager.getGameMode();
        
        Vec3d pos = player.getPos();
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        
        this.dimensionName = player.getServerWorld().getRegistryKey().getValue().toString();
        this.experienceLevel = player.experienceLevel;
        this.experienceProgress = player.experienceProgress;
        this.lastSeen = Instant.now().toEpochMilli();
        
        // Update inventory
        updateInventory(player);
    }
    
    private void updateInventory(ServerPlayerEntity player) {
        // Main inventory (slots 9-35)
        this.inventory = new ArrayList<>();
        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            this.inventory.add(new InventoryItem(stack, i));
        }
        
        // Hotbar (slots 0-8)
        this.hotbar = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            this.hotbar.add(new InventoryItem(stack, i));
        }
        
        // Armor (slots 36-39)
        this.armor = new ArrayList<>();
        for (int i = 36; i < 40; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            this.armor.add(new InventoryItem(stack, i));
        }
        
        // Offhand (slot 40)
        ItemStack offhandStack = player.getInventory().getStack(40);
        this.offhand = new InventoryItem(offhandStack, 40);
    }
    
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", uuid.toString());
        json.addProperty("username", username);
        json.addProperty("online", online);
        json.addProperty("lastSeen", lastSeen);
        json.addProperty("firstJoined", firstJoined);
        json.addProperty("health", health);
        json.addProperty("maxHealth", maxHealth);
        json.addProperty("foodLevel", foodLevel);
        json.addProperty("saturationLevel", saturationLevel);
        json.addProperty("gameMode", gameMode != null ? gameMode.getName() : "unknown");
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        json.addProperty("dimension", dimensionName);
        json.addProperty("experienceLevel", experienceLevel);
        json.addProperty("experienceProgress", experienceProgress);
        
        // Add playtime data
        json.addProperty("totalPlayTime", totalPlayTime);
        json.addProperty("totalPlayTimeFormatted", formatPlayTime(totalPlayTime));
        
        // Calculate health percentage
        double healthPercentage = maxHealth > 0 ? (health / maxHealth) * 100 : 0;
        json.addProperty("healthPercentage", healthPercentage);
        
        // Add inventory data
        if (inventory != null) {
            JsonArray inventoryArray = new JsonArray();
            for (InventoryItem item : inventory) {
                inventoryArray.add(item.toJson());
            }
            json.add("inventory", inventoryArray);
        }
        
        if (hotbar != null) {
            JsonArray hotbarArray = new JsonArray();
            for (InventoryItem item : hotbar) {
                hotbarArray.add(item.toJson());
            }
            json.add("hotbar", hotbarArray);
        }
        
        if (armor != null) {
            JsonArray armorArray = new JsonArray();
            for (InventoryItem item : armor) {
                armorArray.add(item.toJson());
            }
            json.add("armor", armorArray);
        }
        
        if (offhand != null) {
            json.add("offhand", offhand.toJson());
        }
        
        return json;
    }
    
    // Inner class for inventory items
    private static class InventoryItem {
        private final String itemId;
        private final String displayName;
        private final int count;
        private final int slot;
        private final boolean isEmpty;
        
        public InventoryItem(ItemStack stack, int slot) {
            this.slot = slot;
            if (stack.isEmpty()) {
                this.isEmpty = true;
                this.itemId = "air";
                this.displayName = "Empty";
                this.count = 0;
            } else {
                this.isEmpty = false;
                this.itemId = stack.getItem().toString();
                this.displayName = stack.getName().getString();
                this.count = stack.getCount();
            }
        }
        
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("slot", slot);
            json.addProperty("itemId", itemId);
            json.addProperty("displayName", displayName);
            json.addProperty("count", count);
            json.addProperty("isEmpty", isEmpty);
            return json;
        }
    }
    
    // Getters
    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public boolean isOnline() { return online; }
    public long getLastSeen() { return lastSeen; }
    public long getFirstJoined() { return firstJoined; }
    public double getHealth() { return health; }
    public double getMaxHealth() { return maxHealth; }
    public int getFoodLevel() { return foodLevel; }
    public float getSaturationLevel() { return saturationLevel; }
    public GameMode getGameMode() { return gameMode; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public String getDimensionName() { return dimensionName; }
    public int getExperienceLevel() { return experienceLevel; }
    public float getExperienceProgress() { return experienceProgress; }
    public ServerPlayerEntity getPlayer() { return player; }
    public long getTotalPlayTime() { return totalPlayTime; }
    
    // Playtime methods
    public void addPlayTime(long milliseconds) {
        this.totalPlayTime += milliseconds;
    }
    
    private String formatPlayTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    // Setters
    public void setOnline(boolean online) { 
        this.online = online; 
        if (!online) {
            this.player = null;
        }
    }
}
