# SynM - Server Management Mod

SynM is a Minecraft Fabric mod that provides a web-based player management portal. It works with both dedicated servers and local/LAN worlds, allowing server administrators to monitor and manage players through a convenient web interface accessible at port 4444.

## Features

### Web Portal (Port 4444)
- **Player List**: View all online and offline players who have joined the server
- **Player Details**: Click on any player to view detailed information including:
  - Health status and percentage
  - Food level and saturation
  - Game mode
  - Experience level
  - Current position and dimension
  - **Complete inventory view** (hotbar, main inventory, armor, offhand)
  - First joined and last seen timestamps

### Player Management Actions (Online Players Only)
- **Heal**: Restore player to full health with regeneration effect
- **Feed**: Fill player's hunger and saturation to maximum
- **Game Mode Changes**: Switch between Creative and Survival modes
- **Teleportation**: Teleport players to specific coordinates
- **Kick**: Remove players from the server with custom reason

### Real-time Updates
- Auto-refresh player data every 5 seconds
- Health bars with visual indicators
- Online/offline status indicators
- Responsive design for desktop and mobile

## Installation

### For Local/LAN Worlds (Single Player)
1. Make sure you have Minecraft 1.21.4 with Fabric Loader installed
2. Build the mod using `./gradlew build`
3. Copy the generated JAR file from `build/libs/` to your `.minecraft/mods` folder
4. Start Minecraft and create or load a world
5. The web portal will automatically start on port 4444
6. Access it at `http://localhost:4444`

### For Dedicated Servers
1. Make sure you have Minecraft 1.21.4 with Fabric Loader installed on your server
2. Build the mod using `./gradlew build`
3. Copy the generated JAR file from `build/libs/` to your server's `mods` folder
4. Start your Minecraft server
5. The web portal will automatically start on port 4444
6. Access it at `http://your-server-ip:4444`

## Usage

### Local/LAN Worlds
1. Install the mod in your client `.minecraft/mods` folder
2. Start Minecraft and load any world (single player or LAN)
3. Open a web browser and go to `http://localhost:4444`
4. You'll see the SynM Server Management Portal
5. If you have LAN players, they'll appear in the player list

### Dedicated Servers
1. Install the mod on your server
2. Start your server
3. Players and administrators can access the web portal at `http://server-ip:4444`
4. Manage all connected players through the web interface

## Web Interface Features

1. **Player Overview**: See all players who have joined, both online and offline
2. **Detailed Player Info**: Click any player to see:
   - Current health and hunger levels
   - Position and dimension
   - Game mode and experience
   - **Complete inventory view** (hotbar, main inventory, armor, offhand)
   - Join/leave timestamps
3. **Management Actions** (for online players):
   - Heal and feed players instantly
   - Change game modes
   - Teleport to specific coordinates
   - Kick players with custom messages

## API Endpoints

The mod provides REST API endpoints for integration:

- `GET /api/players` - Get all player data (online and offline)
- `GET /api/player/{uuid}` - Get specific player data
- `POST /api/action/{action}` - Perform actions on players

## Requirements

- Minecraft 1.21.4
- Fabric Loader 0.16.14+
- Fabric API
- Java 21+

## Technical Details

- **Works everywhere**: Compatible with single player, LAN, and dedicated servers
- **Port 4444**: The web interface runs on port 4444 by default (falls back to 4445 if busy)
- **Local access**: For single player/LAN worlds, access via `http://localhost:4444`
- **Data Storage**: Player information is stored in memory during world/server runtime
- **Auto-detection**: Automatically detects server type and adjusts accordingly

## Troubleshooting

### "Web server failed to start"
- Port 4444 might be in use by another application
- The mod will automatically try port 4445 as a fallback
- Check the game logs for the actual port being used

### "Can't access the web portal"
- For local worlds: Use `http://localhost:4444`
- For LAN worlds: Use `http://localhost:4444` on the host computer
- For dedicated servers: Use `http://server-ip:4444`
- Make sure the port isn't blocked by firewall

### "No players showing up"
- Players must join the world/server at least once to appear
- The mod tracks players from the moment it's installed
- Restart the world/server if you just installed the mod

## Building from Source

```bash
git clone <repository-url>
cd synm
./gradlew build
```

The built JAR file will be located in `build/libs/`.

## License

This project is licensed under CC0-1.0.

## Support

If you encounter any issues:
1. Check the Minecraft game logs for error messages
2. Try accessing the web portal on the fallback port 4445
3. Make sure you're using the correct URL for your setup type
