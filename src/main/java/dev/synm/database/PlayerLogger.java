package dev.synm.database;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Minimal stub interface used by mixins and PlayerManager for logging.
 * Implementations can be added later; returning null from SynM.getPlayerLogger()
 * means logging is disabled without breaking compilation.
 */
public interface PlayerLogger {
    void logPlayerDeath(ServerPlayerEntity player, String deathMessage);
    void logGameModeChange(ServerPlayerEntity player, String from, String to, String reason);
}
