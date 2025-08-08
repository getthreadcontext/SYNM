package dev.synm;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.synm.web.WebServer;
import dev.synm.player.PlayerManager;
import dev.synm.database.PlayerLogger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
// Database imports temporarily disabled
// import dev.synm.database.DatabaseManager;
// import dev.synm.database.PlayerLogger;

public class SynM implements ModInitializer {
	public static final String MOD_ID = "synm";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	private static WebServer webServer;
	private static PlayerManager playerManager;
	// Database components temporarily disabled
	// private static DatabaseManager databaseManager;
	// private static PlayerLogger playerLogger;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing SynM mod...");
		
		// Initialize player manager first
		playerManager = new PlayerManager();
		
		// Database system temporarily disabled to fix crashes
		// TODO: Re-enable database logging later
		/*
		// Initialize database system with error handling
		try {
			databaseManager = new DatabaseManager();
			databaseManager.initialize();
			
			// Initialize player logger
			playerLogger = new PlayerLogger(databaseManager);
			LOGGER.info("Database system initialized successfully");
		} catch (Exception e) {
			LOGGER.error("Failed to initialize database system, continuing without logging", e);
			// Continue without database logging if it fails
		}
		*/
		
		// Register server lifecycle events
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			// Check if this is a valid server environment for the web portal
			if (shouldStartWebServer(server)) {
				LOGGER.info("Server started, launching web portal on port 4444...");
				webServer = new WebServer(4444, playerManager, server);
				webServer.start();
			} else {
				LOGGER.info("SynM detected client-only environment, web portal disabled");
			}
		});
		
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("Server stopping, shutting down web portal...");
			if (webServer != null) {
				webServer.stop();
			}
			// Database system disabled
		});
		
		// Register player connection events
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			try {
				if (playerManager != null) {
					playerManager.onPlayerJoin(handler.getPlayer());
				}
				// Database logging temporarily disabled
				// if (playerLogger != null) {
				//     playerLogger.logPlayerJoin(handler.getPlayer());
				// }
			} catch (Exception e) {
				LOGGER.error("Error handling player join", e);
			}
		});

		// Server tick: enforce freeze and periodic states
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (playerManager != null) {
				try { playerManager.onServerTick(server); } catch (Exception e) { LOGGER.error("Tick handler error", e); }
			}
		});
		
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			try {
				if (playerManager != null) {
					playerManager.onPlayerLeave(handler.getPlayer());
				}
				// Database logging temporarily disabled
				// if (playerLogger != null) {
				//     playerLogger.logPlayerLeave(handler.getPlayer(), "Disconnected");
				// }
			} catch (Exception e) {
				LOGGER.error("Error handling player disconnect", e);
			}
		});
		
		LOGGER.info("SynM mod initialized successfully!");
	}
	
	private boolean shouldStartWebServer(MinecraftServer server) {
		// Check if this is a dedicated server by checking if it's running in single player mode
		boolean isDedicatedServer = server.isDedicated();
		
		LOGGER.info("Server type detected - Dedicated: {}", isDedicatedServer);
		
		if (isDedicatedServer) {
			LOGGER.info("Running on dedicated server, starting web portal");
			return true;
		} else {				
			LOGGER.info("Running on integrated server (local/LAN world), starting web portal for local management");
			// Always start for integrated servers too, so users can manage their local worlds
				return true;
			}
	}
	
	public static PlayerManager getPlayerManager() {
		return playerManager;
				}
				
				public static WebServer getWebServer() {
					return webServer;
				}
	
	// Database getters temporarily disabled
	/*
	public static DatabaseManager getDatabaseManager() {
		return databaseManager;
	}
	
	*/

	// Temporary stub to satisfy callers while database is disabled
	public static PlayerLogger getPlayerLogger() {
		return null; // no-op logger for now
	}
}
 